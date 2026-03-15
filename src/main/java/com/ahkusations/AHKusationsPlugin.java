package com.ahkusations;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.kit.KitType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@PluginDescriptor(
	name = "AHKusations",
	description = "Analyzes PvP combat to estimate likelihood of AHK/cheating",
	tags = {"pvp", "ahk", "cheat", "detection", "combat"}
)
public class AHKusationsPlugin extends Plugin
{
	private static final int DEFAULT_MAX_HP = 99;

	@Inject
	private Client client;

	@Inject
	private AHKusationsConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ClientThread clientThread;

	@Inject
	@Getter
	private ItemManager itemManager;

	@Inject
	private EventBus eventBus;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private AHKusationsOverlay overlay;

	private NavigationButton navButton;
	private AHKusationsPanel panel;
	private FightDataStore dataStore;

	private FightSession currentFight;
	private Player currentOpponent;
	private int lastInteractionTick;
	private boolean fightConfirmed; // true once an attack animation is seen from either player

	// Per-tick event buffers
	private final List<Integer> pendingHitsOnLocal = new ArrayList<>();
	private final List<Integer> pendingHitsOnOpponent = new ArrayList<>();
	private boolean localDeathThisTick;
	private boolean opponentDeathThisTick;
	private int opponentSpotAnimThisTick = -1;

	// Click tracking buffer (sub-tick precision for self-analysis)
	private final List<ClickEvent> pendingClicks = new ArrayList<>();

	// Ghost barrage detection: track magic XP changes during fight
	private int lastMagicXp = -1;
	private boolean magicAnimSeenThisTick;
	@Getter
	private int ghostBarrageCount;

	@Getter
	private final List<FightHistoryEntry> fightHistory = new CopyOnWriteArrayList<>();

	public void setFightLabel(FightHistoryEntry entry, String label)
	{
		try
		{
			entry.setUserLabel(label);
			if (dataStore != null)
			{
				dataStore.setUserLabel(entry.getTimestampMs(), entry.getOpponentName(), label);
			}
		}
		catch (Exception e)
		{
			log.warn("Error setting fight label: {}", e.getMessage());
		}
	}

	// Manual event subscribers (avoids LambdaMetafactory classloader issues with sideloaded plugins)
	private EventBus.Subscriber interactingSub;
	private EventBus.Subscriber animationSub;
	private EventBus.Subscriber hitsplatSub;
	private EventBus.Subscriber statSub;
	private EventBus.Subscriber menuClickSub;
	private EventBus.Subscriber gameTickSub;

