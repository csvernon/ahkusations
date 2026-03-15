package com.ahkusations;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor(force = true)
@AllArgsConstructor
public class FightHistoryEntry
{
	private final String opponentName;
	private final int opponentCombatLevel;
	private final int durationTicks;
	private final double durationSeconds;
	private final long timestampMs;
	private final int totalTicks;
	private final CheatAnalysis opponentAnalysis;
	private final CheatAnalysis selfAnalysis;
	private String userLabel; // "CHEATER", "LEGIT", or null
}
