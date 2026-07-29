package com.wjz.mobsthinknow.ai.zombie.squad;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把首领智力转化成可见的战术复杂度。低智力首领会倾向于一拥而上；更聪明的首领才会逐步
 * 解锁单侧包抄、双侧包抄和截断退路。
 *
 * <p>开启武装小队后还会按兵种匹配职位：斧手优先顶到正面破盾施压，剑手优先去两翼，
 * 矛手凭长杆优先负责截断退路。全员空手时的分配结果与旧版完全一致。</p>
 */
public final class SquadRolePlanner {
	private SquadRolePlanner() {
	}

	public static Map<Integer, SquadRole> plan(
		final List<Integer> orderedMemberIds,
		final int leaderId,
		final int leaderIntelligence
	) {
		return plan(orderedMemberIds, leaderId, leaderIntelligence, Map.of());
	}

	public static Map<Integer, SquadRole> plan(
		final List<Integer> orderedMemberIds,
		final int leaderId,
		final int leaderIntelligence,
		final Map<Integer, WeaponClass> weapons
	) {
		Map<Integer, SquadLoadout> loadouts = new LinkedHashMap<>();
		weapons.forEach((memberId, weapon) -> loadouts.put(memberId, new SquadLoadout(weapon, false)));
		return planLoadouts(orderedMemberIds, leaderId, leaderIntelligence, loadouts);
	}

	public static Map<Integer, SquadRole> planLoadouts(
		final List<Integer> orderedMemberIds,
		final int leaderId,
		final int leaderIntelligence,
		final Map<Integer, SquadLoadout> loadouts
	) {
		Map<Integer, SquadRole> roles = new LinkedHashMap<>();
		roles.put(leaderId, SquadRole.LEADER);

		List<Integer> followers = new ArrayList<>(orderedMemberIds.size());
		for (int memberId : orderedMemberIds) {
			if (memberId == leaderId) {
				continue;
			}
			SquadLoadout loadout = loadouts.getOrDefault(memberId, SquadLoadout.UNARMED);
			if (loadout.utility() != UtilityClass.NONE) {
				// 工具兵的专职优先于普通阵型槽；若工具兵当选首领，仍保留 LEADER 身份。
				roles.put(memberId, SquadRole.SUPPORT);
			} else {
				followers.add(memberId);
			}
		}

		// 职位槽仍完全由首领智力决定；装备只影响"谁去补哪个槽"。
		boolean[] assigned = new boolean[followers.size()];
		for (int slotIndex = 0; slotIndex < followers.size(); slotIndex++) {
			SquadRole slot = roleFor(slotIndex, leaderIntelligence);
			int chosen = -1;
			int chosenScore = Integer.MIN_VALUE;
			for (int i = 0; i < followers.size(); i++) {
				if (assigned[i]) {
					continue;
				}
				int score = preference(slot, loadouts.getOrDefault(followers.get(i), SquadLoadout.UNARMED));
				// 同分时保持原有顺序（智力/血量/ID 排序），空手小队因此与旧行为完全一致。
				if (score > chosenScore) {
					chosenScore = score;
					chosen = i;
				}
			}
			assigned[chosen] = true;
			roles.put(followers.get(chosen), slot);
		}
		return roles;
	}

	/**
	 * 智力到职位槽的映射：1~3 全员正面；4~6 解锁左翼；7~8 解锁右翼；
	 * 9~10 解锁截断退路。超出基础槽位的高智力成员继续补两翼。
	 */
	private static SquadRole roleFor(final int index, final int intelligence) {
		if (intelligence <= 3 || index == 0) {
			return SquadRole.PRESSURER;
		}
		if (index == 1 && intelligence >= 4) {
			return SquadRole.FLANK_LEFT;
		}
		if (index == 2 && intelligence >= 7) {
			return SquadRole.FLANK_RIGHT;
		}
		if (index == 3 && intelligence >= 9) {
			return SquadRole.CUTOFF;
		}

		if (intelligence >= 7) {
			return (index & 1) == 0 ? SquadRole.FLANK_RIGHT : SquadRole.FLANK_LEFT;
		}
		return SquadRole.PRESSURER;
	}

	private static int preference(final SquadRole slot, final SquadLoadout loadout) {
		int base = weaponPreference(slot, loadout.weapon());
		// 盾牌属于"顶在前面挨打"的装备：优先把持盾成员派到正面施压位。
		if (loadout.shield() && slot == SquadRole.PRESSURER) {
			base += 2;
		}
		return base;
	}

	private static int weaponPreference(final SquadRole slot, final WeaponClass weapon) {
		return switch (slot) {
			case PRESSURER -> switch (weapon) {
				case AXE -> 3;
				case SPEAR -> 2;
				case NONE -> 1;
				case SWORD -> 0;
			};
			case FLANK_LEFT, FLANK_RIGHT -> switch (weapon) {
				case SWORD -> 3;
				case NONE -> 1;
				case AXE, SPEAR -> 0;
			};
			case CUTOFF -> switch (weapon) {
				case SPEAR -> 3;
				case SWORD -> 2;
				case NONE -> 1;
				case AXE -> 0;
			};
			case LEADER, SUPPORT, RANGED -> 0;
		};
	}
}
