package com.wjz.mobsthinknow.paper.squad;

import com.wjz.mobsthinknow.paper.PaperEntityMath;
import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.wjz.mobsthinknow.shared.math.Vec3d;
import com.wjz.mobsthinknow.shared.squad.MixedSquadState;
import java.util.EnumSet;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

/** 只在集结、会议、部署和换届阶段接管 MOVE/LOOK；进入交战后立即把控制权还给兵种 Goal。 */
public final class PaperSquadOrderGoal implements Goal<Mob> {
	private static final double ARRIVAL_DISTANCE_SQUARED = 1.15 * 1.15;
	private static final int REPATH_TICKS = 5;

	private final Mob mob;
	private final GoalKey<Mob> key;
	private final PaperSquadCoordinator coordinator;
	private final PaperSquadMetrics metrics;
	private long nextRepathAt;

	public PaperSquadOrderGoal(
		final Mob mob,
		final GoalKey<Mob> key,
		final PaperSquadCoordinator coordinator
	) {
		this.mob = mob;
		this.key = key;
		this.coordinator = coordinator;
		this.metrics = coordinator.metrics();
	}

	@Override
	public boolean shouldActivate() {
		PaperSquadDirective directive = this.coordinator.directiveFor(this.mob);
		return this.mob.isValid() && !this.mob.isDead()
			&& directive != null
			&& directive.isHoldingForOrders();
	}

	@Override
	public boolean shouldStayActive() {
		return this.shouldActivate();
	}

	@Override
	public void start() {
		this.nextRepathAt = Bukkit.getCurrentTick();
	}

	@Override
	public void tick() {
		PaperSquadDirective directive = this.coordinator.directiveFor(this.mob);
		if (directive == null || !directive.isHoldingForOrders()) {
			return;
		}
		this.updateLook(directive);
		Vec3d point = directive.destination();
		Location destination = new Location(this.mob.getWorld(), point.x(), point.y(), point.z());
		if (PaperEntityMath.distanceSquared(this.mob, destination) <= ARRIVAL_DISTANCE_SQUARED) {
			this.mob.getPathfinder().stopPathfinding();
			return;
		}
		long now = Bukkit.getCurrentTick();
		if (now < this.nextRepathAt && this.mob.getPathfinder().hasPath()) {
			return;
		}
		Pathfinder pathfinder = this.mob.getPathfinder();
		Pathfinder.PathResult path = pathfinder.findPath(destination);
		if (path != null && pathfinder.moveTo(path, movementSpeed(directive.state()))) {
			this.metrics.orderPath();
		} else {
			this.metrics.orderPathFailure();
		}
		this.nextRepathAt = now + REPATH_TICKS;
	}

	@Override
	public void stop() {
		this.mob.getPathfinder().stopPathfinding();
		this.nextRepathAt = 0L;
	}

	@Override
	public GoalKey<Mob> getKey() {
		return this.key;
	}

	@Override
	public EnumSet<GoalType> getTypes() {
		return EnumSet.of(GoalType.MOVE, GoalType.LOOK);
	}

	private void updateLook(final PaperSquadDirective directive) {
		LivingEntity focus;
		if (directive.state().isMeetingPhase() && !this.mob.getUniqueId().equals(directive.leaderId())) {
			focus = this.coordinator.leaderFor(this.mob);
		} else {
			focus = this.coordinator.sharedTargetFor(this.mob);
		}
		if (focus != null && focus.isValid()) {
			this.mob.lookAt(focus, 35.0F, 30.0F);
		}
	}

	private static double movementSpeed(final MixedSquadState state) {
		return switch (state) {
			case FORMING, REORGANIZING -> 1.08;
			case BRIEFING -> 0.92;
			case DEPLOYING -> 1.18;
			case ENGAGING -> 1.0;
		};
	}
}
