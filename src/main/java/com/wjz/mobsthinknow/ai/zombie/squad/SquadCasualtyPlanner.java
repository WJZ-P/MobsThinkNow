package com.wjz.mobsthinknow.ai.zombie.squad;

import java.util.Comparator;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 混编小队伤员撤离的纯快照规划器。
 *
 * <p>每轮只选生命比例最低的一名伤员和一名护卫，避免多人同时后撤。盾兵优先；没有盾兵时，能够
 * 载人的高智力蜘蛛优先于普通步行护卫，其次才比较距离、生命和智力。输出的护卫点始终位于威胁与
 * 伤员之间。</p>
 */
public final class SquadCasualtyPlanner {
	public static final double DEFAULT_MAXIMUM_ACTIVATION_DISTANCE = 12.0;
	public static final double SAFE_DISTANCE = 10.0;
	private static final double EVACUATION_DISTANCE = 4.2;
	private static final double SCREEN_DISTANCE = 1.55;
	private static final double LATERAL_OFFSET = 0.65;
	private static final double MAXIMUM_ESCORT_DISTANCE_SQUARED = 12.0 * 12.0;

	private SquadCasualtyPlanner() {
	}

	public static @Nullable Response select(
		final List<MemberSnapshot> members,
		final Vec3 threatPosition,
		final double healthThreshold
	) {
		double clampedThreshold = Math.max(0.05, Math.min(0.75, healthThreshold));
		MemberSnapshot casualty = members.stream()
			.filter(MemberSnapshot::casualtyEligible)
			.filter(member -> member.healthFraction() > 0.0 && member.healthFraction() <= clampedThreshold)
			.filter(member -> horizontalDistanceSquared(member.position(), threatPosition)
				<= DEFAULT_MAXIMUM_ACTIVATION_DISTANCE * DEFAULT_MAXIMUM_ACTIVATION_DISTANCE)
			.min(Comparator.comparingDouble(MemberSnapshot::healthFraction)
				.thenComparingDouble(member -> horizontalDistanceSquared(member.position(), threatPosition))
				.thenComparingInt(MemberSnapshot::entityId))
			.orElse(null);
		if (casualty == null) {
			return null;
		}

		MemberSnapshot escort = members.stream()
			.filter(member -> member.entityId() != casualty.entityId())
			.filter(MemberSnapshot::escortEligible)
			.filter(member -> member.healthFraction() >= 0.55)
			.filter(member -> member.position().distanceToSqr(casualty.position()) <= MAXIMUM_ESCORT_DISTANCE_SQUARED)
			.min(Comparator.comparing((MemberSnapshot member) -> !member.hasShield())
				.thenComparing(member -> !member.mobileCarrier())
				.thenComparingDouble(member -> member.position().distanceToSqr(casualty.position()))
				.thenComparing(Comparator.comparingDouble(MemberSnapshot::healthFraction).reversed())
				.thenComparing(Comparator.comparingInt(MemberSnapshot::intelligence).reversed())
				.thenComparingInt(MemberSnapshot::entityId))
			.orElse(null);
		return escort == null ? null : responseForPair(casualty, escort, threatPosition);
	}

	/** 用实时位置刷新同一对成员的两个站位，但不改变这一轮的成员选择。 */
	public static Response responseForPair(
		final MemberSnapshot casualty,
		final MemberSnapshot escort,
		final Vec3 threatPosition
	) {
		Vec3 away = horizontalUnit(casualty.position().subtract(threatPosition), casualty.entityId(), escort.entityId());
		Vec3 side = new Vec3(-away.z, 0.0, away.x)
			.scale(((casualty.entityId() ^ escort.entityId()) & 1) == 0 ? LATERAL_OFFSET : -LATERAL_OFFSET);
		Vec3 casualtyDestination = casualty.position()
			.add(away.scale(EVACUATION_DISTANCE))
			.add(side);
		Vec3 escortDestination = casualty.position()
			.add(away.scale(-SCREEN_DISTANCE))
			.add(side.scale(0.25));
		return new Response(
			casualty.entityId(),
			escort.entityId(),
			casualtyDestination,
			escortDestination,
			threatPosition
		);
	}

	public static boolean isSafe(final Vec3 casualtyPosition, final Vec3 threatPosition) {
		return horizontalDistanceSquared(casualtyPosition, threatPosition) >= SAFE_DISTANCE * SAFE_DISTANCE;
	}

	static double horizontalDistanceSquared(final Vec3 first, final Vec3 second) {
		double x = first.x - second.x;
		double z = first.z - second.z;
		return x * x + z * z;
	}

	private static Vec3 horizontalUnit(final Vec3 vector, final int firstId, final int secondId) {
		Vec3 horizontal = new Vec3(vector.x, 0.0, vector.z);
		if (horizontal.horizontalDistanceSqr() >= 1.0E-6) {
			return horizontal.normalize();
		}
		double angle = Math.floorMod(firstId * 31 + secondId * 17, 360) * Math.PI / 180.0;
		return new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
	}

	public record MemberSnapshot(
		int entityId,
		Vec3 position,
		double healthFraction,
		int intelligence,
		boolean casualtyEligible,
		boolean escortEligible,
		boolean hasShield,
		boolean mobileCarrier
	) {
	}

	public record Response(
		int casualtyId,
		int escortId,
		Vec3 casualtyDestination,
		Vec3 escortDestination,
		Vec3 focusPosition
	) {
	}
}
