package com.wjz.mobsthinknow.ai.giant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 不接触世界的巨人近战动作选择器。
 *
 * <p>高智力巨人大概率使用当前局面得分最高的动作；低智力个体仍按权重保留失误和变化。
 * 选择器只接收“哪只手可用”，因此手中载荷、射手登乘和投掷恢复都由调用方统一折算，
 * 不会出现格斗模块偷偷抢占一只忙碌手臂的情况。</p>
 */
public final class GiantMeleePlanner {
	public static final double MAXIMUM_ACTION_DISTANCE = 7.25;

	private GiantMeleePlanner() {
	}

	public static GiantMeleeAction choose(
		final Context context,
		final double tacticalRoll,
		final double weightedRoll
	) {
		if (context.horizontalDistance() < 0.0
			|| context.horizontalDistance() > MAXIMUM_ACTION_DISTANCE
			|| context.nearbyEnemies() <= 0) {
			return GiantMeleeAction.NONE;
		}

		List<Candidate> candidates = candidates(context, weightedRoll);
		if (candidates.isEmpty()) {
			return GiantMeleeAction.NONE;
		}

		/*
		 * IQ 10 必定选择局面最优解；IQ 1 仍有 28% 的理性选择概率。其余时候走权重随机，
		 * 让同一批巨人不会像同步播放脚本一样整齐地使用完全相同的动作。
		 */
		double tacticalChance = Math.min(1.0, 0.20 + GiantIntelligence.clamp(context.intelligence()) * 0.08);
		if (clampUnit(tacticalRoll) < tacticalChance) {
			return candidates.stream()
				.max(Comparator.comparingDouble(Candidate::score))
				.orElseThrow()
				.action();
		}

		double total = candidates.stream().mapToDouble(Candidate::score).sum();
		double cursor = clampUnit(weightedRoll) * total;
		for (Candidate candidate : candidates) {
			cursor -= candidate.score();
			if (cursor <= 0.0) {
				return candidate.action();
			}
		}
		return candidates.getLast().action();
	}

	private static List<Candidate> candidates(final Context context, final double handRoll) {
		List<Candidate> result = new ArrayList<>(7);
		double distance = context.horizontalDistance();
		int enemies = context.nearbyEnemies();
		int intelligence = GiantIntelligence.clamp(context.intelligence());
		GiantMeleeAction previous = context.previousAction();

		if (context.rightHandAvailable()
			&& context.leftHandAvailable()
			&& distance <= 6.25
			&& enemies >= 2) {
			double score = 20.0 + enemies * 18.0 + intelligence * 2.0;
			add(result, GiantMeleeAction.GROUND_SMASH, score, previous);
		}

		if (distance <= 4.20) {
			GiantMeleeAction stomp = chooseFoot(previous, handRoll);
			double occupiedHandsBonus = context.rightHandAvailable() || context.leftHandAvailable() ? 0.0 : 24.0;
			double score = 38.0 + (4.20 - distance) * 12.0 + occupiedHandsBonus;
			add(result, stomp, score, previous);
		}

		if (distance <= 4.80 && (context.targetDefending() || distance <= 2.60)) {
			GiantMeleeAction kick = chooseKick(previous, handRoll);
			double defenseBonus = context.targetDefending() ? 92.0 : 0.0;
			double score = 54.0 + defenseBonus + intelligence * 1.5 + Math.max(0.0, 2.60 - distance) * 14.0;
			add(result, kick, score, previous);
		}

		GiantHand hand = chooseAvailableHand(context, handRoll);
		if (hand != null
			&& context.targetGrabbable()
			&& enemies <= 2
			&& intelligence >= 7
			&& distance <= 4.65) {
			GiantMeleeAction grab = hand == GiantHand.RIGHT
				? GiantMeleeAction.GRAB_RIGHT
				: GiantMeleeAction.GRAB_LEFT;
			double isolationBonus = enemies == 1 ? 18.0 : 0.0;
			double crowdPenalty = enemies > 1 ? 30.0 : 0.0;
			double comboBonus = previous.family() == GiantMeleeAction.Family.SLAP ? 24.0 : 0.0;
			double score = 72.0 + intelligence * 2.0 + (4.65 - distance) * 4.0
				+ isolationBonus + comboBonus - crowdPenalty;
			add(result, grab, score, previous);
		}
		if (hand != null && distance <= MAXIMUM_ACTION_DISTANCE) {
			GiantMeleeAction sweep = hand == GiantHand.RIGHT
				? GiantMeleeAction.SWEEP_RIGHT
				: GiantMeleeAction.SWEEP_LEFT;
			double score = 24.0 + enemies * 11.0 + (distance > 4.80 ? 12.0 : 0.0);
			add(result, sweep, score, previous);
		}
		if (hand != null && distance <= 5.40) {
			GiantMeleeAction slap = hand == GiantHand.RIGHT
				? GiantMeleeAction.SLAP_RIGHT
				: GiantMeleeAction.SLAP_LEFT;
			double score = 52.0 + (enemies == 1 ? 26.0 : 0.0) + (5.40 - distance) * 4.0;
			add(result, slap, score, previous);
		}
		return result;
	}

