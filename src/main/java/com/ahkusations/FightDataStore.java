package com.ahkusations;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
public class FightDataStore
{
	private static final Path DATA_DIR = Paths.get(System.getProperty("user.home"), ".runelite", "ahkusations");
	private static final Path RAW_DATA_DIR = DATA_DIR.resolve("raw");

	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	public FightDataStore()
	{
		try
		{
			Files.createDirectories(DATA_DIR);
			Files.createDirectories(RAW_DATA_DIR);
		}
		catch (IOException e)
		{
			log.warn("Failed to create AHKusations data directory: {}", e.getMessage());
		}
	}

	/**
	 * Save raw tick-by-tick fight data for later re-analysis.
	 */
	public void saveRawFight(FightSession session)
	{
		if (session == null || session.getOpponentName() == null) return;
		String safeName = session.getOpponentName().replaceAll("[^a-zA-Z0-9_-]", "_");
		String fileName = session.getStartTimeMs() + "_" + safeName + ".json";
		Path file = RAW_DATA_DIR.resolve(fileName);

		RawFightData data = new RawFightData();
		data.opponentName = session.getOpponentName();
		data.opponentCombatLevel = session.getOpponentCombatLevel();
		data.startTick = session.getStartTick();
		data.endTick = session.getEndTick();
		data.startTimeMs = session.getStartTimeMs();
		data.durationTicks = session.getDurationTicks();
		data.durationSeconds = session.getDurationSeconds();
		data.snapshots = session.getSnapshots();

		try (Writer writer = Files.newBufferedWriter(file))
		{
			gson.toJson(data, writer);
			log.debug("Saved raw fight data: {}", fileName);
		}
		catch (IOException e)
		{
			log.warn("Failed to save raw fight data: {}", e.getMessage());
		}
	}

	/**
	 * Load all raw fight files, re-analyze with current algorithm, return fresh history.
	 */
	public List<FightHistoryEntry> loadAndReanalyze(boolean analyzeSelf, int maxHistory)
	{
		List<RawFightData> allFights = new ArrayList<>();

		try (DirectoryStream<Path> stream = Files.newDirectoryStream(RAW_DATA_DIR, "*.json"))
		{
			for (Path file : stream)
			{
				try (Reader reader = Files.newBufferedReader(file))
				{
					RawFightData data = gson.fromJson(reader, RawFightData.class);
					if (data != null && data.snapshots != null && data.snapshots.size() >= 2)
					{
						allFights.add(data);
					}
				}
				catch (Exception e)
				{
					log.warn("Failed to load raw fight {}: {}", file.getFileName(), e.getMessage());
				}
			}
		}
		catch (IOException e)
		{
			log.warn("Failed to read raw fight directory: {}", e.getMessage());
			return new ArrayList<>();
		}

		// Sort by timestamp descending (newest first)
		allFights.sort(Comparator.comparingLong((RawFightData d) -> d.startTimeMs).reversed());

		// Trim to max history
		if (allFights.size() > maxHistory)
		{
			allFights = allFights.subList(0, maxHistory);
		}

		// Re-analyze each fight with the current algorithm
		List<FightHistoryEntry> history = new ArrayList<>();
		for (RawFightData raw : allFights)
		{
			try
			{
				FightSession session = new FightSession(raw.opponentName, raw.opponentCombatLevel, raw.startTick, raw.startTimeMs);
				session.setEndTick(raw.endTick);
				session.setActive(false);
				for (TickSnapshot snap : raw.snapshots)
				{
					if (snap != null)
					{
						session.addSnapshot(snap);
					}
				}

				CheatAnalysis opponentAnalysis = CheatDetector.analyzeOpponent(session);
				CheatAnalysis selfAnalysis = analyzeSelf ? CheatDetector.analyzeLocal(session) : null;

				FightHistoryEntry entry = FightHistoryEntry.builder()
					.opponentName(raw.opponentName)
					.opponentCombatLevel(raw.opponentCombatLevel)
					.durationTicks(raw.durationTicks)
					.durationSeconds(raw.durationSeconds)
					.timestampMs(raw.startTimeMs)
					.totalTicks(raw.snapshots.size())
					.opponentAnalysis(opponentAnalysis)
					.selfAnalysis(selfAnalysis)
					.userLabel(raw.userLabel)
					.build();

				history.add(entry);

				log.debug("Re-analyzed fight vs {} | Score: {}", raw.opponentName,
					String.format("%.1f", opponentAnalysis.getOverallScore()));
			}
			catch (Exception e)
			{
				log.warn("Failed to re-analyze fight vs {}: {}", raw.opponentName, e.getMessage());
			}
		}

		log.info("Loaded and re-analyzed {} fights from disk", history.size());
		return history;
	}

	/**
	 * Container for raw fight data serialization.
	 */
	/**
	 * Update the userLabel field in a raw fight JSON file identified by its timestamp.
	 * @param label "CHEATER", "LEGIT", or null to clear
	 */
	public void setUserLabel(long timestampMs, String opponentName, String label)
	{
		if (opponentName == null) return;
		String safeName = opponentName.replaceAll("[^a-zA-Z0-9_-]", "_");
		Path file = RAW_DATA_DIR.resolve(timestampMs + "_" + safeName + ".json");
		if (!Files.exists(file))
		{
			log.warn("Cannot set label — raw fight file not found: {}", file.getFileName());
			return;
		}

		try
		{
			RawFightData data;
			try (Reader reader = Files.newBufferedReader(file))
			{
				data = gson.fromJson(reader, RawFightData.class);
			}
			if (data == null) return;

			data.userLabel = label;

			try (Writer writer = Files.newBufferedWriter(file))
			{
				gson.toJson(data, writer);
			}
			log.info("Set label '{}' on fight vs {} ({})", label, opponentName, timestampMs);
		}
		catch (IOException e)
		{
			log.warn("Failed to update label for fight {}: {}", file.getFileName(), e.getMessage());
		}
	}

	static class RawFightData
	{
		String opponentName;
		int opponentCombatLevel;
		int startTick;
		int endTick;
		long startTimeMs;
		int durationTicks;
		double durationSeconds;
		List<TickSnapshot> snapshots;
		String userLabel; // "CHEATER", "LEGIT", or null
	}
}
