package com.ahkusations;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class FightSession
{
	private final String opponentName;
	private final int opponentCombatLevel;
	private final int startTick;
	private final long startTimeMs;
	private final List<TickSnapshot> snapshots = new ArrayList<>();

	@Setter
	private int endTick;
	@Setter
	private boolean active = true;

	public FightSession(String opponentName, int opponentCombatLevel, int startTick)
	{
		this(opponentName, opponentCombatLevel, startTick, System.currentTimeMillis());
	}

	public FightSession(String opponentName, int opponentCombatLevel, int startTick, long startTimeMs)
	{
		this.opponentName = opponentName;
		this.opponentCombatLevel = opponentCombatLevel;
		this.startTick = startTick;
		this.startTimeMs = startTimeMs;
	}

	public void addSnapshot(TickSnapshot snapshot)
	{
		snapshots.add(snapshot);
	}

	public int getDurationTicks()
	{
		return Math.max(0, endTick - startTick);
	}

	public double getDurationSeconds()
	{
		return getDurationTicks() * 0.6;
	}
}
