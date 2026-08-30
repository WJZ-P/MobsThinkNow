package com.wjz.mobsthinknow.paper.squad;

import com.wjz.mobsthinknow.shared.math.Vec3d;
import com.wjz.mobsthinknow.shared.squad.MixedSquadPlan;
import com.wjz.mobsthinknow.shared.squad.MixedSquadRole;
import com.wjz.mobsthinknow.shared.squad.MixedSquadState;
import java.util.Objects;
import java.util.UUID;

/** 协调器交给单只 Paper 怪物的不可变命令快照。 */
public record PaperSquadDirective(
	long squadId,
	int term,
	MixedSquadState state,
	MixedSquadPlan plan,
	MixedSquadRole role,
	Vec3d destination,
	double focusX,
	double focusY,
	double focusZ,
	UUID leaderId,
	UUID targetId,
	boolean hasSharedTargetMemory
) {
	boolean matches(
		final long squadId,
		final int term,
		final MixedSquadState state,
		final MixedSquadPlan plan,
		final MixedSquadRole role,
		final Vec3d destination,
		final double focusX,
		final double focusY,
		final double focusZ,
		final UUID leaderId,
		final UUID targetId,
		final boolean hasSharedTargetMemory
	) {
		return this.squadId == squadId
			&& this.term == term
			&& this.state == state
			&& this.plan == plan
			&& this.role == role
			&& this.destination.equals(destination)
			&& Double.doubleToLongBits(this.focusX) == Double.doubleToLongBits(focusX)
			&& Double.doubleToLongBits(this.focusY) == Double.doubleToLongBits(focusY)
			&& Double.doubleToLongBits(this.focusZ) == Double.doubleToLongBits(focusZ)
			&& this.leaderId.equals(leaderId)
			&& Objects.equals(this.targetId, targetId)
			&& this.hasSharedTargetMemory == hasSharedTargetMemory;
	}

	/** Compatibility view for diagnostic callers; combat goals keep the cached primitive coordinates. */
	public Vec3d focusPosition() {
		return new Vec3d(this.focusX, this.focusY, this.focusZ);
	}

	public boolean isHoldingForOrders() {
		return this.state.isFormationPhase();
	}
}
