package com.wjz.mobsthinknow.ai.zombie.squad;

import com.wjz.mobsthinknow.ai.zombie.ZombieBodyAction;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 把小队阶段、真实路线报告与个体画像映射成确定性的社交场景。
 *
 * <p>这里只处理不可变值，不持有实体或世界引用。服务端用同一份 {@link Scene} 驱动动作、声音和
 * 下一 tick 的注视目标，避免“身体在点头、头却看向另一个方向”。</p>
 */
public final class SquadSocialChoreography {
	public static final int LEADER_ACTOR_ID = -1;
	public static final int DEFAULT_BRIEFING_TICKS = 64;
	public static final int DEFAULT_REGROUP_TICKS = 48;
	private static final int BASE_BRIEFING_TICKS = 64;
	private static final int BASE_REGROUP_TICKS = 48;
	private static final int RALLY_REPEAT_TICKS = 36;
	private static final int RALLY_REPEAT_OFFSET = 20;
	private static final int MAXIMUM_STAGGERED_RESPONSES = 8;

	private SquadSocialChoreography() {
	}

	public static Scene sceneAt(
		final SquadState state,
		final long squadId,
		final long phase,
		final List<Participant> followers,
		final Timing timing
	) {
		if (phase < 0L) {
			return Scene.EMPTY;
		}
		return switch (state) {
			case FORMING -> new Scene(
				phase == 0L ? List.of(Cue.leader(ZombieBodyAction.CALL_TO_MEETING)) : List.of(),
				Attention.FOLLOW_LEADER
			);
			case RALLYING -> rallyScene(phase, followers);
			case BRIEFING -> briefingScene(squadId, phase, followers, timing.briefingTicks());
			case REORGANIZING -> reorganizationScene(squadId, phase, followers, timing.regroupTicks());
			case DEPLOYING -> deploymentScene(squadId, phase, followers);
			case ENGAGING -> Scene.EMPTY;
		};
	}

	private static Scene rallyScene(final long phase, final List<Participant> followers) {
		if (phase >= RALLY_REPEAT_OFFSET && (phase - RALLY_REPEAT_OFFSET) % RALLY_REPEAT_TICKS == 0L) {
			return new Scene(List.of(Cue.leader(ZombieBodyAction.CALL_TO_MEETING)), Attention.FOLLOW_LEADER);
		}

		Participant idleActor = followers.stream()
			.filter(participant -> participant.idleStyle() != IdleStyle.NONE)
			.filter(participant -> Math.floorMod(participant.stableKey(), 3L) == 0L)
			.filter(participant -> phase == 12L + Math.floorMod(participant.stableKey() >>> 7, 40L))
			.min(Comparator.comparingInt(Participant::entityId))
			.orElse(null);
		List<Cue> cues = idleActor == null
			? List.of()
			: List.of(Cue.follower(idleActor.entityId(), idleAction(idleActor.idleStyle())));
		return new Scene(cues, Attention.FOLLOW_LEADER);
	}