	@Provides
	AHKusationsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(AHKusationsConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		dataStore = new FightDataStore();

		// Load raw fight data and re-analyze with current algorithm
		try
		{
			List<FightHistoryEntry> saved = dataStore.loadAndReanalyze(true, config.maxFightHistory());
			fightHistory.clear();
			fightHistory.addAll(saved);
		}
		catch (Exception e)
		{
			log.warn("Failed to load fight history: {}", e.getMessage());
			fightHistory.clear();
		}

		panel = new AHKusationsPanel(this);

		BufferedImage icon;
		try
		{
			icon = ImageUtil.loadImageResource(getClass(), "/ahkusations.png");
		}
		catch (Exception e)
		{
			icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		}

		navButton = NavigationButton.builder()
			.tooltip("AHKusations")
			.icon(icon)
			.priority(10)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		overlayManager.add(overlay);

		// Register events manually to avoid classloader lambda issues
		interactingSub = eventBus.register(InteractingChanged.class, this::onInteractingChanged, 0);
		animationSub = eventBus.register(AnimationChanged.class, this::onAnimationChanged, 0);
		hitsplatSub = eventBus.register(HitsplatApplied.class, this::onHitsplatApplied, 0);
		statSub = eventBus.register(StatChanged.class, this::onStatChanged, 0);
		menuClickSub = eventBus.register(MenuOptionClicked.class, this::onMenuOptionClicked, 0);
		gameTickSub = eventBus.register(GameTick.class, this::onGameTick, 0);

		log.info("AHKusations started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		// Discard active fight on shutdown rather than trying to analyze
		// (client state may be invalid during plugin shutdown)
		currentFight = null;
		currentOpponent = null;
		fightConfirmed = false;
		lastMagicXp = -1;
		clearTickBuffers();

		if (interactingSub != null) eventBus.unregister(interactingSub);
		if (animationSub != null) eventBus.unregister(animationSub);
		if (hitsplatSub != null) eventBus.unregister(hitsplatSub);
		if (statSub != null) eventBus.unregister(statSub);
		if (menuClickSub != null) eventBus.unregister(menuClickSub);
		if (gameTickSub != null) eventBus.unregister(gameTickSub);

		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		log.info("AHKusations stopped");
	}

	private void onInteractingChanged(InteractingChanged event)
	{
		try
		{
			Actor source = event.getSource();
			Actor target = event.getTarget();

			// Only care about player-to-player interactions involving the local player
			if (!(source instanceof Player) || !(target instanceof Player))
			{
				return;
			}

			Player local = client.getLocalPlayer();
			if (local == null || (source != local && target != local))
			{
				return;
			}

			// Determine the opponent
			Player opponent = (Player) (source == local ? target : source);
			if (opponent.getName() == null)
			{
				return;
			}

			// If we already have a confirmed fight with this opponent, just refresh the tick
			if (currentFight != null && currentFight.isActive()
				&& opponent.getName().equals(currentFight.getOpponentName()))
			{
				lastInteractionTick = client.getTickCount();
				return;
			}

			// New potential opponent — end any existing fight first
			if (currentFight != null && currentFight.isActive())
			{
				endFight();
			}

			// Create a tentative fight — will only be confirmed once an attack animation is seen
			startFight(opponent);
		}
		catch (Exception e)
		{
			log.debug("Error in onInteractingChanged: {}", e.getMessage());
		}
	}

	private void onAnimationChanged(AnimationChanged event)
	{
		try
		{
			Actor actor = event.getActor();
			if (!(actor instanceof Player))
			{
				return;
			}

			int anim = actor.getAnimation();

			// Death detection
			if (CombatUtils.isDeathAnimation(anim))
			{
				if (actor == client.getLocalPlayer())
				{
					localDeathThisTick = true;
				}
				else if (actor == currentOpponent)
				{
					opponentDeathThisTick = true;
				}
			}

			// Confirm fight once either player performs a known attack animation
			if (currentFight != null && currentFight.isActive() && !fightConfirmed)
			{
				if (actor == client.getLocalPlayer() || actor == currentOpponent)
				{
					AttackStyle style = CombatUtils.getStyleFromAnimation(anim);
					if (style != AttackStyle.UNKNOWN)
					{
						fightConfirmed = true;
						log.info("Fight confirmed vs {} (attack animation {})", currentFight.getOpponentName(), anim);
						if (panel != null)
						{
							panel.onFightStarted(currentFight.getOpponentName());
						}
						String localName = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "You";
						overlay.onFightStarted(currentFight.getOpponentName(), localName);
					}
				}
			}

			// Track if local player played a magic animation (for ghost barrage detection)
			if (actor == client.getLocalPlayer())
			{
				AttackStyle style = CombatUtils.getStyleFromAnimation(anim);
				if (style == AttackStyle.MAGIC)
				{
					magicAnimSeenThisTick = true;
				}
			}
		}
		catch (Exception e)
		{
			log.debug("Error in onAnimationChanged: {}", e.getMessage());
		}
	}

	private void onHitsplatApplied(HitsplatApplied event)
	{
		try
		{
			if (currentFight == null || !currentFight.isActive())
			{
				return;
			}

			Actor actor = event.getActor();
			Hitsplat hitsplat = event.getHitsplat();
			if (hitsplat == null) return;
			int damage = hitsplat.getAmount();

			if (actor == client.getLocalPlayer())
			{
				pendingHitsOnLocal.add(damage);
			}
			else if (actor == currentOpponent)
			{
				pendingHitsOnOpponent.add(damage);
			}
		}
		catch (Exception e)
		{
			log.debug("Error in onHitsplatApplied: {}", e.getMessage());
		}
	}

	private void onMenuOptionClicked(MenuOptionClicked event)
	{
		try
		{
			if (currentFight == null || !currentFight.isActive())
			{
				return;
			}

			String option = event.getMenuOption();
			String target = event.getMenuTarget();
			int widgetGroupId = -1;

			try
			{
				int widgetId = event.getWidgetId();
				if (widgetId > 0)
				{
					widgetGroupId = widgetId >> 16;
				}
			}
			catch (Exception e)
			{
				// widget ID may not be available
			}

			// Clean HTML tags from target (RuneLite wraps in <col=...>)
			if (target != null)
			{
				target = target.replaceAll("<[^>]+>", "");
			}

			pendingClicks.add(ClickEvent.builder()
				.timestampMs(System.currentTimeMillis())
				.widgetGroupId(widgetGroupId)
				.menuOption(option != null ? option : "")
				.menuTarget(target != null ? target : "")
				.actionParam(event.getParam0())
				.build());
		}
		catch (Exception e)
		{
			log.debug("Error in onMenuOptionClicked: {}", e.getMessage());
		}
	}

	private void onStatChanged(StatChanged event)
	{
		try
		{
			// Ghost barrage detection: magic XP gain without magic animation
			if (currentFight == null || !currentFight.isActive())
			{
				return;
			}

			if (event.getSkill() == Skill.MAGIC)
			{
				int currentXp = event.getXp();
				if (lastMagicXp >= 0 && currentXp > lastMagicXp && !magicAnimSeenThisTick)
				{
					ghostBarrageCount++;
					log.info("Ghost barrage detected! (magic XP gained without visible animation)");
				}
				lastMagicXp = currentXp;
			}
		}
		catch (Exception e)
		{
			log.debug("Error in onStatChanged: {}", e.getMessage());
		}
	}

	private void onGameTick(GameTick event)
	{
		if (currentFight == null || !currentFight.isActive())
		{
			magicAnimSeenThisTick = false;
			pendingClicks.clear();
			return;
		}

		try
		{
			int currentTick = client.getTickCount();

			// Check for fight timeout (applies to both confirmed and unconfirmed)
			if (currentTick - lastInteractionTick > config.fightEndTimeout())
			{
				if (fightConfirmed)
				{
					endFight();
				}
				else
				{
					// Tentative fight timed out without any attacks — discard silently (was a trade/follow/etc.)
					log.debug("Tentative fight with {} discarded (no attacks detected)", currentFight.getOpponentName());
					currentFight = null;
					currentOpponent = null;
					lastMagicXp = -1;
					fightConfirmed = false;
				}
				clearTickBuffers();
				return;
			}

			// Opponent gone
			if (currentOpponent == null || currentOpponent.getName() == null)
			{
				if (fightConfirmed)
				{
					endFight();
				}
				else
				{
					currentFight = null;
					currentOpponent = null;
					lastMagicXp = -1;
					fightConfirmed = false;
				}
				clearTickBuffers();
				return;
			}

			// Don't record snapshots until the fight is confirmed by an attack animation
			if (!fightConfirmed)
			{
				// Still refresh interaction tick if opponent targets us
				Actor opponentTarget = currentOpponent.getInteracting();
				if (opponentTarget == client.getLocalPlayer())
				{
					lastInteractionTick = currentTick;
				}
				clearTickBuffers();
				return;
			}

			// --- Fight is confirmed from here ---

			// Check for death - end fight
			if (localDeathThisTick || opponentDeathThisTick)
			{
				TickSnapshot snapshot = captureSnapshot(currentTick);
				if (snapshot != null)
				{
					currentFight.addSnapshot(snapshot);
				}
				endFight();
				clearTickBuffers();
				return;
			}

			// Refresh interaction tick if opponent is still targeting us
			Actor opponentTarget = currentOpponent.getInteracting();
			if (opponentTarget == client.getLocalPlayer())
			{
				lastInteractionTick = currentTick;
			}

			// Poll opponent graphic (avoids GraphicChanged event classloader issues)
			try
			{
				int gfx = currentOpponent.getGraphic();
				if (gfx > 0)
				{
					opponentSpotAnimThisTick = gfx;
				}
			}
			catch (Exception e)
			{
				// getGraphic() is deprecated and may not be available
			}

			// Capture tick snapshot
			TickSnapshot snapshot = captureSnapshot(currentTick);
			if (snapshot != null)
			{
				currentFight.addSnapshot(snapshot);
			}
			// Update live overlay analysis
			overlay.updateLiveAnalysis(currentFight, currentTick);

			clearTickBuffers();
		}
		catch (Exception e)
		{
			log.warn("Error in onGameTick: {}", e.getMessage(), e);
			clearTickBuffers();
		}
	}

	private void clearTickBuffers()
	{
		pendingHitsOnLocal.clear();
		pendingHitsOnOpponent.clear();
		pendingClicks.clear();
		localDeathThisTick = false;
		opponentDeathThisTick = false;
		opponentSpotAnimThisTick = -1;
		magicAnimSeenThisTick = false;
	}

	private void startFight(Player opponent)
	{
		String name = opponent.getName();
		if (name == null)
		{
			return;
		}

		int tick = client.getTickCount();
		currentOpponent = opponent;
		currentFight = new FightSession(name, opponent.getCombatLevel(), tick);
		lastInteractionTick = tick;
		fightConfirmed = false;
		ghostBarrageCount = 0;

		// Initialize magic XP tracking
		lastMagicXp = client.getSkillExperience(Skill.MAGIC);

		log.debug("Tentative fight with {} (level {}) — awaiting attack animation", name, opponent.getCombatLevel());
	}

	private void endFight()
	{
		if (currentFight == null)
		{
			return;
		}

		try
		{
			currentFight.setEndTick(client.getTickCount());
		}
		catch (Exception e)
		{
			log.debug("Error getting tick count for endFight: {}", e.getMessage());
		}
		currentFight.setActive(false);

		int duration = currentFight.getDurationTicks();
		String name = currentFight.getOpponentName();

		if (duration >= config.minFightTicks())
		{
			try
			{
				CheatAnalysis opponentAnalysis = CheatDetector.analyzeOpponent(currentFight);

				CheatAnalysis selfAnalysis = null;
				if (true)
				{
					selfAnalysis = CheatDetector.analyzeLocal(currentFight);
				}

				FightHistoryEntry entry = FightHistoryEntry.builder()
					.opponentName(name)
					.opponentCombatLevel(currentFight.getOpponentCombatLevel())
					.durationTicks(duration)
					.durationSeconds(currentFight.getDurationSeconds())
					.timestampMs(currentFight.getStartTimeMs())
					.totalTicks(currentFight.getSnapshots().size())
					.opponentAnalysis(opponentAnalysis)
					.selfAnalysis(selfAnalysis)
					.build();

				fightHistory.add(0, entry);

				// Trim history
				while (fightHistory.size() > config.maxFightHistory())
				{
					fightHistory.remove(fightHistory.size() - 1);
				}

				log.info("Fight ended with {} | Duration: {}s | Opponent score: {} ({})",
					name, String.format("%.1f", currentFight.getDurationSeconds()),
					String.format("%.1f", opponentAnalysis.getOverallScore()), opponentAnalysis.getVerdict());

				if (opponentAnalysis.getFlags() != null)
				{
					for (String flag : opponentAnalysis.getFlags())
					{
						log.info("  Flag: {}", flag);
					}
				}

				panel.onFightEnded(entry);
				String localName = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "You";
				overlay.onFightEnded(entry, localName);

				// Save raw tick data to disk (re-analyzed on next startup)
				dataStore.saveRawFight(currentFight);
			}
			catch (Exception e)
			{
				log.warn("Error analyzing fight with {}: {}", name, e.getMessage(), e);
			}
		}
		else
		{
			log.debug("Fight with {} too short to analyze ({} ticks)", name, duration);
		}

		currentFight = null;
		currentOpponent = null;
		fightConfirmed = false;
		lastMagicXp = -1;
	}

	private TickSnapshot captureSnapshot(int tick)
	{
		try
		{
			Player local = client.getLocalPlayer();
			if (local == null || currentOpponent == null)
			{
				return null;
			}

			int[] localEquip = getEquipmentIds(local);
			int[] opponentEquip = getEquipmentIds(currentOpponent);

			int localWeapon = getWeaponId(local);
			int opponentWeapon = getWeaponId(currentOpponent);

			int localHp = CombatUtils.estimateHp(
				local.getHealthRatio(), local.getHealthScale(), DEFAULT_MAX_HP);
			int opponentHp = CombatUtils.estimateHp(
				currentOpponent.getHealthRatio(), currentOpponent.getHealthScale(), DEFAULT_MAX_HP);

			// Active interface tab (varc 171): 0=Combat, 3=Inventory, 5=Prayer, 6=Magic
			int activeTab = -1;
			try
			{
				activeTab = client.getVarcIntValue(171);
			}
			catch (Exception e)
			{
				// Fallback if varc not available
			}

			// Safe overhead/animation reads — player may have despawned between check and here
			HeadIcon localOverhead = null;
			int localAnim = -1;
			HeadIcon oppOverhead = null;
			int oppAnim = -1;
			try { localOverhead = local.getOverheadIcon(); } catch (Exception ignored) {}
			try { localAnim = local.getAnimation(); } catch (Exception ignored) {}
			try { oppOverhead = currentOpponent.getOverheadIcon(); } catch (Exception ignored) {}
			try { oppAnim = currentOpponent.getAnimation(); } catch (Exception ignored) {}

			return TickSnapshot.builder()
				.tick(tick)
				.localEquipment(localEquip)
				.localOverhead(localOverhead)
				.localAnimation(localAnim)
				.localWeaponId(localWeapon)
				.localEstimatedHp(localHp)
				.opponentEquipment(opponentEquip)
				.opponentOverhead(oppOverhead)
				.opponentAnimation(oppAnim)
				.opponentWeaponId(opponentWeapon)
				.opponentEstimatedHp(opponentHp)
				.opponentSpotAnim(opponentSpotAnimThisTick)
				.hitsOnLocal(new ArrayList<>(pendingHitsOnLocal))
				.hitsOnOpponent(new ArrayList<>(pendingHitsOnOpponent))
				.localDied(localDeathThisTick)
				.opponentDied(opponentDeathThisTick)
				.localActiveTab(activeTab)
				.localClicks(new ArrayList<>(pendingClicks))
				.build();
		}
		catch (Exception e)
		{
			log.debug("Error capturing snapshot: {}", e.getMessage());
			return null;
		}
	}

	private int[] getEquipmentIds(Player player)
	{
		PlayerComposition composition = player.getPlayerComposition();
		if (composition == null)
		{
			return new int[0];
		}
		int[] ids = composition.getEquipmentIds();
		return ids != null ? Arrays.copyOf(ids, ids.length) : new int[0];
	}

	/**
	 * Get weapon item ID using the safe getEquipmentId(KitType) API
	 * which handles offset math internally.
	 */
	private int getWeaponId(Player player)
	{
		PlayerComposition composition = player.getPlayerComposition();
		if (composition == null)
		{
			return -1;
		}
		int id = composition.getEquipmentId(KitType.WEAPON);
		return id >= 0 ? id : -1;
	}

	/**
	 * Resolve an item ID to its display name.
	 */
	public String getItemName(int itemId)
	{
		if (itemId <= 0)
		{
			return "None";
		}
		try
		{
			ItemComposition def = client.getItemDefinition(itemId);
			if (def != null && def.getName() != null && !def.getName().equals("null"))
			{
				return def.getName();
			}
		}
		catch (Exception e)
		{
			log.debug("Failed to get item name for id {}: {}", itemId, e.getMessage());
		}
		return "Item #" + itemId;
	}
}
