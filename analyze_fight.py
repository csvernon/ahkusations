import json
import statistics
from collections import defaultdict, Counter

with open(r"C:\Users\cvern\.runelite\ahkusations\raw\1773378270960_sallnoobs.json", "r") as f:
    data = json.load(f)

snapshots = data["snapshots"]
print(f"Fight: local vs {data['opponentName']} | {data['durationTicks']} ticks ({data['durationSeconds']}s)")
print(f"Ticks {data['startTick']} - {data['endTick']}")
print(f"Total snapshots: {len(snapshots)}")
print()

# ============================================================
# We analyze the OPPONENT (sallnoobs) as the suspected cheater
# ============================================================

WEAPON_STYLES = {
    26374: "RANGED",   # ZCB
    12904: "MAGIC",    # Toxic staff
    21006: "MAGIC",    # Kodai wand
    4151: "MELEE",     # Abyssal whip
    11802: "MELEE",    # AGS
    13652: "MELEE",    # Dragon claws
    24225: "MAGIC",    # Volatile nightmare staff
}

EQUIPMENT_SLOTS = ["HEAD", "CAPE", "AMULET", "BODY", "LEGS", "SHIELD", "UNUSED1", "BOOTS", "UNUSED2", "GLOVES", "RING", "AMMO"]

# Known attack animations (common PvP ones)
ATTACK_ANIMS = {
    1658, 1667, 7855, 7045, 7514, 7515, 7516, 7638, 7642, 7643, 7644,
    1979, 393, 401, 400, 406, 407, 414, 419, 420, 428, 429, 430, 440,
    1062, 1067, 1074, 1658, 1665, 1667, 2323, 2890, 3294, 3297, 3298,
    3299, 3300, 4230, 5061, 5439, 6103, 6696, 7004, 7045, 7054, 7055,
    7511, 7512, 7513, 7514, 7515, 7516, 7644, 7855, 8145, 8056,
    390, 386, 381, 376, 422, 423, 424, 426, 9168, 9171, 1203, 1378,
    7552, 7554, 7555, 7556, 7514, 1658, 2075, 2076, 2077, 2078,
    7618, # ZCB
    1979, # Toxic staff autocast
    1162, # whip
    7644, # AGS spec
    7514, # dragon claws spec
    393, 390, # staff bash/autocast
    7855, # volatile spec
    8056, # zaryte crossbow
}

# ============================================================
# 1. GEAR SWITCHES
# ============================================================
print("=" * 60)
print("1. GEAR SWITCHES (Opponent Equipment)")
print("=" * 60)

switch_counts = []
for i in range(1, len(snapshots)):
    prev_eq = snapshots[i-1]["opponentEquipment"]
    curr_eq = snapshots[i]["opponentEquipment"]
    changes = sum(1 for a, b in zip(prev_eq, curr_eq) if a != b)
    if changes > 0:
        switch_counts.append((snapshots[i]["tick"], changes))

all_change_vals = [c for _, c in switch_counts]
print(f"Ticks with gear changes: {len(switch_counts)} / {len(snapshots)-1}")
if all_change_vals:
    print(f"Max switches in a single tick: {max(all_change_vals)}")
    print(f"Average switches per tick (when switching): {statistics.mean(all_change_vals):.2f}")
    print(f"Variance of switch counts: {statistics.variance(all_change_vals):.2f}")
    print(f"Stdev of switch counts: {statistics.stdev(all_change_vals):.2f}")

    # Distribution
    dist = Counter(all_change_vals)
    print(f"\nSwitch count distribution:")
    for k in sorted(dist.keys()):
        print(f"  {k} slots changed: {dist[k]} times")

    # Show top-5 biggest switches
    top = sorted(switch_counts, key=lambda x: -x[1])[:10]
    print(f"\nTop 10 biggest switch ticks:")
    for tick, cnt in top:
        print(f"  Tick {tick}: {cnt} slots changed")

print()

# ============================================================
# 2. LOADOUT PERFECTION
# ============================================================
print("=" * 60)
print("2. LOADOUT PERFECTION (by Opponent Weapon)")
print("=" * 60)

weapon_loadouts = defaultdict(set)
weapon_tick_counts = Counter()
for s in snapshots:
    wep = s["opponentWeaponId"]
    eq_tuple = tuple(s["opponentEquipment"])
    weapon_loadouts[wep].add(eq_tuple)
    weapon_tick_counts[wep] += 1

for wep in sorted(weapon_loadouts.keys()):
    style = WEAPON_STYLES.get(wep, "UNKNOWN")
    n_loadouts = len(weapon_loadouts[wep])
    n_ticks = weapon_tick_counts[wep]
    perfect = "PERFECT (always same gear)" if n_loadouts == 1 else ""
    print(f"  Weapon {wep} ({style}): {n_loadouts} unique loadout(s) across {n_ticks} ticks {perfect}")

print()

# ============================================================
# 3. ATTACK-TICK STYLE SWITCHING
# ============================================================
print("=" * 60)
print("3. ATTACK-TICK STYLE SWITCHING (Opponent)")
print("=" * 60)

