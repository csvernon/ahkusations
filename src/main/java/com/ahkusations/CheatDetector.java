package com.ahkusations;

import net.runelite.api.HeadIcon;

import java.util.*;

public class CheatDetector
{
	public static CheatAnalysis analyzeOpponent(FightSession session)
	{
		return analyze(session, false);
	}

	public static CheatAnalysis analyzeLocal(FightSession session)
	{
		return analyze(session, true);
	}

	private static CheatAnalysis analyze(FightSession session, boolean analyzeLocal)
	{
		String playerName = analyzeLocal ? "You" : (session.getOpponentName() != null ? session.getOpponentName() : "Unknown");

		// Top-level safety net — analysis must never crash the client
		try
		{
			return analyzeInner(session, analyzeLocal, playerName);
		}
		catch (Exception e)
		{
			return CheatAnalysis.builder()
				.playerName(playerName)
				.overallScore(0)
				.flags(List.of("Analysis error: " + e.getMessage()))
				.build();
		}
	}

	private static CheatAnalysis analyzeInner(FightSession session, boolean analyzeLocal, String playerName)
	{
		List<TickSnapshot> snapshots = session.getSnapshots();
		List<String> flags = new ArrayList<>();

		if (snapshots.size() < 2)
		{
			return CheatAnalysis.builder()
				.playerName(playerName)
				.overallScore(0)
				.flags(List.of("Fight too short to analyze"))
				.build();
		}

		// ============================================================
		// 1. GEAR SWITCH ANALYSIS
		// ============================================================
		int maxSwitches = 0;
		int totalSwitches = 0;
		int switchTicks = 0;
		int bigSwitchCount = 0; // 4+ way switches
		List<Integer> switchCounts = new ArrayList<>();

		for (int i = 1; i < snapshots.size(); i++)
		{
			if (isDead(snapshots.get(i), analyzeLocal) || isDead(snapshots.get(i - 1), analyzeLocal))
			{
				continue;
			}

			int[] prev = getEquip(snapshots.get(i - 1), analyzeLocal);
			int[] curr = getEquip(snapshots.get(i), analyzeLocal);
			int switches = CombatUtils.countGearSwitches(prev, curr);
			if (switches > 0)
			{
				switchCounts.add(switches);
				totalSwitches += switches;
				switchTicks++;
				maxSwitches = Math.max(maxSwitches, switches);
				if (switches >= 4) bigSwitchCount++;
			}
		}

		double avgSwitches = switchTicks > 0 ? (double) totalSwitches / switchTicks : 0;

		double gearSwitchScore = 0;

		// Max switch size
		if (maxSwitches >= 8) { gearSwitchScore = 100; flags.add("Inhuman: " + maxSwitches + "-way switch"); }
		else if (maxSwitches >= 7) { gearSwitchScore = 80; flags.add("Very suspicious: " + maxSwitches + "-way switch"); }
		else if (maxSwitches >= 6) { gearSwitchScore = 60; flags.add("Suspicious: " + maxSwitches + "-way switch"); }
		else if (maxSwitches >= 5) { gearSwitchScore = 40; flags.add(maxSwitches + "-way switches detected"); }
		else if (maxSwitches >= 4) { gearSwitchScore = 20; }

		// Frequency of big switches — many 4+ way switches is a strong automation signal
		if (bigSwitchCount >= 15)
		{
			gearSwitchScore = Math.min(100, gearSwitchScore + 40);
			flags.add("Frequent " + bigSwitchCount + "x 4+ way switches");
		}
		else if (bigSwitchCount >= 8)
		{
			gearSwitchScore = Math.min(100, gearSwitchScore + 25);
			flags.add(bigSwitchCount + "x 4+ way switches");
		}
		else if (bigSwitchCount >= 4)
		{
			gearSwitchScore = Math.min(100, gearSwitchScore + 15);
		}

		// Switch count consistency (low variance = robotic)
		if (switchCounts.size() >= 5)
		{
			double variance = calcVariance(switchCounts);
			if (variance < 1.0 && avgSwitches >= 2.5)
			{
				gearSwitchScore = Math.min(100, gearSwitchScore + 20);
				flags.add("Consistent switch patterns (var=" + String.format("%.2f", variance) + ")");
			}
			else if (variance < 2.0 && avgSwitches >= 2.5)
			{
				gearSwitchScore = Math.min(100, gearSwitchScore + 10);
			}
		}

		gearSwitchScore = Math.min(100, gearSwitchScore);

		// ============================================================
		// 2. LOADOUT PERFECTION
		// ============================================================
		Map<Integer, Set<String>> loadoutsPerWeapon = new HashMap<>();
		Map<Integer, Integer> ticksPerWeapon = new HashMap<>();
		for (TickSnapshot snap : snapshots)
		{
			if (isDead(snap, analyzeLocal)) continue;
			int weapon = getWeapon(snap, analyzeLocal);
			int[] equip = getEquip(snap, analyzeLocal);
			if (weapon > 0 && equip.length > 0)
			{
				loadoutsPerWeapon.computeIfAbsent(weapon, k -> new HashSet<>()).add(Arrays.toString(equip));
				ticksPerWeapon.merge(weapon, 1, Integer::sum);
			}
		}

		int totalUniqueLoadouts = 0;
		int weaponsWithLowVariation = 0; // <=2 loadouts with 20+ ticks
		int weaponsWithPerfectLoadout = 0;
		int weaponsChecked = 0;
		for (Map.Entry<Integer, Set<String>> entry : loadoutsPerWeapon.entrySet())
		{
			int uniqueForWeapon = entry.getValue().size();
			totalUniqueLoadouts += uniqueForWeapon;
			int ticks = ticksPerWeapon.getOrDefault(entry.getKey(), 0);
			if (ticks >= 5)
			{
				weaponsChecked++;
				if (uniqueForWeapon == 1) weaponsWithPerfectLoadout++;
				if (uniqueForWeapon <= 2 && ticks >= 20) weaponsWithLowVariation++;
			}
		}

		double loadoutPerfection = weaponsChecked > 0
			? (double) weaponsWithPerfectLoadout / weaponsChecked * 100 : 0;

		double loadoutScore = 0;

		// Perfect loadouts (exactly 1 gear set per weapon)
		if (weaponsWithPerfectLoadout >= 2)
		{
			loadoutScore = 70;
			flags.add("Perfect loadouts: 0 variation across " + weaponsWithPerfectLoadout + " styles");
		}
		else if (weaponsWithPerfectLoadout >= 1 && switchTicks >= 5)
		{
			loadoutScore = 40;
		}

		// Near-perfect: <=2 loadouts over 20+ ticks per weapon (e.g., ZCB: 2 loadouts / 122 ticks)
		if (weaponsWithLowVariation >= 2)
		{
			loadoutScore = Math.max(loadoutScore, 60);
			if (loadoutScore < 70)
			{
				flags.add("Near-perfect loadouts: " + weaponsWithLowVariation + " weapons with <=2 gear sets over 20+ ticks");
			}
		}
		else if (weaponsWithLowVariation >= 1 && switchTicks >= 10)
		{
			loadoutScore = Math.max(loadoutScore, 40);
		}

		// Bonus for high switch count with perfect loadouts
		if ((weaponsWithPerfectLoadout >= 1 || weaponsWithLowVariation >= 2) && switchTicks >= 20)
		{
			loadoutScore = Math.min(100, loadoutScore + 20);
			flags.add("Consistent gear across " + switchTicks + " switch ticks");
		}

		// Loadout ratio: unique loadouts per tick — bots have very few loadouts relative to fight length
		int activeTicks = 0;
		for (TickSnapshot snap : snapshots)
		{
			if (!isDead(snap, analyzeLocal)) activeTicks++;
		}
		double loadoutRatio = activeTicks > 0 ? (double) totalUniqueLoadouts / activeTicks : 1.0;
		if (activeTicks >= 30)
		{
			if (loadoutRatio < 0.008)
			{
				loadoutScore = Math.min(100, loadoutScore + 30);
				flags.add("Extreme loadout ratio: " + String.format("%.4f", loadoutRatio)
					+ " (" + totalUniqueLoadouts + " loadouts / " + activeTicks + " ticks)");
			}
			else if (loadoutRatio < 0.015)
			{
				loadoutScore = Math.min(100, loadoutScore + 15);
				flags.add("Low loadout ratio: " + String.format("%.4f", loadoutRatio)
					+ " (" + totalUniqueLoadouts + " loadouts / " + activeTicks + " ticks)");
			}
		}

		loadoutScore = Math.min(100, loadoutScore);

		// ============================================================
		// 3. ATTACK INTERVAL REGULARITY
		// Only count intervals where the player performed an action that would
		// interrupt auto-attacking (gear switch, eating, moving). Standing still
		// auto-retaliating naturally produces perfect intervals — that's not suspicious.
		// ============================================================
		List<Integer> attackIntervals = new ArrayList<>();
		int lastAttackTick = -1;
		int lastAttackSnapIdx = -1;
		int totalAttackAnims = 0;
		for (int si = 0; si < snapshots.size(); si++)
		{
			TickSnapshot snap = snapshots.get(si);
			if (isDead(snap, analyzeLocal)) continue;
			int anim = getAnim(snap, analyzeLocal);
			int weapon = getWeapon(snap, analyzeLocal);
			AttackStyle style = CombatUtils.determineAttackStyle(anim, weapon);
			if (style != AttackStyle.UNKNOWN && anim > 0)
			{
				totalAttackAnims++;
				if (lastAttackTick >= 0 && lastAttackSnapIdx >= 0)
				{
					int interval = snap.getTick() - lastAttackTick;
					// Only count intervals >= 2 ticks (skip animation carryover)
					if (interval >= 2 && interval <= 20)
					{
						// Check if an interrupting action occurred between the two attacks
						if (hadInterruptingAction(snapshots, lastAttackSnapIdx, si, analyzeLocal))
						{
							attackIntervals.add(interval);
						}
					}
				}
				lastAttackTick = snap.getTick();
				lastAttackSnapIdx = si;
			}
		}

		double attackIntervalVariance = attackIntervals.size() >= 3
			? calcVarianceD(attackIntervals) : -1;

		double attackTimingScore = 0;
		if (attackIntervalVariance >= 0 && attackIntervals.size() >= 5)
		{
			if (attackIntervalVariance < 1.0)
			{
				attackTimingScore = 80;
				flags.add("Robotic attack timing: var=" + String.format("%.2f", attackIntervalVariance));
			}
			else if (attackIntervalVariance < 3.0)
			{
				attackTimingScore = 50;
				flags.add("Tight attack timing: var=" + String.format("%.2f", attackIntervalVariance));
			}
			else if (attackIntervalVariance < 6.0)
			{
				attackTimingScore = 25;
			}
		}

		// No long gaps — a human always has occasional 7+ tick gaps from hesitation/distraction
		if (attackIntervals.size() >= 20)
		{
			int longGaps = 0;
			for (int interval : attackIntervals)
			{
				if (interval >= 7) longGaps++;
			}
			if (longGaps == 0)
			{
				attackTimingScore = Math.min(100, attackTimingScore + 25);
				flags.add("No attack gaps >=7 ticks in " + attackIntervals.size() + " intervals");
			}
		}

		// ============================================================
		// 4. ATTACK-TICK STYLE SWITCHING
		// ============================================================
		int attackTickStyleSwitches = 0;
		int totalAttackTicksForStyle = 0;
		for (int i = 1; i < snapshots.size(); i++)
		{
			TickSnapshot prev = snapshots.get(i - 1);
			TickSnapshot curr = snapshots.get(i);
			if (isDead(curr, analyzeLocal) || isDead(prev, analyzeLocal)) continue;

			int anim = getAnim(curr, analyzeLocal);
			int weapon = getWeapon(curr, analyzeLocal);
			AttackStyle style = CombatUtils.determineAttackStyle(anim, weapon);
			if (style == AttackStyle.UNKNOWN || anim <= 0) continue;

			totalAttackTicksForStyle++;

			int prevWeapon = getWeapon(prev, analyzeLocal);
			if (prevWeapon != weapon && prevWeapon > 0 && weapon > 0)
			{
				AttackStyle prevStyle = CombatUtils.getStyleFromWeapon(prevWeapon);
				if (prevStyle != AttackStyle.UNKNOWN && prevStyle != style)
				{
					attackTickStyleSwitches++;
				}
			}
		}

		double attackTickSwitchRate = totalAttackTicksForStyle > 0
			? (double) attackTickStyleSwitches / totalAttackTicksForStyle * 100 : 0;

		double styleChangeScore = 0;
		if (totalAttackTicksForStyle >= 5)
		{
			if (attackTickSwitchRate >= 60)
			{
				styleChangeScore = 90;
				flags.add("Always switches style on attack: " + attackTickStyleSwitches + "/" + totalAttackTicksForStyle
					+ " (" + String.format("%.0f%%", attackTickSwitchRate) + ")");
			}
			else if (attackTickSwitchRate >= 35)
			{
				styleChangeScore = 60;
				flags.add("Frequent attack-tick style switches: " + attackTickStyleSwitches + "/" + totalAttackTicksForStyle
					+ " (" + String.format("%.0f%%", attackTickSwitchRate) + ")");
			}
			else if (attackTickSwitchRate >= 15)
			{
				styleChangeScore = 35;
				flags.add("Attack-tick style switches: " + attackTickStyleSwitches + "/" + totalAttackTicksForStyle
					+ " (" + String.format("%.0f%%", attackTickSwitchRate) + ")");
			}
		}

		// ============================================================
		// 5. PRAYER REACTION ANALYSIS
		// ============================================================
		int oneTickReactions = 0;
		int twoTickReactions = 0;
		int totalPrayerOpportunities = 0;
		int correctPrayerCount = 0;
		int totalPrayerChecks = 0;

		for (int i = 1; i < snapshots.size(); i++)
		{
			TickSnapshot prev = snapshots.get(i - 1);
			TickSnapshot curr = snapshots.get(i);
			if (prev == null || curr == null) continue;

			int attackerWeapon = analyzeLocal ? curr.getOpponentWeaponId() : curr.getLocalWeaponId();
			int attackerAnim = analyzeLocal ? curr.getOpponentAnimation() : curr.getLocalAnimation();
			HeadIcon defenderPrayer = analyzeLocal ? curr.getLocalOverhead() : curr.getOpponentOverhead();

			AttackStyle attackerStyle = CombatUtils.getStyleFromWeapon(attackerWeapon);
			if (attackerStyle == AttackStyle.UNKNOWN)
			{
				attackerStyle = CombatUtils.getStyleFromAnimation(attackerAnim);
			}

			if (attackerStyle != AttackStyle.UNKNOWN && defenderPrayer != null)
			{
				totalPrayerChecks++;
				if (CombatUtils.isPrayerCorrect(defenderPrayer, attackerStyle))
				{
					correctPrayerCount++;
				}
			}

			int prevAttackerWeapon = analyzeLocal ? prev.getOpponentWeaponId() : prev.getLocalWeaponId();
			if (prevAttackerWeapon != attackerWeapon && attackerWeapon != -1 && prevAttackerWeapon != -1)
			{
				AttackStyle newStyle = CombatUtils.getStyleFromWeapon(attackerWeapon);
				if (newStyle == AttackStyle.UNKNOWN)
				{
					newStyle = CombatUtils.getStyleFromAnimation(attackerAnim);
				}
				int prevAttackerAnim = analyzeLocal ? prev.getOpponentAnimation() : prev.getLocalAnimation();
				AttackStyle oldStyle = CombatUtils.getStyleFromWeapon(prevAttackerWeapon);
				if (oldStyle == AttackStyle.UNKNOWN)
				{
					oldStyle = CombatUtils.getStyleFromAnimation(prevAttackerAnim);
				}

				if (newStyle != AttackStyle.UNKNOWN && oldStyle != AttackStyle.UNKNOWN && newStyle != oldStyle)
				{
					totalPrayerOpportunities++;
					HeadIcon correctPrayer = CombatUtils.getProtectionPrayer(newStyle);

					if (i + 1 < snapshots.size() && snapshots.get(i + 1) != null)
					{
						HeadIcon nextPrayer = analyzeLocal
							? snapshots.get(i + 1).getLocalOverhead()
							: snapshots.get(i + 1).getOpponentOverhead();
						HeadIcon currDefenderPrayer = analyzeLocal
							? curr.getLocalOverhead()
							: curr.getOpponentOverhead();

						if (nextPrayer == correctPrayer && currDefenderPrayer != correctPrayer)
						{
							oneTickReactions++;
						}
						else if (i + 2 < snapshots.size() && snapshots.get(i + 2) != null)
						{
							HeadIcon tick2Prayer = analyzeLocal
								? snapshots.get(i + 2).getLocalOverhead()
								: snapshots.get(i + 2).getOpponentOverhead();
							if (tick2Prayer == correctPrayer)
							{
								twoTickReactions++;
							}
						}
					}
				}
			}
		}

		double prayerAccuracyPct = totalPrayerChecks > 0
			? (double) correctPrayerCount / totalPrayerChecks * 100 : 0;

		double prayerReactionScore = 0;
		if (totalPrayerOpportunities >= 3)
		{
			double oneTickRate = (double) oneTickReactions / totalPrayerOpportunities;
			if (oneTickRate >= 0.8)
			{
				prayerReactionScore = 95;
				flags.add("1-tick prayer reactions: " + oneTickReactions + "/" + totalPrayerOpportunities
					+ " (" + String.format("%.0f%%", oneTickRate * 100) + ")");
			}
			else if (oneTickRate >= 0.5)
			{
				prayerReactionScore = 60;
				flags.add("Frequent 1-tick reactions: " + oneTickReactions + "/" + totalPrayerOpportunities);
			}
			else if (oneTickRate >= 0.3)
			{
				prayerReactionScore = 30;
			}
		}

		double prayerAccuracyScore = 0;
		double prayerAccThresh1 = analyzeLocal ? 99 : 98;
		double prayerAccThresh2 = analyzeLocal ? 97 : 90;
		double prayerAccThresh3 = analyzeLocal ? 95 : 80;
		if (totalPrayerChecks >= 10)
		{
			if (prayerAccuracyPct >= prayerAccThresh1) { prayerAccuracyScore = 90; flags.add("Near-perfect prayer: " + String.format("%.1f%%", prayerAccuracyPct)); }
			else if (prayerAccuracyPct >= prayerAccThresh2) { prayerAccuracyScore = 50; flags.add("High prayer accuracy: " + String.format("%.1f%%", prayerAccuracyPct)); }
			else if (prayerAccuracyPct >= prayerAccThresh3) { prayerAccuracyScore = 20; }
		}

		// ============================================================
		// 7. OFF-PRAY ATTACK ANALYSIS
		// ============================================================
		int attackTickCount = 0;
		int offPrayAttacks = 0;
		int offPraySwitchAttacks = 0;
		for (int i = 0; i < snapshots.size(); i++)
		{
			TickSnapshot snap = snapshots.get(i);
			if (isDead(snap, analyzeLocal)) continue;
			int anim = getAnim(snap, analyzeLocal);
			int weapon = getWeapon(snap, analyzeLocal);
			HeadIcon opponentPrayer = analyzeLocal ? snap.getOpponentOverhead() : snap.getLocalOverhead();
			AttackStyle style = CombatUtils.determineAttackStyle(anim, weapon);
			if (style != AttackStyle.UNKNOWN && anim > 0)
			{
				attackTickCount++;
				if (opponentPrayer != null && !CombatUtils.isPrayerCorrect(opponentPrayer, style))
				{
					offPrayAttacks++;
					if (i > 0)
					{
						int prevWeapon = getWeapon(snapshots.get(i - 1), analyzeLocal);
						if (prevWeapon != weapon) offPraySwitchAttacks++;
					}
				}
			}
		}

		double offPrayScore = 0;
		if (attackTickCount >= 5)
		{
			double offPrayRate = (double) offPrayAttacks / attackTickCount * 100;
			if (offPrayRate >= 70) { offPrayScore = 80; flags.add("Off-prayer attacks: " + String.format("%.0f%%", offPrayRate)); }
			else if (offPrayRate >= 50) { offPrayScore = 50; flags.add("High off-prayer rate: " + String.format("%.0f%%", offPrayRate)); }
			else if (offPrayRate >= 30) { offPrayScore = 25; flags.add("Off-prayer attacks: " + String.format("%.0f%%", offPrayRate)); }

			if (offPraySwitchAttacks >= 3)
			{
				offPrayScore = Math.min(100, offPrayScore + 25);
				flags.add("Switches to off-pray style: " + offPraySwitchAttacks + "x");
			}
		}

		// ============================================================
		// 8. SPEC DETECTION
		// ============================================================
		for (int i = 1; i < snapshots.size(); i++)
		{
			int anim = getAnim(snapshots.get(i), analyzeLocal);
			if (CombatUtils.isSpecAnimation(anim))
			{
				int currWeapon = getWeapon(snapshots.get(i), analyzeLocal);
				int prevWeapon = getWeapon(snapshots.get(i - 1), analyzeLocal);
				if (currWeapon != prevWeapon && prevWeapon != -1)
				{
					int[] prevEquip = getEquip(snapshots.get(i - 1), analyzeLocal);
					int[] currEquip = getEquip(snapshots.get(i), analyzeLocal);
					int switches = CombatUtils.countGearSwitches(prevEquip, currEquip);
					if (switches >= 4)
					{
						flags.add("1-tick " + CombatUtils.getSpecName(anim) + " + " + switches + "-way switch");
					}
				}
			}
		}

		// ============================================================
		// 9. ATTACK-THEN-DEFEND PATTERN
		// Bots attack and immediately switch to defensive gear the very next tick.
		// After ranged attack → staff next tick. After magic attack → tank body/legs/shield next tick.
		// ============================================================
		int attackDefendPerfect = 0;
		int attackDefendOpportunities = 0;

		for (int i = 0; i + 1 < snapshots.size(); i++)
		{
			TickSnapshot attackTick = snapshots.get(i);
			TickSnapshot nextTick = snapshots.get(i + 1);
			if (isDead(attackTick, analyzeLocal) || isDead(nextTick, analyzeLocal)) continue;

			int anim = getAnim(attackTick, analyzeLocal);
			int weapon = getWeapon(attackTick, analyzeLocal);
			AttackStyle attackStyle = CombatUtils.determineAttackStyle(anim, weapon);
			if (attackStyle == AttackStyle.UNKNOWN || anim <= 0) continue;

			// This tick has an attack — check if they switch to defensive gear next tick
			int nextWeapon = getWeapon(nextTick, analyzeLocal);
			if (nextWeapon == weapon || nextWeapon <= 0) continue; // no weapon change

			AttackStyle nextWeaponStyle = CombatUtils.getStyleFromWeapon(nextWeapon);

			// Check for the specific defend patterns:
			// After RANGED attack → staff (MAGIC weapon) equipped next tick
			// After MAGIC attack → different weapon + gear switches (tank gear: body, legs, shield)
			boolean isDefendSwitch = false;

			if (attackStyle == AttackStyle.RANGED && nextWeaponStyle == AttackStyle.MAGIC)
			{
				// Ranged attack → staff next tick (tanking with mage weapon)
				isDefendSwitch = true;
			}
			else if (attackStyle == AttackStyle.MAGIC)
			{
				// Magic attack → check if body/legs/shield changed next tick (tank swap)
				int[] currEquip = getEquip(attackTick, analyzeLocal);
				int[] nextEquip = getEquip(nextTick, analyzeLocal);
				int defensiveSlotsChanged = 0;
				// Check torso (4), shield (5), legs (7)
				if (currEquip.length > 7 && nextEquip.length > 7)
				{
					if (currEquip[4] != nextEquip[4]) defensiveSlotsChanged++; // body
					if (currEquip[5] != nextEquip[5]) defensiveSlotsChanged++; // shield
					if (currEquip[7] != nextEquip[7]) defensiveSlotsChanged++; // legs
				}
				if (defensiveSlotsChanged >= 2)
				{
					isDefendSwitch = true;
				}
			}
			else if (attackStyle == AttackStyle.MELEE)
			{
				// Melee attack → staff or tank gear next tick
				int[] currEquip = getEquip(attackTick, analyzeLocal);
				int[] nextEquip = getEquip(nextTick, analyzeLocal);
				int slotsChanged = CombatUtils.countGearSwitches(currEquip, nextEquip);
				if (slotsChanged >= 3 && (nextWeaponStyle == AttackStyle.MAGIC || nextWeaponStyle == AttackStyle.RANGED))
				{
					isDefendSwitch = true;
				}
			}

			if (isDefendSwitch)
			{
				attackDefendOpportunities++;
				attackDefendPerfect++;
			}
			else
			{
				// They attacked but didn't switch to defense next tick
				// Only count as opportunity if they DID switch weapon (just to a different attack style)
				if (nextWeapon != weapon)
				{
					attackDefendOpportunities++;
				}
			}
		}

		double attackDefendScore = 0;
		if (attackDefendOpportunities >= 5)
		{
			double defendRate = (double) attackDefendPerfect / attackDefendOpportunities * 100;
			if (defendRate >= 90)
			{
				attackDefendScore = 90;
				flags.add("Perfect attack→defend: " + attackDefendPerfect + "/" + attackDefendOpportunities
					+ " (" + String.format("%.0f%%", defendRate) + ") always tanks next tick");
			}
			else if (defendRate >= 70)
			{
				attackDefendScore = 60;
				flags.add("Consistent attack→defend: " + attackDefendPerfect + "/" + attackDefendOpportunities
					+ " (" + String.format("%.0f%%", defendRate) + ")");
			}
			else if (defendRate >= 50)
			{
				attackDefendScore = 35;
				flags.add("Attack→defend pattern: " + attackDefendPerfect + "/" + attackDefendOpportunities
					+ " (" + String.format("%.0f%%", defendRate) + ")");
			}
		}
		else if (attackDefendOpportunities >= 3 && attackDefendPerfect == attackDefendOpportunities)
		{
			attackDefendScore = 40;
			flags.add("Attack→defend: " + attackDefendPerfect + "/" + attackDefendOpportunities + " (100%)");
		}

		// ============================================================
		// 10. CLICK ANALYSIS (self-analysis only — uses real-time click data)
		// ============================================================
		double clickScore = 0;
		if (analyzeLocal)
		{
			List<Long> allClickTimestamps = new ArrayList<>();
			int maxClicksInTick = 0;
			List<Integer> clicksPerTick = new ArrayList<>();

			for (TickSnapshot snap : snapshots)
			{
				if (snap == null) continue;
				List<ClickEvent> clicks = snap.getLocalClicks();
				if (clicks == null || clicks.isEmpty()) continue;

				clicksPerTick.add(clicks.size());
				maxClicksInTick = Math.max(maxClicksInTick, clicks.size());

				for (ClickEvent click : clicks)
				{
					if (click != null && click.getTimestampMs() > 0)
					{
						allClickTimestamps.add(click.getTimestampMs());
					}
				}
			}

			// Analyze intervals between consecutive clicks
			if (allClickTimestamps.size() >= 5)
			{
				List<Long> clickIntervals = new ArrayList<>();
				for (int i = 1; i < allClickTimestamps.size(); i++)
				{
					long interval = allClickTimestamps.get(i) - allClickTimestamps.get(i - 1);
					if (interval > 0 && interval < 2000) // ignore gaps > 2 seconds
					{
						clickIntervals.add(interval);
					}
				}

				if (clickIntervals.size() >= 5)
				{
					// Average click interval
					double avgInterval = clickIntervals.stream().mapToLong(Long::longValue).average().orElse(0);

					// Variance of click intervals
					double clickVariance = 0;
					for (long interval : clickIntervals)
					{
						clickVariance += (interval - avgInterval) * (interval - avgInterval);
					}
					clickVariance /= clickIntervals.size();
					double clickStdDev = Math.sqrt(clickVariance);

					// Coefficient of variation (stddev / mean) — lower = more robotic
					double cv = avgInterval > 0 ? clickStdDev / avgInterval : 0;

					// Inhuman click speed: average < 30ms between clicks
					if (avgInterval < 30)
					{
						clickScore += 50;
						flags.add("Inhuman click speed: avg " + String.format("%.0fms", avgInterval) + " between clicks");
					}
					else if (avgInterval < 60)
					{
						clickScore += 25;
						flags.add("Very fast clicks: avg " + String.format("%.0fms", avgInterval));
					}

					// Robotic click consistency (very low coefficient of variation)
					if (cv < 0.15 && clickIntervals.size() >= 10)
					{
						clickScore += 40;
						flags.add("Robotic click timing: CV=" + String.format("%.2f", cv)
							+ " (avg " + String.format("%.0fms", avgInterval) + " ±" + String.format("%.0fms", clickStdDev) + ")");
					}
					else if (cv < 0.25 && clickIntervals.size() >= 10)
					{
						clickScore += 20;
						flags.add("Consistent click timing: CV=" + String.format("%.2f", cv));
					}
				}
			}

			clickScore = Math.min(100, clickScore);
		}

		// ============================================================
		// CONSISTENCY SCORE
		// ============================================================
		double consistencyScore = 0;
		consistencyScore += loadoutScore * 0.40;
		consistencyScore += styleChangeScore * 0.30;
		consistencyScore += attackTimingScore * 0.30;
		consistencyScore = Math.min(100, consistencyScore);

		// ============================================================
		// OVERALL SCORE
		// Opponent: Gear 12%, Loadout 12%, Atk→Def 12%, Consistency 20%,
		//           Off-pray 8%, Style switch 6%, Atk timing 15%, Prayer react 8%, Prayer acc 7%
		// Self: same but with Click analysis 10% (reduces other weights slightly)
		// ============================================================
		double overallScore;
		if (analyzeLocal && clickScore > 0)
		{
			overallScore = (gearSwitchScore * 0.10)
				+ (loadoutScore * 0.10)
				+ (attackDefendScore * 0.10)
				+ (consistencyScore * 0.18)
				+ (offPrayScore * 0.07)
				+ (styleChangeScore * 0.05)
				+ (clickScore * 0.10)
				+ (attackTimingScore * 0.13)
				+ (prayerReactionScore * 0.08)
				+ (prayerAccuracyScore * 0.09);
		}
		else
		{
			overallScore = (gearSwitchScore * 0.15)
				+ (loadoutScore * 0.15)
				+ (attackDefendScore * 0.15)
				+ (consistencyScore * 0.20)
				+ (offPrayScore * 0.10)
				+ (styleChangeScore * 0.10)
				+ (attackTimingScore * 0.15)
				+ (prayerReactionScore * 0.08)
				+ (prayerAccuracyScore * 0.07);
		}

		// Multi-indicator boost
		int highIndicators = 0;
		if (gearSwitchScore >= 30) highIndicators++;
		if (loadoutScore >= 30) highIndicators++;
		if (attackDefendScore >= 30) highIndicators++;
		if (consistencyScore >= 25) highIndicators++;
		if (styleChangeScore >= 25) highIndicators++;
		if (offPrayScore >= 20) highIndicators++;
		if (attackTimingScore >= 20) highIndicators++;
		if (prayerReactionScore >= 25) highIndicators++;
		if (analyzeLocal && clickScore >= 20) highIndicators++;

		if (highIndicators >= 5)
		{
			overallScore = Math.min(100, overallScore * 1.7);
			flags.add("Multiple suspicious indicators (" + highIndicators + "/8)");
		}
		else if (highIndicators >= 4)
		{
			overallScore = Math.min(100, overallScore * 1.5);
			flags.add("Multiple suspicious indicators (" + highIndicators + "/8)");
		}
		else if (highIndicators >= 3)
		{
			overallScore = Math.min(100, overallScore * 1.3);
			flags.add("Multiple suspicious indicators (" + highIndicators + "/8)");
		}
		else if (highIndicators >= 2)
		{
			overallScore = Math.min(100, overallScore * 1.15);
		}

		return CheatAnalysis.builder()
			.playerName(playerName)
			.overallScore(Math.min(100, overallScore))
			.gearSwitchScore(gearSwitchScore)
			.prayerReactionScore(Math.min(100, prayerReactionScore))
			.prayerAccuracyScore(prayerAccuracyScore)
			.consistencyScore(consistencyScore)
			.maxGearSwitchesInTick(maxSwitches)
			.avgGearSwitchesPerTick(avgSwitches)
			.zeroTickPrayerReactions(oneTickReactions)
			.totalPrayerOpportunities(totalPrayerOpportunities)
			.correctPrayerCount(correctPrayerCount)
			.prayerAccuracyPct(prayerAccuracyPct)
			.prayerUptime(0)
			.uniqueLoadouts(totalUniqueLoadouts)
			.reactionAsymmetry(0)
			.attackIntervalVariance(attackIntervalVariance)
			.loadoutPerfection(loadoutPerfection)
			.attackDefendScore(attackDefendScore)
			.clickAnalysisScore(clickScore)
			.flags(flags)
			.build();
	}

