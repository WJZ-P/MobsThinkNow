package com.wjz.mobsthinknow.ai.zombie.squad;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 把“原阵位 + 少量替代阵位 + 原版寻路结果”归并为会议可以表达的路线报告。
 *
 * <p>候选生成和 {@code Navigation#createPath} 仍由服务器主线程协调器负责；这里是可独立测试的
 * 常数时间选择层。候选顺序就是首领的偏好顺序，找到第一条可达路线便停止。</p>
 */
public final class SquadBriefingRoutePlanner {
	private SquadBriefingRoutePlanner() {
	}

	public static Result resolve(
		final SquadRole requestedRole,
		final @Nullable Vec3 requestedDestination,
		final List<Candidate> fallbacks,
		final Predicate<Vec3> canReach
	) {
		Objects.requireNonNull(requestedRole, "requestedRole");
		Objects.requireNonNull(fallbacks, "fallbacks");
		Objects.requireNonNull(canReach, "canReach");

		int checks = 0;
		if (requestedDestination != null) {
			checks++;
			if (canReach.test(requestedDestination)) {
				return new Result(
					requestedRole,
					requestedRole,
					requestedDestination,
					requestedDestination,
					SquadRouteOutcome.CLEAR,
					checks
				);
			}
		}

		for (Candidate candidate : fallbacks) {
			if (requestedDestination != null && candidate.destination().distanceToSqr(requestedDestination) < 1.0E-6) {
				continue;
			}
			checks++;
			if (canReach.test(candidate.destination())) {
				return new Result(
					requestedRole,
					candidate.role(),
					requestedDestination,
					candidate.destination(),
					SquadRouteOutcome.REROUTED,
					checks
				);
			}
		}

		return new Result(
			requestedRole,
			SquadRole.PRESSURER,
			requestedDestination,
			null,
			SquadRouteOutcome.BLOCKED,
			checks
		);
	}

	public record Candidate(SquadRole role, Vec3 destination) {
		public Candidate {
			Objects.requireNonNull(role, "role");
			Objects.requireNonNull(destination, "destination");
		}
	}

	public record Result(
		SquadRole requestedRole,
		SquadRole assignedRole,
		@Nullable Vec3 requestedDestination,
		@Nullable Vec3 resolvedDestination,
		SquadRouteOutcome outcome,
		int pathChecks
	) {
		public Result {
			Objects.requireNonNull(requestedRole, "requestedRole");
			Objects.requireNonNull(assignedRole, "assignedRole");
			Objects.requireNonNull(outcome, "outcome");
			if (pathChecks < 0) {
				throw new IllegalArgumentException("pathChecks must be non-negative");
			}
		}
	}
}
