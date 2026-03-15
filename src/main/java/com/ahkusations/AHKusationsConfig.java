package com.ahkusations;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("ahkusations")
public interface AHKusationsConfig extends Config
{
	@ConfigSection(
		name = "Detection Thresholds",
		description = "Thresholds for cheat detection scoring",
		position = 0
	)
	String thresholdsSection = "thresholds";

	@ConfigItem(
		keyName = "minFightTicks",
		name = "Min fight duration (ticks)",
		description = "Minimum fight length in ticks before analysis runs",
		section = thresholdsSection,
		position = 1
	)
	default int minFightTicks()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "maxFightHistory",
		name = "Max fight history",
		description = "Maximum number of fights to keep in history",
		position = 2
	)
	default int maxFightHistory()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "fightEndTimeout",
		name = "Fight end timeout (ticks)",
		description = "Ticks of no interaction before a fight is considered over",
		position = 3
	)
	default int fightEndTimeout()
	{
		return 16;
	}

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show overlay",
		description = "Show in-game overlay with cheat analysis during and after fights",
		position = 5
	)
	default boolean showOverlay()
	{
		return true;
	}
}