	// --- Helpers ---

	private static boolean isDead(TickSnapshot snap, boolean local)
	{
		if (snap == null) return true;
		if (local)
		{
			return snap.isLocalDied() || snap.getLocalEstimatedHp() == 0
				|| snap.getLocalWeaponId() == -1;
		}
		return snap.isOpponentDied() || snap.getOpponentEstimatedHp() == 0
			|| snap.getOpponentWeaponId() == -1;
	}

	private static final int[] EMPTY_EQUIP = new int[0];

	private static int[] getEquip(TickSnapshot snap, boolean local)
	{
		int[] equip = local ? snap.getLocalEquipment() : snap.getOpponentEquipment();
		return equip != null ? equip : EMPTY_EQUIP;
	}

	private static int getWeapon(TickSnapshot snap, boolean local)
	{
		if (snap == null) return -1;
		return local ? snap.getLocalWeaponId() : snap.getOpponentWeaponId();
	}

	private static int getAnim(TickSnapshot snap, boolean local)
	{
		if (snap == null) return -1;
		return local ? snap.getLocalAnimation() : snap.getOpponentAnimation();
	}

	private static double calcVariance(List<Integer> values)
	{
		if (values.size() < 2) return 0;
		double mean = values.stream().mapToInt(Integer::intValue).average().orElse(0);
		double sum = 0;
		for (int v : values) sum += (v - mean) * (v - mean);
		return sum / values.size();
	}

