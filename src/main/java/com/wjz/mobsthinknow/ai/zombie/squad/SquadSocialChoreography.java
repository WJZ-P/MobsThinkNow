package com.wjz.mobsthinknow.ai.zombie.squad;

import com.wjz.mobsthinknow.ai.zombie.ZombieBodyAction;
import java.util.List;

/**
 * 把小队阶段与相位映射成确定性的社交动作节拍。
 *
 * <p>这里只处理整数、职位与动作编号，不持有实体或世界引用。相同小队在相同阶段总会得到相同编排，
 * 多个小队则通过 {@code squadId} 错开提问成员，既有变化又不会每次重载后突然换人。</p>
 */
public final class SquadSocialChoreography {
	static final int BRIEFING_CYCLE_TICKS = 24;
	private static final int RALLY_REPEAT_TICKS = 36;
	private static final int RALLY_REPEAT_OFFSET = 20;
	private static final int MAXIMUM_DEPLOYMENT_RESPONSES = 8;

	private SquadSocialChoreography() {
	}

	public static List<Cue> cuesAt(
		final SquadState state,
		final long squadId,
		final long phase,
		final List<SquadRole> followerRoles
	) {
		if (phase < 0L) {
			return List.of();
		}
		return switch (state) {
			case FORMING -> phase == 0L
				? List.of(Cue.leader(ZombieBodyAction.CALL_TO_MEETING))
				: List.of();
			case RALLYING -> phase >= RALLY_REPEAT_OFFSET
				&& (phase - RALLY_REPEAT_OFFSET) % RALLY_REPEAT_TICKS == 0L
				? List.of(Cue.leader(ZombieBodyAction.CALL_TO_MEETING))
				: List.of();
			case BRIEFING -> briefingCues(squadId, phase, followerRoles);
			case REORGANIZING -> reorganizationCues(squadId, phase, followerRoles);
			case DEPLOYING -> deploymentCues(squadId, phase, followerRoles);
			case ENGAGING -> List.of();
		};
	}

	private static List<Cue> briefingCues(
		final long squadId,
		final long phase,
		final List<SquadRole> followerRoles
	) {
		int localTick = (int)(phase % BRIEFING_CYCLE_TICKS);
		long cycle = phase / BRIEFING_CYCLE_TICKS;
		int rotating = rotatingIndex(squadId, cycle, followerRoles.size());
		return switch (localTick) {
			case 0 -> List.of(Cue.leader(ZombieBodyAction.SURVEY_MEMBERS));
			case 3 -> followerCue(followerRoles, rotating, ZombieBodyAction.CONFER);
			case 6 -> List.of(Cue.leader(orderFor(followerRoles, SquadRole.FLANK_LEFT, ZombieBodyAction.COMMAND_LEFT)));
			case 10 -> followerCue(
				followerRoles,
				roleOrFallback(followerRoles, SquadRole.FLANK_LEFT, rotating),
				ZombieBodyAction.NOD
			);
			case 13 -> followerCue(
				followerRoles,
				rotating + (followerRoles.size() > 1 ? 1 : 0),
				ZombieBodyAction.SHAKE_HEAD
			);
			case 16 -> List.of(Cue.leader(orderFor(followerRoles, SquadRole.FLANK_RIGHT, ZombieBodyAction.COMMAND_RIGHT)));
			case 20 -> followerCue(
				followerRoles,
				roleOrFallback(followerRoles, SquadRole.FLANK_RIGHT, rotating + 2),
				ZombieBodyAction.ACKNOWLEDGE
			);
			default -> List.of();
		};
	}

	private static List<Cue> reorganizationCues(
		final long squadId,
		final long phase,
		final List<SquadRole> followerRoles
	) {
		return switch ((int)phase) {
			case 0 -> List.of(Cue.leader(ZombieBodyAction.SURVEY_MEMBERS));
			case 5 -> List.of(Cue.leader(ZombieBodyAction.COMMAND));
			case 10 -> followerCue(
				followerRoles,
				rotatingIndex(squadId, 0L, followerRoles.size()),
				ZombieBodyAction.NOD
			);
			default -> List.of();
		};
	}

	private static List<Cue> deploymentCues(
		final long squadId,
		final long phase,
		final List<SquadRole> followerRoles
	) {
		if (phase == 0L) {
			return List.of(Cue.leader(ZombieBodyAction.ADVANCE_ORDER));
		}
		if (phase < 2L || phase % 2L != 0L) {
			return List.of();
		}
		int sequence = (int)((phase - 2L) / 2L);
		if (sequence >= Math.min(followerRoles.size(), MAXIMUM_DEPLOYMENT_RESPONSES)) {
			return List.of();
		}
		int start = rotatingIndex(squadId, 1L, followerRoles.size());
		return followerCue(followerRoles, start + sequence, ZombieBodyAction.ACKNOWLEDGE);
	}

	private static ZombieBodyAction orderFor(
		final List<SquadRole> roles,
		final SquadRole requestedRole,
		final ZombieBodyAction directionalAction
	) {
		return roles.contains(requestedRole) ? directionalAction : ZombieBodyAction.COMMAND;
	}

	private static int roleOrFallback(
		final List<SquadRole> roles,
		final SquadRole requestedRole,
		final int fallback
	) {
		int index = roles.indexOf(requestedRole);
		return index >= 0 ? index : fallback;
	}

	private static List<Cue> followerCue(
		final List<SquadRole> roles,
		final int requestedIndex,
		final ZombieBodyAction action
	) {
		if (roles.isEmpty()) {
			return List.of();
		}
		return List.of(Cue.follower(Math.floorMod(requestedIndex, roles.size()), action));
	}

	private static int rotatingIndex(final long squadId, final long cycle, final int size) {
		if (size <= 0) {
			return 0;
		}
		long mixed = squadId ^ (cycle * 0x9E3779B97F4A7C15L);
		mixed ^= mixed >>> 33;
		mixed *= 0xFF51AFD7ED558CCDL;
		mixed ^= mixed >>> 33;
		return Math.floorMod(Long.hashCode(mixed), size);
	}

	public record Cue(boolean leader, int followerIndex, ZombieBodyAction action) {
		private static Cue leader(final ZombieBodyAction action) {
			return new Cue(true, -1, action);
		}

		private static Cue follower(final int followerIndex, final ZombieBodyAction action) {
			return new Cue(false, followerIndex, action);
		}
	}
}
