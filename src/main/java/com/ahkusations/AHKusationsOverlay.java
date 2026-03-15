package com.ahkusations;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

public class AHKusationsOverlay extends OverlayPanel
{
	private final AHKusationsPlugin plugin;
	private final AHKusationsConfig config;

	// Live analysis state (updated during fight)
	// Volatile: written on game thread, read on EDT render thread
	private volatile CheatAnalysis liveOpponentAnalysis;
	private volatile CheatAnalysis liveSelfAnalysis;
	private volatile String liveOpponentName;
	private volatile boolean fighting;

	// Last completed fight (persists on screen)
	private volatile CheatAnalysis lastOpponentAnalysis;
	private volatile CheatAnalysis lastSelfAnalysis;
	private volatile String lastOpponentName;
	private volatile String localPlayerName;

	// Throttle live analysis to every 5 ticks
	private int lastAnalysisTick;
	private static final int ANALYSIS_INTERVAL = 5;

	@Inject
	public AHKusationsOverlay(AHKusationsPlugin plugin, AHKusationsConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setPriority(OverlayPriority.HIGH);
	}

	public void onFightStarted(String opponentName, String localName)
	{
		liveOpponentName = opponentName;
		localPlayerName = localName;
		liveOpponentAnalysis = null;
		liveSelfAnalysis = null;
		fighting = true;
		lastAnalysisTick = 0;
	}

	public void updateLiveAnalysis(FightSession session, int currentTick)
	{
		if (!fighting || session == null)
		{
			return;
		}

		if (currentTick - lastAnalysisTick < ANALYSIS_INTERVAL)
		{
			return;
		}
		lastAnalysisTick = currentTick;

		if (session.getSnapshots().size() < 2)
		{
			return;
		}

		try
		{
			liveOpponentAnalysis = CheatDetector.analyzeOpponent(session);
			if (true)
			{
				liveSelfAnalysis = CheatDetector.analyzeLocal(session);
			}
		}
		catch (Exception ignored)
		{
		}
	}

	public void onFightEnded(FightHistoryEntry entry, String localName)
	{
		fighting = false;
		liveOpponentAnalysis = null;
		liveSelfAnalysis = null;
		liveOpponentName = null;

		if (entry != null)
		{
			lastOpponentName = entry.getOpponentName();
			lastOpponentAnalysis = entry.getOpponentAnalysis();
			lastSelfAnalysis = entry.getSelfAnalysis();
			localPlayerName = localName;
		}
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		try
		{
			if (!config.showOverlay())
			{
				return null;
			}

			// Snapshot volatile fields into locals for consistent reads
			boolean isFighting = fighting;
			CheatAnalysis oppAnalysis;
			CheatAnalysis selfAnalysis;
			String oppName;
			String localName = localPlayerName;

			if (isFighting)
			{
				oppAnalysis = liveOpponentAnalysis;
				selfAnalysis = liveSelfAnalysis;
				oppName = liveOpponentName;
			}
			else
			{
				oppAnalysis = lastOpponentAnalysis;
				selfAnalysis = lastSelfAnalysis;
				oppName = lastOpponentName;
			}

			// Nothing to show yet
			if (oppAnalysis == null && oppName == null)
			{
				return null;
			}

			panelComponent.getChildren().add(TitleComponent.builder()
				.text("AHKusations")
				.color(Color.WHITE)
				.build());

			if (isFighting)
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left("Status:")
					.leftColor(Color.YELLOW)
					.right("LIVE")
					.rightColor(Color.YELLOW)
					.build());
			}

			// Opponent section
			if (oppName != null)
			{
				panelComponent.getChildren().add(LineComponent.builder()
					.left(oppName)
					.leftColor(Color.WHITE)
					.build());

				if (oppAnalysis != null)
				{
					String verdict = oppAnalysis.getVerdict();
					Color verdictColor = getVerdictColor(oppAnalysis.getOverallScore());

					panelComponent.getChildren().add(LineComponent.builder()
						.left("Score:")
						.right(String.format("%.0f", oppAnalysis.getOverallScore()))
						.rightColor(verdictColor)
						.build());

					panelComponent.getChildren().add(LineComponent.builder()
						.left("Verdict:")
						.right(verdict)
						.rightColor(verdictColor)
						.build());
				}
				else if (isFighting)
				{
					panelComponent.getChildren().add(LineComponent.builder()
						.left("Analyzing...")
						.leftColor(Color.GRAY)
						.build());
				}
			}

			// Self section
			if (selfAnalysis != null && localName != null)
			{
				panelComponent.getChildren().add(LineComponent.builder().build()); // spacer

				panelComponent.getChildren().add(LineComponent.builder()
					.left(localName + " (You)")
					.leftColor(Color.CYAN)
					.build());

				String selfVerdict = selfAnalysis.getVerdict();
				Color selfColor = getVerdictColor(selfAnalysis.getOverallScore());

				panelComponent.getChildren().add(LineComponent.builder()
					.left("Score:")
					.right(String.format("%.0f", selfAnalysis.getOverallScore()))
					.rightColor(selfColor)
					.build());

				panelComponent.getChildren().add(LineComponent.builder()
					.left("Verdict:")
					.right(selfVerdict)
					.rightColor(selfColor)
					.build());
			}

			return super.render(graphics);
		}
		catch (Exception e)
		{
			return null;
		}
	}

	private static Color getVerdictColor(double score)
	{
		if (score >= 75) return new Color(255, 50, 50);
		if (score >= 55) return new Color(255, 140, 0);
		if (score >= 35) return Color.YELLOW;
		if (score >= 15) return new Color(144, 238, 144);
		return Color.GREEN;
	}
}
