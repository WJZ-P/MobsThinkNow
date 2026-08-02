package com.wjz.mobsthinknow.ai.zombie.squad;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** 为会议成员生成面向战场的半圆席位，并按职位把左右翼与后排放到可读位置。 */
public final class SquadMeetingFormation {
	private static final int INNER_ROW_CAPACITY = 5;
	private static final int OUTER_ROW_CAPACITY = 7;
	private static final double ROW_SPACING = 1.25;
	private static final double ANGLE_STEP_RADIANS = Math.toRadians(22.0);
	private static final double MINIMUM_HORIZONTAL_LENGTH_SQUARED = 1.0E-6;

	private SquadMeetingFormation() {
	}

	/**
	 * 返回与 {@code roles} 下标一一对应的站位。正横向为左翼，负横向为右翼；远程与辅助职位优先
	 * 使用外圈，其余近战成员优先内圈。最多 19 名跟随者时只需要三排且同排间距保持稳定。
	 */
	public static List<Vec3> arrange(
		final Vec3 center,
		final Vec3 towardTarget,
		final List<SquadRole> roles,
		final double configuredRadius
	) {
		if (roles.isEmpty()) {
			return List.of();
		}
		double radius = Math.max(1.0, configuredRadius);
		Vec3 forward = horizontalUnit(towardTarget, new Vec3(0.0, 0.0, 1.0));
		Vec3 lateral = new Vec3(-forward.z, 0.0, forward.x);
		List<Slot> available = generateSlots(roles.size(), radius);
		List<Vec3> result = new ArrayList<>(java.util.Collections.nCopies(roles.size(), center));
		List<Integer> assignmentOrder = new ArrayList<>(roles.size());
		for (int index = 0; index < roles.size(); index++) {
			assignmentOrder.add(index);
		}
		assignmentOrder.sort(
			Comparator.comparingInt((Integer index) -> assignmentPriority(roles.get(index)))
				.thenComparingInt(Integer::intValue)
		);

		for (int memberIndex : assignmentOrder) {
			SquadRole role = roles.get(memberIndex);
			int selected = selectBestSlot(available, role, memberIndex, radius);
			Slot slot = available.remove(selected);
			Vec3 offset = forward.scale(Math.cos(slot.angle()) * slot.radius())
				.add(lateral.scale(Math.sin(slot.angle()) * slot.radius()));
			result.set(memberIndex, center.add(offset));
		}
		return List.copyOf(result);
	}

	private static List<Slot> generateSlots(final int count, final double baseRadius) {
		List<Slot> slots = new ArrayList<>(count);
		int remaining = count;
		int row = 0;
		while (remaining > 0) {
			int capacity = row == 0 ? INNER_ROW_CAPACITY : OUTER_ROW_CAPACITY;
			int rowCount = Math.min(capacity, remaining);
			double radius = baseRadius + row * ROW_SPACING;
			double firstAngle = (rowCount - 1) * ANGLE_STEP_RADIANS * 0.5;
			for (int index = 0; index < rowCount; index++) {
				slots.add(new Slot(radius, firstAngle - index * ANGLE_STEP_RADIANS, row));
			}
			remaining -= rowCount;
			row++;
		}
		return slots;
	}

	private static int selectBestSlot(
		final List<Slot> slots,
		final SquadRole role,
		final int stableIndex,
		final double baseRadius
	) {
		double desiredAngle = desiredAngle(role, stableIndex);
		int selected = 0;
		double bestScore = Double.POSITIVE_INFINITY;
		for (int index = 0; index < slots.size(); index++) {
			Slot slot = slots.get(index);
			double angleCost = Math.abs(slot.angle() - desiredAngle) * 10.0;
			double rowDistance = slot.radius() - baseRadius;
			double rowCost = prefersBackRow(role) ? -rowDistance * 1.6 : rowDistance * 1.2;
			double score = angleCost + rowCost + slot.row() * 1.0E-4;
			if (score < bestScore) {
				bestScore = score;
				selected = index;
			}
		}
		return selected;
	}

	private static int assignmentPriority(final SquadRole role) {
		return switch (role) {
			case FLANK_LEFT, FLANK_RIGHT -> 0;
			case RANGED, SUPPORT -> 1;
			default -> 2;
		};
	}

	private static boolean prefersBackRow(final SquadRole role) {
		return role == SquadRole.RANGED || role == SquadRole.SUPPORT;
	}

	private static double desiredAngle(final SquadRole role, final int stableIndex) {
		return switch (role) {
			case FLANK_LEFT -> Math.toRadians(66.0);
			case FLANK_RIGHT -> Math.toRadians(-66.0);
			case RANGED, SUPPORT -> Math.toRadians((stableIndex & 1) == 0 ? 36.0 : -36.0);
			case CARRIER -> Math.toRadians((stableIndex & 1) == 0 ? 48.0 : -48.0);
			default -> Math.toRadians((stableIndex & 1) == 0 ? 8.0 : -8.0);
		};
	}

	private static Vec3 horizontalUnit(final Vec3 preferred, final Vec3 fallback) {
		Vec3 horizontal = new Vec3(preferred.x, 0.0, preferred.z);
		if (horizontal.horizontalDistanceSqr() < MINIMUM_HORIZONTAL_LENGTH_SQUARED) {
			horizontal = new Vec3(fallback.x, 0.0, fallback.z);
		}
		return horizontal.normalize();
	}

	private record Slot(double radius, double angle, int row) {
	}
}