attack_ticks = []
style_switch_on_attack = 0
weapon_switch_on_attack = 0

for i in range(1, len(snapshots)):
    s = snapshots[i]
    prev = snapshots[i-1]
    anim = s["opponentAnimation"]
    if anim > 0 and anim in ATTACK_ANIMS:
        attack_ticks.append(i)
        prev_wep = prev["opponentWeaponId"]
        curr_wep = s["opponentWeaponId"]
        prev_style = WEAPON_STYLES.get(prev_wep, "UNKNOWN")
        curr_style = WEAPON_STYLES.get(curr_wep, "UNKNOWN")

        if curr_wep != prev_wep:
            weapon_switch_on_attack += 1
            if curr_style != prev_style:
                style_switch_on_attack += 1

total_attack_ticks = len(attack_ticks)
print(f"Total attack ticks (opponent): {total_attack_ticks}")
print(f"Weapon switched on attack tick: {weapon_switch_on_attack}")
print(f"Style switched on attack tick: {style_switch_on_attack}")
if total_attack_ticks > 0:
    print(f"Ratio weapon-switch-on-attack: {weapon_switch_on_attack}/{total_attack_ticks} = {weapon_switch_on_attack/total_attack_ticks:.3f}")
    print(f"Ratio style-switch-on-attack: {style_switch_on_attack}/{total_attack_ticks} = {style_switch_on_attack/total_attack_ticks:.3f}")

# Also show all attack animations seen
anim_counter = Counter()
for i in attack_ticks:
    anim_counter[snapshots[i]["opponentAnimation"]] += 1
print(f"\nAttack animations seen (opponent):")
for anim, cnt in anim_counter.most_common():
    print(f"  Anim {anim}: {cnt} times")

print()

# ============================================================
# 4. ATTACK INTERVAL REGULARITY
# ============================================================
print("=" * 60)
print("4. ATTACK INTERVAL REGULARITY (Opponent)")
print("=" * 60)

attack_tick_numbers = [snapshots[i]["tick"] for i in attack_ticks]
intervals = [attack_tick_numbers[i+1] - attack_tick_numbers[i] for i in range(len(attack_tick_numbers)-1)]

if len(intervals) >= 2:
    print(f"Attack ticks: {attack_tick_numbers}")
    print(f"Intervals: {intervals}")
    print(f"Mean interval: {statistics.mean(intervals):.2f}")
    print(f"Variance: {statistics.variance(intervals):.2f}")
    print(f"Stdev: {statistics.stdev(intervals):.2f}")
    print(f"Min interval: {min(intervals)}")
    print(f"Max interval: {max(intervals)}")

    interval_dist = Counter(intervals)
    print(f"\nInterval distribution:")
    for k in sorted(interval_dist.keys()):
        print(f"  {k} ticks apart: {interval_dist[k]} times")
elif len(intervals) == 1:
    print(f"Only one interval: {intervals[0]}")
else:
    print("Not enough attack ticks to compute intervals")

print()

# ============================================================
# 5. EAT PATTERNS (Opponent HP)
# ============================================================
print("=" * 60)
print("5. EAT PATTERNS (Opponent HP)")
print("=" * 60)

eats = []
for i in range(1, len(snapshots)):
    prev_hp = snapshots[i-1]["opponentEstimatedHp"]
    curr_hp = snapshots[i]["opponentEstimatedHp"]
    if prev_hp > 0 and curr_hp > 0 and curr_hp > prev_hp:
        heal_amount = curr_hp - prev_hp
        eats.append({
            "tick": snapshots[i]["tick"],
            "hp_before": prev_hp,
            "hp_after": curr_hp,
            "heal": heal_amount
        })