	private static void add(
		final List<Candidate> candidates,
		final GiantMeleeAction action,
		final double rawScore,
		final GiantMeleeAction previous
	) {
		double repetitionPenalty = action.family() == previous.family() ? 0.25 : 1.0;
		candidates.add(new Candidate(action, Math.max(1.0, rawScore * repetitionPenalty)));
	}

	private static GiantMeleeAction chooseFoot(final GiantMeleeAction previous, final double roll) {
		if (previous == GiantMeleeAction.STOMP_RIGHT) {
			return GiantMeleeAction.STOMP_LEFT;
		}
		if (previous == GiantMeleeAction.STOMP_LEFT) {
			return GiantMeleeAction.STOMP_RIGHT;
		}
		return clampUnit(roll) < 0.5 ? GiantMeleeAction.STOMP_RIGHT : GiantMeleeAction.STOMP_LEFT;
	}

	private static GiantMeleeAction chooseKick(final GiantMeleeAction previous, final double roll) {
		if (previous == GiantMeleeAction.KICK_RIGHT) {
			return GiantMeleeAction.KICK_LEFT;
		}
		if (previous == GiantMeleeAction.KICK_LEFT) {
			return GiantMeleeAction.KICK_RIGHT;
		}
		return clampUnit(roll) < 0.5 ? GiantMeleeAction.KICK_RIGHT : GiantMeleeAction.KICK_LEFT;
	}

	private static GiantHand chooseAvailableHand(final Context context, final double roll) {
		if (context.rightHandAvailable() && !context.leftHandAvailable()) {
			return GiantHand.RIGHT;
		}
		if (context.leftHandAvailable() && !context.rightHandAvailable()) {
			return GiantHand.LEFT;
		}
		if (!context.rightHandAvailable()) {
			return null;
		}
		GiantHand previousHand = context.previousAction().actionHand();
		if (previousHand != null) {
			return previousHand == GiantHand.RIGHT ? GiantHand.LEFT : GiantHand.RIGHT;
		}
		return clampUnit(roll) < 0.5 ? GiantHand.RIGHT : GiantHand.LEFT;
	}

	private static double clampUnit(final double value) {
		if (!Double.isFinite(value)) {
			return 0.0;
		}
		return Math.max(0.0, Math.min(Math.nextDown(1.0), value));
	}

	public record Context(
		double horizontalDistance,
		int nearbyEnemies,
		boolean rightHandAvailable,
		boolean leftHandAvailable,
		int intelligence,
		boolean targetDefending,
		boolean targetGrabbable,
		GiantMeleeAction previousAction
	) {
		public Context(
			final double horizontalDistance,
			final int nearbyEnemies,
			final boolean rightHandAvailable,
			final boolean leftHandAvailable,
			final int intelligence,
			final GiantMeleeAction previousAction
		) {
			this(
				horizontalDistance,
				nearbyEnemies,
				rightHandAvailable,
				leftHandAvailable,
				intelligence,
				false,
				false,
				previousAction
			);
		}

		public Context {
			if (previousAction == null) {
				previousAction = GiantMeleeAction.NONE;
			}
		}
	}

	private record Candidate(GiantMeleeAction action, double score) {
	}
}