	private static Scene briefingScene(
		final long squadId,
		final long phase,
		final List<Participant> followers,
		final int duration
	) {
		Participant rotating = rotatingParticipant(squadId, 0L, followers);
		Participant left = roleOrFallback(followers, SquadRole.FLANK_LEFT, rotating);
		Participant right = roleOrFallback(followers, SquadRole.FLANK_RIGHT, rotateAfter(followers, rotating, 1));
		Participant conference = rotateAfter(followers, rotating, followers.size() > 2 ? 2 : 1);

		int surveyTick = scaled(0, duration, BASE_BRIEFING_TICKS);
		int conferenceTick = scaled(8, duration, BASE_BRIEFING_TICKS);
		int leftOrderTick = scaled(16, duration, BASE_BRIEFING_TICKS);
		int leftResponseTick = scaled(26 + responseDelay(left), duration, BASE_BRIEFING_TICKS);
		int leftCorrectionTick = scaled(33, duration, BASE_BRIEFING_TICKS);
		int rightOrderTick = scaled(38, duration, BASE_BRIEFING_TICKS);
		int rightResponseTick = scaled(48 + responseDelay(right), duration, BASE_BRIEFING_TICKS);
		int rightCorrectionTick = scaled(55, duration, BASE_BRIEFING_TICKS);
		int finalOrderTick = scaled(60, duration, BASE_BRIEFING_TICKS);

		List<Cue> cues = new ArrayList<>(1);
		if (phase == surveyTick) {
			cues.add(Cue.leader(ZombieBodyAction.SURVEY_MEMBERS));
		} else if (conference != null && phase == conferenceTick) {
			cues.add(Cue.follower(conference.entityId(), ZombieBodyAction.CONFER));
		} else if (phase == leftOrderTick) {
			cues.add(Cue.leader(orderAction(followers, SquadRole.FLANK_LEFT, ZombieBodyAction.COMMAND_LEFT)));
		} else if (left != null && phase == leftResponseTick) {
			cues.add(Cue.follower(left.entityId(), responseAction(left, false)));
		} else if (left != null && left.routeOutcome().isObjection() && phase == leftCorrectionTick) {
			cues.add(Cue.leader(correctionAction(left, ZombieBodyAction.COMMAND_LEFT)));
		} else if (phase == rightOrderTick) {
			cues.add(Cue.leader(orderAction(followers, SquadRole.FLANK_RIGHT, ZombieBodyAction.COMMAND_RIGHT)));
		} else if (right != null && phase == rightResponseTick) {
			cues.add(Cue.follower(right.entityId(), responseAction(right, true)));
		} else if (right != null && right.routeOutcome().isObjection() && phase == rightCorrectionTick) {
			cues.add(Cue.leader(correctionAction(right, ZombieBodyAction.COMMAND_RIGHT)));
		} else if (phase == finalOrderTick) {
			cues.add(Cue.leader(ZombieBodyAction.COMMAND));
		}

		Attention attention;
		if (phase < conferenceTick) {
			attention = new Attention(Focus.leader(), actorOrTarget(rotating));
		} else if (phase < leftOrderTick) {
			attention = actorAttention(conference);
		} else if (phase < leftResponseTick) {
			attention = orderAttention(left, SquadRole.FLANK_LEFT, phase - leftOrderTick, duration);
		} else if (phase < leftCorrectionTick) {
			attention = actorAttention(left);
		} else if (phase < rightOrderTick) {
			attention = left != null && left.routeOutcome().isObjection()
				? Attention.order(SquadRole.FLANK_LEFT)
				: Attention.FOLLOW_LEADER;
		} else if (phase < rightResponseTick) {
			attention = orderAttention(right, SquadRole.FLANK_RIGHT, phase - rightOrderTick, duration);
		} else if (phase < rightCorrectionTick) {
			attention = actorAttention(right);
		} else if (phase < finalOrderTick) {
			attention = right != null && right.routeOutcome().isObjection()
				? Attention.order(SquadRole.FLANK_RIGHT)
				: Attention.FOLLOW_LEADER;
		} else {
			attention = new Attention(Focus.leader(), Focus.target());
		}
		return new Scene(List.copyOf(cues), attention);
	}

	private static Scene reorganizationScene(
		final long squadId,
		final long phase,
		final List<Participant> followers,
		final int duration
	) {
		List<Cue> cues = new ArrayList<>(1);
		int responseCount = Math.min(followers.size(), MAXIMUM_STAGGERED_RESPONSES);
		int start = rotatingIndex(squadId, 3L, followers.size());
		for (int sequence = 0; sequence < responseCount; sequence++) {
			Participant participant = followers.get(Math.floorMod(start + sequence, followers.size()));
			if (phase == scaled(sequence * 2, duration, BASE_REGROUP_TICKS)) {
				cues.add(Cue.follower(participant.entityId(), ZombieBodyAction.SUCCESSION_LOOK_AROUND));
				break;
			}
		}
		if (phase == scaled(14, duration, BASE_REGROUP_TICKS)) {
			cues.add(Cue.leader(ZombieBodyAction.SUCCESSION_SALUTE));
		}
		for (int sequence = 0; sequence < responseCount; sequence++) {
			Participant participant = followers.get(Math.floorMod(start + sequence, followers.size()));
			if (phase == scaled(26 + sequence * 2, duration, BASE_REGROUP_TICKS)) {
				cues.add(Cue.follower(participant.entityId(), ZombieBodyAction.NOD));
				break;
			}
		}
		if (phase == scaled(43, duration, BASE_REGROUP_TICKS)) {
			cues.add(Cue.leader(ZombieBodyAction.COMMAND));
		}

		Attention attention = phase < scaled(14, duration, BASE_REGROUP_TICKS)
			? new Attention(Focus.target(), Focus.target())
			: Attention.FOLLOW_LEADER;
		return new Scene(List.copyOf(cues), attention);
	}