print(f"Total eat events detected: {len(eats)}")
if eats:
    # Bucket by HP before eating (ranges of 5)
    buckets = defaultdict(list)
    for e in eats:
        bucket = (e["hp_before"] // 5) * 5
        buckets[bucket].append(e)

    print(f"\nEat HP threshold buckets (HP before eat, buckets of 5):")
    for bucket in sorted(buckets.keys()):
        count = len(buckets[bucket])
        pct = count / len(eats) * 100
        flag = " *** 60%+ ***" if pct >= 60 else ""
        print(f"  HP {bucket}-{bucket+4}: {count} eats ({pct:.1f}%){flag}")
        for e in buckets[bucket]:
            print(f"    Tick {e['tick']}: {e['hp_before']} -> {e['hp_after']} (+{e['heal']})")

    # Combo eats (heal 30+ in one tick)
    combo_eats = [e for e in eats if e["heal"] >= 30]
    print(f"\nCombo eats (heal >= 30 in one tick): {len(combo_eats)}")
    for e in combo_eats:
        print(f"  Tick {e['tick']}: {e['hp_before']} -> {e['hp_after']} (+{e['heal']})")

    # All eats listed
    print(f"\nAll eats:")
    for e in eats:
        print(f"  Tick {e['tick']}: HP {e['hp_before']} -> {e['hp_after']} (+{e['heal']})")

print()

# ============================================================
# 6. OFF-PRAYER ATTACKS (Opponent attacking into local prayer)
# ============================================================
print("=" * 60)
print("6. OFF-PRAYER ATTACKS (Opponent attacks vs Local prayer)")
print("=" * 60)

off_prayer = 0
on_prayer = 0
attack_details = []

for i in attack_ticks:
    s = snapshots[i]
    opp_wep = s["opponentWeaponId"]
    opp_style = WEAPON_STYLES.get(opp_wep, "UNKNOWN")
    local_prayer = s["localOverhead"]  # what prayer local player has up

    if opp_style == "UNKNOWN":
        continue

    if local_prayer is None or local_prayer == "NONE" or local_prayer == "null":
        off_prayer += 1
        attack_details.append((s["tick"], opp_wep, opp_style, local_prayer, "NO PRAYER"))
    elif local_prayer != opp_style:
        off_prayer += 1
        attack_details.append((s["tick"], opp_wep, opp_style, local_prayer, "OFF-PRAYER"))
    else:
        on_prayer += 1
        attack_details.append((s["tick"], opp_wep, opp_style, local_prayer, "ON-PRAYER (blocked)"))

total_counted = off_prayer + on_prayer
print(f"Opponent attacks into correct local prayer (blocked): {on_prayer}")
print(f"Opponent attacks into wrong/no local prayer (off-prayer): {off_prayer}")
if total_counted > 0:
    print(f"Off-prayer ratio: {off_prayer}/{total_counted} = {off_prayer/total_counted:.3f}")

print(f"\nAttack details:")
for tick, wep, style, prayer, result in attack_details:
    print(f"  Tick {tick}: wep={wep}({style}) vs prayer={prayer} -> {result}")

print()

# ============================================================
# 7. PRAYER REACTION (Local prayer switching to match opponent weapon)
# ============================================================
print("=" * 60)
print("7. PRAYER REACTION (Local prayer vs Opponent weapon style)")
print("=" * 60)

# When opponent switches weapon style, does local player's prayer match on next tick?
style_switch_events = 0
prayer_matched_next_tick = 0
prayer_reaction_details = []

for i in range(1, len(snapshots) - 1):
    prev = snapshots[i-1]
    curr = snapshots[i]
    nxt = snapshots[i+1]

    prev_wep = prev["opponentWeaponId"]
    curr_wep = curr["opponentWeaponId"]
    prev_style = WEAPON_STYLES.get(prev_wep, "UNKNOWN")
    curr_style = WEAPON_STYLES.get(curr_wep, "UNKNOWN")

    if prev_style != curr_style and curr_style != "UNKNOWN" and prev_style != "UNKNOWN":
        style_switch_events += 1
        next_prayer = nxt["localOverhead"]
        matched = (next_prayer == curr_style)
        if matched:
            prayer_matched_next_tick += 1
        prayer_reaction_details.append({
            "tick": curr["tick"],
            "from_style": prev_style,
            "to_style": curr_style,
            "next_tick_prayer": next_prayer,
            "matched": matched
        })

print(f"Opponent style switch events: {style_switch_events}")
print(f"Local prayer matched on next tick (1-tick reaction): {prayer_matched_next_tick}")
if style_switch_events > 0:
    print(f"1-tick prayer reaction ratio: {prayer_matched_next_tick}/{style_switch_events} = {prayer_matched_next_tick/style_switch_events:.3f}")

print(f"\nPrayer reaction details:")
for d in prayer_reaction_details:
    m = "MATCHED" if d["matched"] else "NOT matched"
    print(f"  Tick {d['tick']}: opp switched {d['from_style']} -> {d['to_style']}, next tick prayer={d['next_tick_prayer']} -> {m}")

print()

# ============================================================
# SUMMARY
# ============================================================
print("=" * 60)
print("SUMMARY")
print("=" * 60)
if all_change_vals:
    print(f"Gear switch max: {max(all_change_vals)} | avg: {statistics.mean(all_change_vals):.2f} | var: {statistics.variance(all_change_vals):.2f}")
print(f"Loadout perfection: {sum(1 for w in weapon_loadouts.values() if len(w)==1)}/{len(weapon_loadouts)} weapons always same gear")
if total_attack_ticks > 0:
    print(f"Style-switch-on-attack ratio: {style_switch_on_attack}/{total_attack_ticks} = {style_switch_on_attack/total_attack_ticks:.3f}")
if len(intervals) >= 2:
    print(f"Attack interval variance: {statistics.variance(intervals):.2f}")
if eats:
    max_bucket_pct = max(len(v)/len(eats)*100 for v in buckets.values())
    print(f"Most concentrated eat bucket: {max_bucket_pct:.1f}%")
    print(f"Combo eats: {len(combo_eats)}")
if total_counted > 0:
    print(f"Off-prayer attack ratio: {off_prayer}/{total_counted} = {off_prayer/total_counted:.3f}")
if style_switch_events > 0:
    print(f"1-tick prayer reaction ratio: {prayer_matched_next_tick}/{style_switch_events} = {prayer_matched_next_tick/style_switch_events:.3f}")
