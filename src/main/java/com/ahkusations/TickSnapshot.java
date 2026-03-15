package com.ahkusations;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.runelite.api.HeadIcon;

import java.util.List;

@Data
@Builder
@NoArgsConstructor(force = true)
@AllArgsConstructor
public class TickSnapshot
{
	private final int tick;

	// Local player state
	private final int[] localEquipment;
	private final HeadIcon localOverhead;
	private final int localAnimation;
	private final int localWeaponId;
	private final int localEstimatedHp;

	// Opponent state
	private final int[] opponentEquipment;
	private final HeadIcon opponentOverhead;
	private final int opponentAnimation;
	private final int opponentWeaponId;
	private final int opponentEstimatedHp;
	private final int opponentSpotAnim;  // For detecting dark bow spec, dragon crossbow spec graphics

	// Hitsplats this tick
	private final List<Integer> hitsOnLocal;
	private final List<Integer> hitsOnOpponent;

	// Whether a death animation played this tick
	private final boolean localDied;
	private final boolean opponentDied;

	// Local player's active interface tab (varc int 171)
	// 0=Combat, 3=Inventory, 5=Prayer, 6=Magic, etc.
	private final int localActiveTab;

	// Local player's clicks this tick (captured from MenuOptionClicked, sub-tick precision)
	private final List<ClickEvent> localClicks;
}
