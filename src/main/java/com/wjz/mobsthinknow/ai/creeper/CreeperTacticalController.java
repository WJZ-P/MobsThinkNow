package com.wjz.mobsthinknow.ai.creeper;

import com.wjz.mobsthinknow.ai.creeper.CreeperCombatMath.ApproachMode;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** 同一只苦力怕的接敌 Goal 与引信 Goal 共享的短期观察和计划快照。 */
public final class CreeperTacticalController {
	private final Creeper creeper;
	private final int stableFlankSide;
	private @Nullable UUID observedTarget;
	private @Nullable Vec3 lastSeenPosition;
	private long lastSeenAt = Long.MIN_VALUE;
	private ApproachMode approachMode = ApproachMode.DIRECT;
	private @Nullable Vec3 approachDestination;
	private boolean feintActive;
	private long nextFeintAt = Long.MIN_VALUE;

	public CreeperTacticalController(final Creeper creeper) {
		this.creeper = creeper;
		this.stableFlankSide = (Long.hashCode(creeper.getUUID().getLeastSignificantBits()) & 1) == 0 ? -1 : 1;
	}

	public boolean observe(final LivingEntity target) {
		if (!target.getUUID().equals(this.observedTarget)) {
			this.observedTarget = target.getUUID();
			this.lastSeenPosition = null;
			this.lastSeenAt = Long.MIN_VALUE;
		}
		boolean visible = this.creeper.getSensing().hasLineOfSight(target);
		if (visible) {
			this.lastSeenPosition = target.position();
			this.lastSeenAt = this.creeper.level().getGameTime();
		}
		return visible;
	}

	public boolean hasRecentSight(final int maximumAgeTicks) {
		return this.lastSeenPosition != null
			&& this.creeper.level().getGameTime() - this.lastSeenAt <= maximumAgeTicks;
	}

	public @Nullable Vec3 lastSeenPosition() {
		return this.lastSeenPosition;
	}

	public int stableFlankSide() {
		return this.stableFlankSide;
	}

	public void rememberApproach(final ApproachMode mode, final Vec3 destination) {
		this.approachMode = mode;
		this.approachDestination = destination;
	}

	public ApproachMode approachMode() {
		return this.approachMode;
	}

	public @Nullable Vec3 approachDestination() {
		return this.approachDestination;
	}

	public void clearApproach() {
		this.approachMode = ApproachMode.DIRECT;
		this.approachDestination = null;
	}

	public boolean canStartFeint(final long currentTick) {
		return !this.feintActive && currentTick >= this.nextFeintAt;
	}

	public boolean isFeintActive() {
		return this.feintActive;
	}

	public void beginFeint() {
		this.feintActive = true;
	}

	public void finishFeint(final long currentTick, final int cooldownTicks) {
		this.feintActive = false;
		this.nextFeintAt = currentTick + Math.max(1, cooldownTicks);
	}
}
