package com.ahkusations;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor(force = true)
@AllArgsConstructor
public class CheatAnalysis
{
	private final String playerName;
	private final double overallScore;

	// Category scores (0-100)
	private final double gearSwitchScore;
	private final double prayerReactionScore;
	private final double prayerAccuracyScore;
	private final double consistencyScore;

	// Raw stats
	private final int maxGearSwitchesInTick;
	private final double avgGearSwitchesPerTick;
	private final int zeroTickPrayerReactions;
	private final int totalPrayerOpportunities;
	private final int correctPrayerCount;
	private final double prayerAccuracyPct;

	// New metrics
	private final double prayerUptime;           // % of ticks with any prayer active
	private final int uniqueLoadouts;             // number of unique gear configurations
	private final double reactionAsymmetry;       // difference in reaction rate between styles
	private final double attackIntervalVariance;  // how regular attack timing is
	private final double loadoutPerfection;       // % of switches that match a "template"
	private final double attackDefendScore;        // attack then immediately equip defensive gear
	private final double clickAnalysisScore;      // self-analysis: click speed and consistency

	// Human-readable flags
	private final List<String> flags;

	public String getVerdict()
	{
		if (overallScore >= 80)
		{
			return "LIKELY CHEATING";
		}
		if (overallScore >= 65)
		{
			return "SUSPICIOUS";
		}
		if (overallScore >= 50)
		{
			return "SLIGHT SUSPICION";
		}
		if (overallScore >= 35)
		{
			return "PROBABLY LEGIT";
		}
		return "LEGIT";
	}
}