	private static Scene deploymentScene(
		final long squadId,
		final long phase,
		final List<Participant> followers
	) {
		if (phase == 0L) {
			return new Scene(
				List.of(Cue.leader(ZombieBodyAction.ADVANCE_ORDER)),
				new Attention(Focus.leader(), Focus.target())
			);
		}
		if (phase < 2L || phase % 2L != 0L) {
			return new Scene(List.of(), phase < 12L ? Attention.FOLLOW_LEADER : Attention.TARGET);
		}
		int sequence = (int)((phase - 2L) / 2L);
		if (sequence >= Math.min(followers.size(), MAXIMUM_STAGGERED_RESPONSES)) {
			return new Scene(List.of(), phase < 12L ? Attention.FOLLOW_LEADER : Attention.TARGET);
		}
		int start = rotatingIndex(squadId, 1L, followers.size());
		Participant participant = followers.get(Math.floorMod(start + sequence, followers.size()));
		return new Scene(
			List.of(Cue.follower(participant.entityId(), ZombieBodyAction.ACKNOWLEDGE)),
			Attention.FOLLOW_LEADER
		);
	}

	private static Attention orderAttention(
		final @Nullable Participant participant,
		final SquadRole role,
		final long segmentPhase,
		final int duration
	) {
		long memberLookTicks = Math.max(3L, scaled(5, duration, BASE_BRIEFING_TICKS));
		return segmentPhase < memberLookTicks && participant != null
			? new Attention(Focus.leader(), Focus.actor(participant.entityId()))
			: Attention.order(role);
	}

	private static Attention actorAttention(final @Nullable Participant participant) {
		return participant == null
			? Attention.FOLLOW_LEADER
			: new Attention(Focus.actor(participant.entityId()), Focus.actor(participant.entityId()));
	}

	private static Focus actorOrTarget(final @Nullable Participant participant) {
		return participant == null ? Focus.target() : Focus.actor(participant.entityId());
	}

	private static ZombieBodyAction responseAction(final Participant participant, final boolean finalResponse) {
		if (participant.routeOutcome().isObjection()) {
			return ZombieBodyAction.SHAKE_HEAD;
		}
		return finalResponse ? ZombieBodyAction.ACKNOWLEDGE : ZombieBodyAction.NOD;
	}

	private static ZombieBodyAction correctionAction(
		final Participant participant,
		final ZombieBodyAction directionalAction
	) {
		return participant.routeOutcome() == SquadRouteOutcome.REROUTED
			? directionalAction
			: ZombieBodyAction.COMMAND;
	}

	private static ZombieBodyAction orderAction(
		final List<Participant> followers,
		final SquadRole requestedRole,
		final ZombieBodyAction directionalAction
	) {
		return followers.stream().anyMatch(participant -> participant.role() == requestedRole)
			? directionalAction
			: ZombieBodyAction.COMMAND;
	}

	private static ZombieBodyAction idleAction(final IdleStyle style) {
		return switch (style) {
			case NONE -> ZombieBodyAction.NONE;
			case SHIELD -> ZombieBodyAction.SHIELD_TAP;
			case SWORD -> ZombieBodyAction.SWORD_INSPECT;
			case AXE -> ZombieBodyAction.AXE_SHOULDER;
			case ENGINEER -> ZombieBodyAction.ENGINEER_CHECK;
			case CONFUSED -> ZombieBodyAction.CONFUSED_TILT;
		};
	}