	private static double calcVarianceD(List<Integer> values)
	{
		if (values.size() < 2) return 0;
		double mean = values.stream().mapToDouble(Integer::doubleValue).average().orElse(0);
		double sum = 0;
		for (int v : values) sum += (v - mean) * (v - mean);
		return sum / values.size();
	}

	/**
	 * Check if an interrupting action occurred between two attack snapshot indices.
	 * Interrupting actions are things that would reset or delay the auto-attack timer:
	 * - Gear/weapon switch (equipment changed)
	 * - Eating (HP increased between ticks)
	 * - Movement (for self-analysis: "Walk here" clicks)
	 *
	 * If none of these occurred, the player was just standing still auto-retaliating
	 * and consistent timing is expected from the game engine, not suspicious.
	 */
	private static boolean hadInterruptingAction(List<TickSnapshot> snapshots, int fromIdx, int toIdx, boolean analyzeLocal)
	{
		for (int i = fromIdx; i < toIdx && i + 1 < snapshots.size(); i++)
		{
			TickSnapshot curr = snapshots.get(i);
			TickSnapshot next = snapshots.get(i + 1);
			if (curr == null || next == null) continue;

			// Gear switch: equipment changed between ticks
			int[] currEquip = getEquip(curr, analyzeLocal);
			int[] nextEquip = getEquip(next, analyzeLocal);
			if (CombatUtils.countGearSwitches(currEquip, nextEquip) > 0)
			{
				return true;
			}

			// Eating: HP increased between ticks
			int currHp = analyzeLocal ? curr.getLocalEstimatedHp() : curr.getOpponentEstimatedHp();
			int nextHp = analyzeLocal ? next.getLocalEstimatedHp() : next.getOpponentEstimatedHp();
			if (currHp > 0 && nextHp > currHp)
			{
				return true;
			}

			// Movement (self only): check for "Walk here" clicks
			if (analyzeLocal && next.getLocalClicks() != null)
			{
				for (ClickEvent click : next.getLocalClicks())
				{
					if (click != null && click.getMenuOption() != null
						&& click.getMenuOption().equals("Walk here"))
					{
						return true;
					}
				}
			}
		}

		return false;
	}
}
