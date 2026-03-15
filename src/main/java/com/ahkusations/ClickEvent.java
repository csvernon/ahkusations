package com.ahkusations;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor(force = true)
@AllArgsConstructor
public class ClickEvent
{
	private final long timestampMs;  // System.currentTimeMillis() at click time
	private final int widgetGroupId; // which interface widget was clicked (inventory, prayer, etc.)
	private final String menuOption; // e.g. "Wield", "Wear", "Eat", "Activate", "Use Special Attack"
	private final String menuTarget; // e.g. item/prayer name
	private final int actionParam;   // item slot or action param
}