	/** IQ 8～10 立即回应；中低智力成员会稳定地慢 1～3 tick。 */
	static int responseDelay(final @Nullable Participant participant) {
		if (participant == null || participant.intelligence() >= 8) {
			return 0;
		}
		int base = Math.max(1, (8 - Math.max(1, participant.intelligence()) + 1) / 2);
		int jitter = Math.floorMod(participant.stableKey(), 2L) == 0L ? 0 : 1;
		return Math.min(3, base + jitter);
	}

	private static @Nullable Participant roleOrFallback(
		final List<Participant> followers,
		final SquadRole requestedRole,
		final @Nullable Participant fallback
	) {
		for (Participant participant : followers) {
			if (participant.role() == requestedRole) {
				return participant;
			}
		}
		return fallback;
	}

	private static @Nullable Participant rotateAfter(
		final List<Participant> followers,
		final @Nullable Participant participant,
		final int offset
	) {
		if (followers.isEmpty()) {
			return null;
		}
		int index = participant == null ? 0 : followers.indexOf(participant);
		return followers.get(Math.floorMod(index + offset, followers.size()));
	}

	private static @Nullable Participant rotatingParticipant(
		final long squadId,
		final long cycle,
		final List<Participant> followers
	) {
		return followers.isEmpty() ? null : followers.get(rotatingIndex(squadId, cycle, followers.size()));
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

	private static int scaled(final int baseTick, final int duration, final int baseline) {
		return Math.round(baseTick * Math.max(1, duration) / (float)baseline);
	}

	public enum IdleStyle {
		NONE,
		SHIELD,
		SWORD,
		AXE,
		ENGINEER,
		CONFUSED
	}

	public enum FocusKind {
		ACTOR,
		ROLE_DESTINATION,
		TARGET
	}

	public record Timing(int briefingTicks, int regroupTicks) {
		public static final Timing DEFAULT = new Timing(DEFAULT_BRIEFING_TICKS, DEFAULT_REGROUP_TICKS);

		public Timing {
			if (briefingTicks <= 0 || regroupTicks <= 0) {
				throw new IllegalArgumentException("Social choreography durations must be positive");
			}
		}
	}

	public record Participant(
		int entityId,
		SquadRole role,
		int intelligence,
		long stableKey,
		SquadRouteOutcome routeOutcome,
		IdleStyle idleStyle
	) {
	}

	public record Cue(int actorEntityId, ZombieBodyAction action) {
		public boolean leader() {
			return this.actorEntityId == LEADER_ACTOR_ID;
		}

		private static Cue leader(final ZombieBodyAction action) {
			return new Cue(LEADER_ACTOR_ID, action);
		}

		private static Cue follower(final int entityId, final ZombieBodyAction action) {
			return new Cue(entityId, action);
		}
	}

	public record Focus(FocusKind kind, int actorEntityId, @Nullable SquadRole role) {
		public static Focus leader() {
			return actor(LEADER_ACTOR_ID);
		}

		public static Focus actor(final int entityId) {
			return new Focus(FocusKind.ACTOR, entityId, null);
		}

		public static Focus roleDestination(final SquadRole role) {
			return new Focus(FocusKind.ROLE_DESTINATION, 0, role);
		}

		public static Focus target() {
			return new Focus(FocusKind.TARGET, 0, null);
		}
	}

	public record Attention(Focus audienceFocus, Focus leaderFocus) {
		public static final Attention FOLLOW_LEADER = new Attention(Focus.leader(), Focus.target());
		public static final Attention TARGET = new Attention(Focus.target(), Focus.target());

		public static Attention order(final SquadRole role) {
			return new Attention(Focus.leader(), Focus.roleDestination(role));
		}
	}

	public record Scene(List<Cue> cues, Attention attention) {
		public static final Scene EMPTY = new Scene(List.of(), Attention.TARGET);
	}
}
