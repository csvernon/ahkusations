package com.ahkusations;

import net.runelite.api.HeadIcon;
import net.runelite.api.kit.KitType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CombatUtils
{
	// Equipment slots relevant to combat gear switching (excludes cosmetic: ARMS=6, HAIR=8, JAW=11)
	public static final int[] COMBAT_SLOTS = {
		KitType.HEAD.getIndex(),    // 0
		KitType.CAPE.getIndex(),    // 1
		KitType.AMULET.getIndex(),  // 2
		KitType.WEAPON.getIndex(),  // 3
		KitType.TORSO.getIndex(),   // 4
		KitType.SHIELD.getIndex(),  // 5
		KitType.LEGS.getIndex(),    // 7
		KitType.HANDS.getIndex(),   // 9
		KitType.BOOTS.getIndex(),   // 10
	};

	public static final int ITEM_OFFSET = 512;

	// Death animation IDs (from pvp-performance-tracker)
	public static final Set<Integer> DEATH_ANIMATIONS = Set.of(645, 10629, 11902);

	// Dark bow spec graphic on TARGET (not the attacker)
	public static final int GFX_DBOW_SPEC = 1100;
	// Dragon crossbow spec graphic on TARGET
	public static final int GFX_DCBOW_SPEC = 157;

	// Weapon ID -> attack style (common PvP weapons)
	private static final Map<Integer, AttackStyle> WEAPON_STYLES = new HashMap<>();

	// Animation ID -> attack style (comprehensive, sourced from pvp-performance-tracker)
	private static final Map<Integer, AttackStyle> ANIMATION_STYLES = new HashMap<>();

	// Known special attack animation IDs
	private static final Set<Integer> SPEC_ANIMATIONS = new HashSet<>();

	// Spec animation -> weapon name (for logging)
	private static final Map<Integer, String> SPEC_NAMES = new HashMap<>();

	// Weapons that use animation ID 0 and need weapon-based detection
	private static final Set<Integer> ANIM_ZERO_WEAPONS = new HashSet<>();

	static
	{
		// =====================================================================
		// MELEE WEAPONS
		// =====================================================================
		WEAPON_STYLES.put(4151, AttackStyle.MELEE);   // Abyssal whip
		WEAPON_STYLES.put(12006, AttackStyle.MELEE);  // Abyssal tentacle
		WEAPON_STYLES.put(11802, AttackStyle.MELEE);  // Armadyl godsword
		WEAPON_STYLES.put(11804, AttackStyle.MELEE);  // Bandos godsword
		WEAPON_STYLES.put(11806, AttackStyle.MELEE);  // Saradomin godsword
		WEAPON_STYLES.put(11808, AttackStyle.MELEE);  // Zamorak godsword
		WEAPON_STYLES.put(13652, AttackStyle.MELEE);  // Dragon claws
		WEAPON_STYLES.put(4153, AttackStyle.MELEE);   // Granite maul
		WEAPON_STYLES.put(12848, AttackStyle.MELEE);  // Granite maul (or)
		WEAPON_STYLES.put(4587, AttackStyle.MELEE);   // Dragon scimitar
		WEAPON_STYLES.put(1215, AttackStyle.MELEE);   // Dragon dagger
		WEAPON_STYLES.put(5680, AttackStyle.MELEE);   // Dragon dagger (p+)
		WEAPON_STYLES.put(5698, AttackStyle.MELEE);   // Dragon dagger (p++)
		WEAPON_STYLES.put(21003, AttackStyle.MELEE);  // Elder maul
		WEAPON_STYLES.put(24417, AttackStyle.MELEE);  // Inquisitor's mace
		WEAPON_STYLES.put(27690, AttackStyle.MELEE);  // Voidwaker (melee weapon, but spec is magic)
		WEAPON_STYLES.put(11838, AttackStyle.MELEE);  // Saradomin sword
		WEAPON_STYLES.put(22325, AttackStyle.MELEE);  // Scythe of vitur
		WEAPON_STYLES.put(22324, AttackStyle.MELEE);  // Ghrazi rapier
		WEAPON_STYLES.put(26219, AttackStyle.MELEE);  // Osmumten's fang
		WEAPON_STYLES.put(13576, AttackStyle.MELEE);  // Dragon warhammer
		WEAPON_STYLES.put(20784, AttackStyle.MELEE);  // Dragon sword
		WEAPON_STYLES.put(12809, AttackStyle.MELEE);  // Saradomin's blessed sword
		WEAPON_STYLES.put(1305, AttackStyle.MELEE);   // Dragon longsword
		WEAPON_STYLES.put(1434, AttackStyle.MELEE);   // Dragon mace
		WEAPON_STYLES.put(4718, AttackStyle.MELEE);   // Dharok's greataxe
		WEAPON_STYLES.put(4710, AttackStyle.MELEE);   // Ahrim's staff (melee)
		WEAPON_STYLES.put(4726, AttackStyle.MELEE);   // Guthan's warspear
		WEAPON_STYLES.put(4747, AttackStyle.MELEE);   // Torag's hammers
		WEAPON_STYLES.put(4755, AttackStyle.MELEE);   // Verac's flail
		WEAPON_STYLES.put(21015, AttackStyle.MELEE);  // Dinh's bulwark
		WEAPON_STYLES.put(13263, AttackStyle.MELEE);  // Abyssal bludgeon
		WEAPON_STYLES.put(13265, AttackStyle.MELEE);  // Abyssal dagger

		// =====================================================================
		// RANGED WEAPONS
		// =====================================================================
		WEAPON_STYLES.put(861, AttackStyle.RANGED);   // Magic shortbow
		WEAPON_STYLES.put(12788, AttackStyle.RANGED); // Magic shortbow (i)
		WEAPON_STYLES.put(11235, AttackStyle.RANGED); // Dark bow
		WEAPON_STYLES.put(11785, AttackStyle.RANGED); // Armadyl crossbow
		WEAPON_STYLES.put(21902, AttackStyle.RANGED); // Dragon crossbow
		WEAPON_STYLES.put(26374, AttackStyle.RANGED); // Zaryte crossbow
		WEAPON_STYLES.put(19481, AttackStyle.RANGED); // Heavy ballista
		WEAPON_STYLES.put(19484, AttackStyle.RANGED); // Light ballista
		WEAPON_STYLES.put(12926, AttackStyle.RANGED); // Toxic blowpipe
		WEAPON_STYLES.put(22804, AttackStyle.RANGED); // Dragon knife
		WEAPON_STYLES.put(22806, AttackStyle.RANGED); // Dragon knife (p)
		WEAPON_STYLES.put(22808, AttackStyle.RANGED); // Dragon knife (p+)
		WEAPON_STYLES.put(22810, AttackStyle.RANGED); // Dragon knife (p++)
		WEAPON_STYLES.put(25862, AttackStyle.RANGED); // Bow of faerdhinen
		WEAPON_STYLES.put(25865, AttackStyle.RANGED); // Bow of faerdhinen (c)
		WEAPON_STYLES.put(28688, AttackStyle.RANGED); // Webweaver bow
		WEAPON_STYLES.put(4734, AttackStyle.RANGED);  // Karil's crossbow

		// =====================================================================
		// MAGIC WEAPONS
		// =====================================================================
		WEAPON_STYLES.put(11905, AttackStyle.MAGIC);  // Trident of the seas
		WEAPON_STYLES.put(12899, AttackStyle.MAGIC);  // Trident of the swamp
		WEAPON_STYLES.put(22323, AttackStyle.MAGIC);  // Sanguinesti staff
		WEAPON_STYLES.put(24422, AttackStyle.MAGIC);  // Nightmare staff
		WEAPON_STYLES.put(24424, AttackStyle.MAGIC);  // Volatile nightmare staff
		WEAPON_STYLES.put(24425, AttackStyle.MAGIC);  // Eldritch nightmare staff
		WEAPON_STYLES.put(24423, AttackStyle.MAGIC);  // Harmonised nightmare staff
		WEAPON_STYLES.put(27275, AttackStyle.MAGIC);  // Tumeken's shadow
		WEAPON_STYLES.put(21006, AttackStyle.MAGIC);  // Kodai wand
		WEAPON_STYLES.put(4675, AttackStyle.MAGIC);   // Ancient staff
		WEAPON_STYLES.put(27665, AttackStyle.MAGIC);  // Accursed sceptre
		WEAPON_STYLES.put(11791, AttackStyle.MAGIC);  // Staff of the dead
		WEAPON_STYLES.put(12904, AttackStyle.MAGIC);  // Toxic staff of the dead
		WEAPON_STYLES.put(22296, AttackStyle.MAGIC);  // Staff of light
		WEAPON_STYLES.put(6914, AttackStyle.MAGIC);   // Master wand
		WEAPON_STYLES.put(6908, AttackStyle.MAGIC);   // Beginner wand
		WEAPON_STYLES.put(6910, AttackStyle.MAGIC);   // Apprentice wand
		WEAPON_STYLES.put(6912, AttackStyle.MAGIC);   // Teacher wand
		WEAPON_STYLES.put(1381, AttackStyle.MAGIC);   // Staff of air
		WEAPON_STYLES.put(1383, AttackStyle.MAGIC);   // Staff of water
		WEAPON_STYLES.put(1385, AttackStyle.MAGIC);   // Staff of earth
		WEAPON_STYLES.put(1387, AttackStyle.MAGIC);   // Staff of fire
		WEAPON_STYLES.put(1393, AttackStyle.MAGIC);   // Battlestaff of fire
		WEAPON_STYLES.put(1395, AttackStyle.MAGIC);   // Battlestaff of water
		WEAPON_STYLES.put(1397, AttackStyle.MAGIC);   // Battlestaff of air
		WEAPON_STYLES.put(1399, AttackStyle.MAGIC);   // Battlestaff of earth
		WEAPON_STYLES.put(3053, AttackStyle.MAGIC);   // Lava battlestaff
		WEAPON_STYLES.put(3054, AttackStyle.MAGIC);   // Mystic lava staff
		WEAPON_STYLES.put(6562, AttackStyle.MAGIC);   // Mud battlestaff
		WEAPON_STYLES.put(6563, AttackStyle.MAGIC);   // Mystic mud staff
		WEAPON_STYLES.put(11998, AttackStyle.MAGIC);  // Steam battlestaff
		WEAPON_STYLES.put(12000, AttackStyle.MAGIC);  // Mystic steam staff
		WEAPON_STYLES.put(1401, AttackStyle.MAGIC);   // Mystic air staff
		WEAPON_STYLES.put(1403, AttackStyle.MAGIC);   // Mystic water staff
		WEAPON_STYLES.put(1405, AttackStyle.MAGIC);   // Mystic earth staff
		WEAPON_STYLES.put(1407, AttackStyle.MAGIC);   // Mystic fire staff
		WEAPON_STYLES.put(28583, AttackStyle.MAGIC);  // Zuriel's staff

		// =====================================================================
		// MELEE ATTACK ANIMATIONS (from pvp-performance-tracker AnimationData)
		// =====================================================================
		ANIMATION_STYLES.put(245, AttackStyle.MELEE);   // Viggora's chainmace
		ANIMATION_STYLES.put(376, AttackStyle.MELEE);   // Dagger slash
		ANIMATION_STYLES.put(381, AttackStyle.MELEE);   // Spear stab
		ANIMATION_STYLES.put(386, AttackStyle.MELEE);   // Sword stab
		ANIMATION_STYLES.put(390, AttackStyle.MELEE);   // Scimitar slash
		ANIMATION_STYLES.put(393, AttackStyle.MELEE);   // Generic slash
		ANIMATION_STYLES.put(395, AttackStyle.MELEE);   // Battleaxe slash
		ANIMATION_STYLES.put(400, AttackStyle.MELEE);   // Mace stab
		ANIMATION_STYLES.put(401, AttackStyle.MELEE);   // Battleaxe crush
		ANIMATION_STYLES.put(406, AttackStyle.MELEE);   // 2h crush
		ANIMATION_STYLES.put(407, AttackStyle.MELEE);   // 2h slash
		ANIMATION_STYLES.put(414, AttackStyle.MELEE);   // Staff crush
		ANIMATION_STYLES.put(419, AttackStyle.MELEE);   // Staff crush alt
		ANIMATION_STYLES.put(422, AttackStyle.MELEE);   // Punch
		ANIMATION_STYLES.put(423, AttackStyle.MELEE);   // Kick
		ANIMATION_STYLES.put(428, AttackStyle.MELEE);   // Staff stab
		ANIMATION_STYLES.put(429, AttackStyle.MELEE);   // Spear crush
		ANIMATION_STYLES.put(440, AttackStyle.MELEE);   // Staff slash
		ANIMATION_STYLES.put(1658, AttackStyle.MELEE);  // Abyssal whip
		ANIMATION_STYLES.put(1665, AttackStyle.MELEE);  // Granite maul
		ANIMATION_STYLES.put(2066, AttackStyle.MELEE);  // Dharok's greataxe crush
		ANIMATION_STYLES.put(2067, AttackStyle.MELEE);  // Dharok's greataxe slash
		ANIMATION_STYLES.put(2078, AttackStyle.MELEE);  // Ahrim's staff crush
		ANIMATION_STYLES.put(2661, AttackStyle.MELEE);  // Obby maul crush
		ANIMATION_STYLES.put(3297, AttackStyle.MELEE);  // Abyssal dagger stab
		ANIMATION_STYLES.put(3298, AttackStyle.MELEE);  // Abyssal bludgeon crush
		ANIMATION_STYLES.put(3852, AttackStyle.MELEE);  // Leaf-bladed battleaxe crush
		ANIMATION_STYLES.put(4503, AttackStyle.MELEE);  // Inquisitor's mace
		ANIMATION_STYLES.put(5865, AttackStyle.MELEE);  // Barrelchest anchor crush
		ANIMATION_STYLES.put(7004, AttackStyle.MELEE);  // Leaf-bladed battleaxe slash
		ANIMATION_STYLES.put(7045, AttackStyle.MELEE);  // Godsword slash
		ANIMATION_STYLES.put(7054, AttackStyle.MELEE);  // Godsword crush
		ANIMATION_STYLES.put(7516, AttackStyle.MELEE);  // Elder maul
		ANIMATION_STYLES.put(8056, AttackStyle.MELEE);  // Scythe
		ANIMATION_STYLES.put(8145, AttackStyle.MELEE);  // Ghrazi rapier stab
		ANIMATION_STYLES.put(9471, AttackStyle.MELEE);  // Osmumten's fang stab
		ANIMATION_STYLES.put(1710, AttackStyle.MELEE);  // Blue moon fend
		ANIMATION_STYLES.put(1711, AttackStyle.MELEE);  // Blue moon jab
		ANIMATION_STYLES.put(1712, AttackStyle.MELEE);  // Blue moon swipe
		ANIMATION_STYLES.put(10989, AttackStyle.MELEE); // Dual macuahuitl
		ANIMATION_STYLES.put(11124, AttackStyle.MELEE); // Elder maul alt

		// =====================================================================
		// RANGED ATTACK ANIMATIONS
		// =====================================================================
		ANIMATION_STYLES.put(426, AttackStyle.RANGED);  // Shortbow
		ANIMATION_STYLES.put(929, AttackStyle.RANGED);  // Rune knife (PvP)
		ANIMATION_STYLES.put(2075, AttackStyle.RANGED); // Karil's crossbow
		ANIMATION_STYLES.put(4230, AttackStyle.RANGED); // Crossbow (PvP)
		ANIMATION_STYLES.put(5061, AttackStyle.RANGED); // Blowpipe
		ANIMATION_STYLES.put(6600, AttackStyle.RANGED); // Darts
		ANIMATION_STYLES.put(7218, AttackStyle.RANGED); // Ballista
		ANIMATION_STYLES.put(7552, AttackStyle.RANGED); // Rune crossbow
		ANIMATION_STYLES.put(7555, AttackStyle.RANGED); // Ballista alt
		ANIMATION_STYLES.put(7617, AttackStyle.RANGED); // Rune knife
		ANIMATION_STYLES.put(8194, AttackStyle.RANGED); // Dragon knife
		ANIMATION_STYLES.put(8195, AttackStyle.RANGED); // Dragon knife poisoned
		ANIMATION_STYLES.put(9166, AttackStyle.RANGED); // Zaryte crossbow (PvP)
		ANIMATION_STYLES.put(9168, AttackStyle.RANGED); // Zaryte crossbow
		ANIMATION_STYLES.put(9858, AttackStyle.RANGED); // Venator bow
		ANIMATION_STYLES.put(11057, AttackStyle.RANGED); // Eclipse atlatl
		ANIMATION_STYLES.put(11465, AttackStyle.RANGED); // Hunter's sunlight crossbow

		// =====================================================================
		// MAGIC ATTACK ANIMATIONS
		// =====================================================================
		ANIMATION_STYLES.put(710, AttackStyle.MAGIC);   // Standard bind
		ANIMATION_STYLES.put(711, AttackStyle.MAGIC);   // Standard strike/bolt/blast
		ANIMATION_STYLES.put(811, AttackStyle.MAGIC);   // God spell
		ANIMATION_STYLES.put(1161, AttackStyle.MAGIC);  // Standard bind (staff)
		ANIMATION_STYLES.put(1162, AttackStyle.MAGIC);  // Standard strike/bolt/blast (staff)
		ANIMATION_STYLES.put(1167, AttackStyle.MAGIC);  // Standard wave (staff)
		ANIMATION_STYLES.put(1978, AttackStyle.MAGIC);  // Ancient single target
		ANIMATION_STYLES.put(1979, AttackStyle.MAGIC);  // Ancient multi target (barrage)
		ANIMATION_STYLES.put(7855, AttackStyle.MAGIC);  // Standard surge (staff)

		// =====================================================================
		// SPECIAL ATTACK ANIMATIONS
		// =====================================================================
		registerSpec(1058, AttackStyle.MELEE, "Dragon longsword");
		registerSpec(1060, AttackStyle.MELEE, "Dragon mace");
		registerSpec(1062, AttackStyle.MELEE, "Dragon dagger");
		registerSpec(1132, AttackStyle.MELEE, "Saradomin sword");
		registerSpec(1378, AttackStyle.MELEE, "Dragon warhammer");
		registerSpec(1667, AttackStyle.MELEE, "Granite maul");
		registerSpec(3300, AttackStyle.MELEE, "Abyssal dagger");
		registerSpec(7514, AttackStyle.MELEE, "Dragon claws");
		registerSpec(7515, AttackStyle.MELEE, "VLS");
		registerSpec(7638, AttackStyle.MELEE, "ZGS");
		registerSpec(7639, AttackStyle.MELEE, "ZGS (or)");
		registerSpec(7640, AttackStyle.MELEE, "SGS");
		registerSpec(7641, AttackStyle.MELEE, "SGS (or)");
		registerSpec(7642, AttackStyle.MELEE, "BGS");
		registerSpec(7643, AttackStyle.MELEE, "BGS (or)");
		registerSpec(7644, AttackStyle.MELEE, "AGS");
		registerSpec(7645, AttackStyle.MELEE, "AGS (or)");
		registerSpec(10427, AttackStyle.MELEE, "AGS (LMS)");
		registerSpec(9171, AttackStyle.MELEE, "Ancient godsword");
		registerSpec(11222, AttackStyle.MELEE, "Osmumten's fang");
		registerSpec(11140, AttackStyle.MELEE, "Burning claws");
		registerSpec(12297, AttackStyle.MELEE, "Arkan blade");

		registerSpec(1074, AttackStyle.RANGED, "Magic shortbow");
		registerSpec(7521, AttackStyle.RANGED, "Dragon thrownaxe");
		registerSpec(8292, AttackStyle.RANGED, "Dragon knife");
		registerSpec(11060, AttackStyle.RANGED, "Eclipse atlatl");

		registerSpec(8532, AttackStyle.MAGIC, "Volatile nightmare staff");
		registerSpec(11275, AttackStyle.MAGIC, "Voidwaker");  // Melee weapon but magic spec

		// Weapons that use animation ID 0 (require weapon-based detection)
		ANIM_ZERO_WEAPONS.add(11235);  // Dark bow
		ANIM_ZERO_WEAPONS.add(21902);  // Dragon crossbow (spec)
	}

	private static void registerSpec(int animId, AttackStyle style, String name)
	{
		SPEC_ANIMATIONS.add(animId);
		SPEC_NAMES.put(animId, name);
		ANIMATION_STYLES.put(animId, style);
	}

	/**
	 * Get the item ID from a raw equipment array value.
	 * Returns -1 if the slot is empty or cosmetic.
	 */
	public static int getItemId(int rawEquipmentValue)
	{
		if (rawEquipmentValue >= ITEM_OFFSET)
		{
			return rawEquipmentValue - ITEM_OFFSET;
		}
		return -1;
	}

	/**
	 * Determine attack style from weapon item ID.
	 */
	public static AttackStyle getStyleFromWeapon(int weaponItemId)
	{
		return WEAPON_STYLES.getOrDefault(weaponItemId, AttackStyle.UNKNOWN);
	}

	/**
	 * Determine attack style from animation ID.
	 * For animation 0, returns UNKNOWN (must use weapon-based fallback).
	 */
	public static AttackStyle getStyleFromAnimation(int animationId)
	{
		if (animationId <= 0)
		{
			return AttackStyle.UNKNOWN;
		}
		return ANIMATION_STYLES.getOrDefault(animationId, AttackStyle.UNKNOWN);
	}

	/**
	 * Check if an animation is a special attack.
	 */
	public static boolean isSpecAnimation(int animationId)
	{
		return SPEC_ANIMATIONS.contains(animationId);
	}

	/**
	 * Get the name of a spec weapon from its animation ID.
	 */
	public static String getSpecName(int animationId)
	{
		return SPEC_NAMES.getOrDefault(animationId, "Unknown spec");
	}

	/**
	 * Check if this weapon uses animation ID 0 and needs weapon-based detection.
	 */
	public static boolean isAnimZeroWeapon(int weaponItemId)
	{
		return ANIM_ZERO_WEAPONS.contains(weaponItemId);
	}

	/**
	 * Check if an animation is a death animation.
	 */
	public static boolean isDeathAnimation(int animationId)
	{
		return DEATH_ANIMATIONS.contains(animationId);
	}

	/**
	 * Get the correct protection prayer for a given attack style.
	 */
	public static HeadIcon getProtectionPrayer(AttackStyle style)
	{
		switch (style)
		{
			case MELEE:
				return HeadIcon.MELEE;
			case RANGED:
				return HeadIcon.RANGED;
			case MAGIC:
				return HeadIcon.MAGIC;
			default:
				return null;
		}
	}

	/**
	 * Check if the overhead prayer is the correct protection for the given attack style.
	 */
	public static boolean isPrayerCorrect(HeadIcon overhead, AttackStyle opponentStyle)
	{
		if (overhead == null || opponentStyle == AttackStyle.UNKNOWN)
		{
			return false;
		}
		HeadIcon correct = getProtectionPrayer(opponentStyle);
		return overhead == correct;
	}

	/**
	 * Count gear changes between two equipment snapshots.
	 * Only counts combat-relevant slots.
	 */
	public static int countGearSwitches(int[] prev, int[] curr)
	{
		if (prev == null || curr == null || prev.length == 0 || curr.length == 0)
		{
			return 0;
		}
		int count = 0;
		for (int slot : COMBAT_SLOTS)
		{
			if (slot < prev.length && slot < curr.length && prev[slot] != curr[slot])
			{
				count++;
			}
		}
		return count;
	}

	/**
	 * Extract the weapon item ID from an equipment array.
	 */
	public static int extractWeaponId(int[] equipment)
	{
		if (equipment == null || equipment.length <= KitType.WEAPON.getIndex())
		{
			return -1;
		}
		return getItemId(equipment[KitType.WEAPON.getIndex()]);
	}

	/**
	 * Determine the attack style a player is using based on animation, falling back to weapon.
	 * Handles animation ID 0 weapons (Dark Bow, Dragon Crossbow) specially.
	 */
	public static AttackStyle determineAttackStyle(int animationId, int weaponItemId)
	{
		AttackStyle style = getStyleFromAnimation(animationId);
		if (style != AttackStyle.UNKNOWN)
		{
			return style;
		}
		// Animation 0 or unknown animation - try weapon
		if (animationId == 0 && isAnimZeroWeapon(weaponItemId))
		{
			return getStyleFromWeapon(weaponItemId);
		}
		return getStyleFromWeapon(weaponItemId);
	}

	/**
	 * Estimate a player's HP from the health bar ratio and scale.
	 * Returns -1 if the health bar is not visible.
	 *
	 * @param healthRatio player.getHealthRatio() (0 to healthScale, or -1 if not visible)
	 * @param healthScale player.getHealthScale() (usually 30 for players)
	 * @param maxHp the player's max HP (99 for most PvP)
	 */
	public static int estimateHp(int healthRatio, int healthScale, int maxHp)
	{
		if (healthRatio < 0 || healthScale <= 1 || maxHp <= 0)
		{
			return -1;
		}
		if (healthRatio == 0)
		{
			return 0;
		}

		int divisor = healthScale - 1;
		int minHealth;
		if (healthRatio > 1)
		{
			minHealth = (maxHp * (healthRatio - 1) + divisor - 1) / divisor;
		}
		else
		{
			minHealth = 1; // ratio == 1 means alive but low
		}
		int maxHealth = (maxHp * healthRatio - 1) / divisor;

		if (maxHealth < minHealth)
		{
			maxHealth = minHealth;
		}

		return (minHealth + maxHealth + 1) / 2;
	}
}
