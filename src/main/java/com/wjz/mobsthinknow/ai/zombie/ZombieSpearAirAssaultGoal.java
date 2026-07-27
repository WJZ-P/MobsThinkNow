package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 持矛空袭僵尸的完整服务器状态机。
 *
 * <p>本 Goal 只负责决策、朝向和装备消耗。滑翔阻力/升力、烟花附着助推、鞘翅耐久以及
 * 长矛沿运动方向的碰撞伤害都继续走 26.1.2 原版实现，避免出现“看似在飞，实际是瞬移”
 * 或“动画举矛，伤害却是普通挥击”的两套规则。</p>
 */
public final class ZombieSpearAirAssaultGoal extends Goal {
	private static final int LAUNCH_SEARCH_RADIUS = 7;
	private static final int LAUNCH_CLEARANCE_HEIGHT = 6;
	// 七格方环、每个水平位置检查脚部上下各一格：24 * (1+...+7) = 672，仍是固定上限。
	private static final int MAXIMUM_RAW_LAUNCH_CHECKS = 672;
	private static final int MAXIMUM_LAUNCH_PATH_CHECKS = 4;
	private static final int LAUNCH_SEARCH_INTERVAL_TICKS = 20;
	private static final int PATH_REFRESH_TICKS = 16;
	private static final int CLIMB_TIMEOUT_TICKS = 100;
	static final int MINIMUM_ORBIT_TICKS = 60;
	static final int MAXIMUM_ORBIT_TICKS = 120;
	static final int MINIMUM_ORBIT_ROCKETS = 1;
	static final int MAXIMUM_ORBIT_ROCKETS = 2;
	static final int MINIMUM_ORBIT_FIRST_ROCKET_DELAY_TICKS = 12;
	static final int MAXIMUM_ORBIT_FIRST_ROCKET_DELAY_TICKS = 24;
	static final int MINIMUM_ORBIT_ROCKET_GAP_TICKS = 48;
	static final int MAXIMUM_ORBIT_ROCKET_GAP_TICKS = 68;
	private static final int ORBIT_LINE_OF_SIGHT_GRACE_TICKS = 80;
	private static final int MINIMUM_ROCKET_COOLDOWN_TICKS = 40;
	private static final int MAXIMUM_ROCKET_COOLDOWN_TICKS = 60;
	private static final int DIVE_TIMEOUT_TICKS = 80;
	private static final int RECOVERY_TIMEOUT_TICKS = 100;
	private static final double LAUNCH_MOVE_SPEED = 1.10;
	private static final double LAUNCH_REACHED_SQUARED = 0.85 * 0.85;
	private static final double DESIRED_ALTITUDE = 6.5;
	private static final double ORBIT_RADIUS = 10.0;
	private static final double ORBIT_ANGULAR_SPEED = 0.045;
	private static final double ORBIT_LOOK_AHEAD = 0.42;
	private static final double ATTACK_STAGING_DISTANCE = 10.0;
	private static final double MINIMUM_ATTACK_DISTANCE_SQUARED = 6.0 * 6.0;
	private static final double POST_ATTACK_CLEAR_DISTANCE_SQUARED = 6.0 * 6.0;
	private static final double RECOVERY_EMERGENCY_SPEED_SQUARED = 0.55 * 0.55;

	private final Zombie zombie;
	private Phase phase = Phase.IDLE;
	private @Nullable LivingEntity target;
	private @Nullable BlockPos launchSite;
	private @Nullable Vec3 approachDirection;
	private long phaseStartedAt;
	private long nextLaunchSearchAt;
	private long nextPathRefreshAt;
	private long nextRocketAt;
	private long nextOrbitRocketAt;
	private long attackAllowedAt;
	private long orbitDeadline;
	private long spearReadyAt;
	private int orbitRocketBudget;
	private int orbitRocketsLaunched;
	private double orbitAngle;
	private double orbitDirection;
	private double closestDiveDistanceSquared = Double.MAX_VALUE;
	private boolean diveHitConfirmed;
	private boolean climbEmergencyRocketUsed;
	private boolean recoveryEmergencyRocketUsed;

	public ZombieSpearAirAssaultGoal(final Zombie zombie) {
		this.zombie = zombie;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		MobsThinkNowConfig config = ConfigManager.get();
		if (!ZombieAirAssault.isEnabled(config) || !ZombieAirAssault.isAirAssaultLoadout(this.zombie)) {
			return false;
		}

		LivingEntity currentTarget = validTarget(this.zombie.getTarget());
		if (currentTarget != null && ZombieAirAssault.isFlightReady(this.zombie, config)) {
			this.target = currentTarget;
			return true;
		}
		// 只接管已经真实进入滑翔的无目标实体。持鞘翅僵尸走台阶或普通跳跃时也会短暂
		// onGround=false；旧判断把这种一个 tick 的离地误认成飞行，导致客户端姿态反复横置/站立。
		return this.zombie.isFallFlying();
	}

	@Override
	public boolean canContinueToUse() {
		MobsThinkNowConfig config = ConfigManager.get();
		if (!ZombieAirAssault.isEnabled(config)
			|| !ZombieAirAssault.isAirAssaultLoadout(this.zombie)
			|| !this.zombie.isAlive()) {
			return false;
		}

		if (!this.zombie.onGround() || this.zombie.isFallFlying()) {
			return true;
		}
		return validTarget(this.zombie.getTarget()) != null
			&& ZombieAirAssault.isFlightReady(this.zombie, config)
			&& this.phase != Phase.IDLE;
	}

	@Override
	public void start() {
		this.target = validTarget(this.zombie.getTarget());
		this.zombie.getNavigation().stop();
		this.zombie.stopUsingItem();
		this.zombie.setAggressive(false);
		this.nextRocketAt = this.now();

		MobsThinkNowConfig config = ConfigManager.get();
		if (this.target == null || !ZombieAirAssault.isFlightReady(this.zombie, config)) {
			this.enterPhase(Phase.LANDING);
		} else if (this.zombie.onGround()) {
			this.enterPhase(Phase.SEEKING_LAUNCH);
			this.nextLaunchSearchAt = this.now();
		} else {
			this.beginGliding();
		}
	}

	@Override
	public void tick() {
		LivingEntity currentTarget = validTarget(this.zombie.getTarget());
		if (currentTarget != null) {
			this.target = currentTarget;
		} else {
			this.target = null;
		}

		if (this.target == null || !ZombieAirAssault.hasUsableGlider(this.zombie)) {
			if (this.phase != Phase.LANDING) {
				this.enterLanding();
			}
			this.tickLanding();
			return;
		}

		switch (this.phase) {
			case SEEKING_LAUNCH -> this.tickSeekingLaunch();
			case LAUNCHING -> this.tickLaunching();
			case CLIMBING -> this.tickClimbing();
			case ORBITING -> this.tickOrbiting();
			case ARMING -> this.tickArming();
			case DIVING -> this.tickDiving();
			case RECOVERING -> this.tickRecovering();
			case LANDING -> this.tickLanding();
			case IDLE -> {
				if (!this.zombie.onGround()) {
					this.enterLanding();
				} else if (ZombieAirAssault.hasRockets(this.zombie)) {
					this.enterPhase(Phase.SEEKING_LAUNCH);
				}
			}
		}
	}

	@Override
	public void stop() {
		this.zombie.getNavigation().stop();
		this.zombie.stopUsingItem();
		this.zombie.setAggressive(false);
		// Goal 被配置热切换、目标规则或其他高优先级 Goal 中断时，也必须交还
		// 原版重力控制；不能把已经失去控制器的僵尸留在滑翔标志中。
		this.stopGliding();
		this.enterPhase(Phase.IDLE);
		this.target = null;
		this.launchSite = null;
		this.approachDirection = null;
		this.attackAllowedAt = 0L;
		this.orbitDeadline = 0L;
		this.nextOrbitRocketAt = 0L;
		this.orbitRocketBudget = 0;
		this.orbitRocketsLaunched = 0;
		this.diveHitConfirmed = false;
		this.climbEmergencyRocketUsed = false;
		this.recoveryEmergencyRocketUsed = false;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	private void tickSeekingLaunch() {
		if (!ZombieAirAssault.hasRockets(this.zombie)) {
			this.enterLanding();
			return;
		}
		if (!this.zombie.onGround()) {
			this.beginGliding();
			return;
		}

		this.zombie.setAggressive(false);
		this.aimAt(this.target.getEyePosition().add(0.0, DESIRED_ALTITUDE, 0.0));
		long now = this.now();
		if (this.launchSite != null) {
			Vec3 destination = Vec3.atBottomCenterOf(this.launchSite);
			if (horizontalDistanceSquared(this.zombie.position(), destination) <= LAUNCH_REACHED_SQUARED
				&& Math.abs(this.zombie.getY() - destination.y) <= 0.75
				&& this.isLaunchSite(this.serverLevel(), this.launchSite)) {
				this.requestTakeoff();
				return;
			}
			if (now >= this.nextPathRefreshAt) {
				Path refreshed = this.zombie.getNavigation().createPath(this.launchSite, 0);
				if (refreshed == null || !refreshed.canReach()) {
					this.clearLaunchPlan();
					this.nextLaunchSearchAt = now;
				} else {
					this.zombie.getNavigation().moveTo(refreshed, LAUNCH_MOVE_SPEED);
					this.nextPathRefreshAt = now + PATH_REFRESH_TICKS;
				}
			}
			return;
		}

		if (now < this.nextLaunchSearchAt) {
			return;
		}
		LaunchPlan plan = this.findLaunchPlan(this.serverLevel());
		this.nextLaunchSearchAt = now + LAUNCH_SEARCH_INTERVAL_TICKS;
		if (plan == null) {
			return;
		}
		this.launchSite = plan.site();
		if (plan.path() == null) {
			this.requestTakeoff();
		} else {
			this.zombie.getNavigation().moveTo(plan.path(), LAUNCH_MOVE_SPEED);
			this.nextPathRefreshAt = now + PATH_REFRESH_TICKS;
		}
	}

	private void requestTakeoff() {
		this.zombie.getNavigation().stop();
		Vec3 horizontal = horizontalUnit(this.target.position().subtract(this.zombie.position()));
		Vec3 movement = this.zombie.getDeltaMovement();
		this.zombie.setDeltaMovement(
			movement.x * 0.35 + horizontal.x * 0.18,
			movement.y,
			movement.z * 0.35 + horizontal.z * 0.18
		);
		this.zombie.getJumpControl().jump();
		this.enterPhase(Phase.LAUNCHING);
	}

	private void tickLaunching() {
		Vec3 climbPoint = this.climbPoint();
		this.aimAt(climbPoint);
		if (!this.zombie.onGround()) {
			this.beginGliding();
			return;
		}
		if (this.ticksInPhase() > 24) {
			this.clearLaunchPlan();
			this.enterPhase(Phase.SEEKING_LAUNCH);
			this.nextLaunchSearchAt = this.now();
			return;
		}
		this.zombie.getJumpControl().jump();
	}

	private void beginGliding() {
		if (this.zombie.onGround() || !ZombieAirAssault.hasUsableGlider(this.zombie)) {
			this.enterLanding();
			return;
		}
		this.ensureGliding();
		this.aimAt(this.climbPoint());
		this.launchRocket();
		this.climbEmergencyRocketUsed = false;
		this.clearLaunchPlan();
		this.enterPhase(Phase.CLIMBING);
	}

	private void tickClimbing() {
		if (this.zombie.onGround()) {
			this.stopGliding();
			if (ZombieAirAssault.hasRockets(this.zombie)) {
				this.enterPhase(Phase.SEEKING_LAUNCH);
				this.nextLaunchSearchAt = this.now();
			} else {
				this.enterPhase(Phase.IDLE);
			}
			return;
		}
		this.ensureGliding();
		Vec3 climbPoint = this.climbPoint();
		this.aimAt(climbPoint);
		this.redirectVelocityToward(climbPoint, 0.075);

		double altitude = this.zombie.getY() - this.target.getY();
		double horizontal = horizontalDistanceSquared(this.zombie.position(), this.target.position());
		if (altitude >= DESIRED_ALTITUDE - 1.0 && horizontal >= 4.0 * 4.0) {
			this.beginOrbiting();
			return;
		}
		if (this.zombie.horizontalCollision) {
			this.beginRecovery();
			return;
		}
		if (!this.climbEmergencyRocketUsed
			&& ZombieAirAssault.hasRockets(this.zombie)
			&& this.now() >= this.nextRocketAt
			&& (altitude < DESIRED_ALTITUDE - 1.5 || this.zombie.getDeltaMovement().lengthSqr() < 0.70 * 0.70)) {
			this.climbEmergencyRocketUsed = this.launchRocket();
		}
		if (this.ticksInPhase() >= CLIMB_TIMEOUT_TICKS) {
			if (!ZombieAirAssault.hasRockets(this.zombie) && altitude < 2.0) {
				this.enterLanding();
			} else {
				this.beginOrbiting();
			}
		}
	}

	/**
	 * 每轮攻击前先进入真正的环绕航线，重新抽取 3～6 秒等待时间，并制定只消耗 1～2 枚烟花的
	 * 推进计划。烟花之间保留 2.4～3.4 秒的滑翔窗口，让鞘翅利用惯性而不是持续喷射。
	 * 初次升空和每次穿越目标后的拉起都会经过这里，因此不会在恢复高度后立刻连续俯冲。
	 */
	private void beginOrbiting() {
		this.stopUsingSpear();
		this.zombie.setAggressive(false);
		Vec3 radial = horizontalUnitOrFallback(this.zombie.position().subtract(this.target.position()));
		this.orbitAngle = Mth.atan2(radial.z, radial.x);
		this.orbitDirection = this.zombie.getRandom().nextBoolean() ? 1.0 : -1.0;
		long now = this.now();
		this.attackAllowedAt = now + orbitDurationTicks(this.zombie.getRandom().nextDouble());
		this.orbitRocketBudget = Math.min(
			orbitRocketCount(this.zombie.getRandom().nextDouble()),
			this.availableRocketCount()
		);
		this.orbitRocketsLaunched = 0;
		this.nextOrbitRocketAt = Math.max(
			this.nextRocketAt,
			now + orbitFirstRocketDelayTicks(this.zombie.getRandom().nextDouble())
		);
		long finalPlannedRocketAt = this.nextOrbitRocketAt
			+ (long)Math.max(0, this.orbitRocketBudget - 1) * MAXIMUM_ORBIT_ROCKET_GAP_TICKS;
		this.orbitDeadline = Math.max(this.attackAllowedAt, finalPlannedRocketAt)
			+ ORBIT_LINE_OF_SIGHT_GRACE_TICKS;
		this.approachDirection = radial;
		this.enterPhase(Phase.ORBITING);
	}

	private void tickOrbiting() {
		if (this.zombie.onGround()) {
			this.stopUsingSpear();
			this.stopGliding();
			if (ZombieAirAssault.hasRockets(this.zombie)) {
				this.enterPhase(Phase.SEEKING_LAUNCH);
				this.nextLaunchSearchAt = this.now();
			} else {
				this.enterPhase(Phase.IDLE);
			}
			return;
		}
		this.ensureGliding();
		this.orbitAngle += this.orbitDirection * ORBIT_ANGULAR_SPEED;
		Vec3 orbitPoint = this.orbitPoint(this.orbitAngle + this.orbitDirection * ORBIT_LOOK_AHEAD);
		this.aimAt(orbitPoint);
		this.redirectVelocityToward(orbitPoint, 0.09);
		if (this.zombie.horizontalCollision) {
			this.orbitDirection = -this.orbitDirection;
			this.beginRecovery();
			return;
		}

		long now = this.now();
		this.tickOrbitRocketPlan(now);
		double horizontal = horizontalDistanceSquared(this.zombie.position(), this.target.position());
		boolean hasLineOfSight = this.zombie.getSensing().hasLineOfSight(this.target);
		if (now >= this.attackAllowedAt
			&& this.isOrbitRocketPlanComplete()
			&& hasLineOfSight
			&& horizontal >= MINIMUM_ATTACK_DISTANCE_SQUARED) {
			this.beginArming();
			return;
		}
		if (now >= this.orbitDeadline) {
			if (!hasLineOfSight && !ZombieAirAssault.hasRockets(this.zombie)) {
				this.enterLanding();
				return;
			}
			// 被建筑长期遮挡时继续换向盘旋并重试，而不是在看不见目标时盲冲。
			this.orbitDirection = -this.orbitDirection;
			this.attackAllowedAt = now + 20L + this.zombie.getRandom().nextInt(41);
			this.orbitDeadline = this.attackAllowedAt + ORBIT_LINE_OF_SIGHT_GRACE_TICKS;
		}
	}

	/**
	 * 先在攻击航线外蓄矛，再进入高速俯冲。
	 *
	 * <p>原版铁矛的 {@link KineticWeapon#delayTicks()} 为 12 tick。烟花滑翔速度足以让僵尸
	 * 在这段时间内直接飞过目标，所以不能像地面 Goal 那样“开始冲锋时才举矛”，否则看似命中
	 * 但原版 KineticWeapon 尚未进入起伤窗口。这里仍只调用原版使用物品链路，不额外伪造伤害。
	 */
	private void beginArming() {
		this.stopUsingSpear();
		this.zombie.setAggressive(true);
		// 冻结本次突击的进场方向；蓄力期间保持在目标外圈，不再沿盘旋点继续漂移。
		this.approachDirection = horizontalUnitOrFallback(this.zombie.position().subtract(this.target.position()));
		this.zombie.startUsingItem(InteractionHand.MAIN_HAND);
		KineticWeapon kineticWeapon = this.zombie.getMainHandItem().get(DataComponents.KINETIC_WEAPON);
		if (kineticWeapon != null) {
			kineticWeapon.makeSound(this.zombie);
		}
		this.spearReadyAt = this.now() + (kineticWeapon == null ? 0L : kineticWeapon.delayTicks()) + 1L;
		this.enterPhase(Phase.ARMING);
	}

	private void tickArming() {
		if (this.zombie.onGround()) {
			this.stopUsingSpear();
			this.stopGliding();
			if (ZombieAirAssault.hasRockets(this.zombie)) {
				this.enterPhase(Phase.SEEKING_LAUNCH);
				this.nextLaunchSearchAt = this.now();
			} else {
				this.enterPhase(Phase.IDLE);
			}
			return;
		}

		this.ensureGliding();
		Vec3 holdingPoint = this.stagingPoint();
		this.aimAt(holdingPoint);
		this.redirectVelocityToward(holdingPoint, 0.11);
		if (!this.zombie.isUsingItem() || !this.zombie.getUseItem().has(DataComponents.KINETIC_WEAPON)) {
			// 装备被其他 Mod 临时改动时重新计时，避免跳过原版长矛的准备窗口。
			this.beginArming();
			return;
		}
		if (this.zombie.horizontalCollision) {
			this.beginRecovery();
			return;
		}
		if (this.now() >= this.spearReadyAt) {
			this.beginDive();
		}
	}

	private void beginDive() {
		this.zombie.setAggressive(true);
		this.aimAt(this.predictedTargetPoint());
		this.closestDiveDistanceSquared = this.zombie.distanceToSqr(this.target);
		this.diveHitConfirmed = false;
		statusAccess(this.zombie).mobsthinknow$recordDiveStart();
		this.enterPhase(Phase.DIVING);
	}

	private void tickDiving() {
		if (this.zombie.onGround()) {
			this.stopUsingSpear();
			this.stopGliding();
			if (ZombieAirAssault.hasRockets(this.zombie)) {
				this.enterPhase(Phase.SEEKING_LAUNCH);
				this.nextLaunchSearchAt = this.now();
			} else {
				this.enterPhase(Phase.IDLE);
			}
			return;
		}
		this.ensureGliding();
		Vec3 aimPoint = this.predictedTargetPoint();
		this.aimAt(aimPoint);
		this.redirectVelocityToward(aimPoint, 0.14);
		if (!this.diveHitConfirmed
			&& (!this.zombie.isUsingItem() || !this.zombie.getUseItem().has(DataComponents.KINETIC_WEAPON))) {
			// 使用状态若被其他逻辑打断，必须重新完成组件声明的蓄力时间，不能带着旧计时继续俯冲。
			this.beginArming();
			return;
		}

		double distanceSquared = this.zombie.distanceToSqr(this.target);
		boolean hitTarget = this.zombie.wasRecentlyStabbed(this.target, 12)
			|| this.zombie.getLastHurtMob() == this.target
				&& this.zombie.tickCount - this.zombie.getLastHurtMobTimestamp() <= 3;
		if (hitTarget && !this.diveHitConfirmed) {
			this.diveHitConfirmed = true;
			// 一次航线只允许一次刺击；随后保持当前速度穿越目标，但不在同一航线重新触发接触伤害。
			this.stopUsingSpear();
			this.zombie.setAggressive(false);
		}
		boolean passedTarget = this.closestDiveDistanceSquared < 4.0 * 4.0
			&& distanceSquared > this.closestDiveDistanceSquared + 3.0 * 3.0;
		this.closestDiveDistanceSquared = Math.min(this.closestDiveDistanceSquared, distanceSquared);
		boolean clearedAfterHit = this.diveHitConfirmed
			&& this.closestDiveDistanceSquared < 4.0 * 4.0
			&& distanceSquared >= POST_ATTACK_CLEAR_DISTANCE_SQUARED;

		// 命中并不立即掉头：继续沿原航线穿过目标，拉开六格后才进入恢复爬升。
		if (clearedAfterHit
			|| !this.diveHitConfirmed && passedTarget
			|| this.zombie.horizontalCollision
			|| this.ticksInPhase() >= DIVE_TIMEOUT_TICKS) {
			this.beginRecovery();
			return;
		}
	}

	private void beginRecovery() {
		this.stopUsingSpear();
		this.zombie.setAggressive(false);
		this.approachDirection = horizontalUnitOrFallback(this.zombie.position().subtract(this.target.position()));
		if (ZombieAirAssault.hasRockets(this.zombie)) {
			this.recoveryEmergencyRocketUsed = false;
			this.enterPhase(Phase.RECOVERING);
		} else {
			this.enterLanding();
		}
	}

	private void tickRecovering() {
		if (!ZombieAirAssault.hasRockets(this.zombie)) {
			this.enterLanding();
			return;
		}
		if (this.zombie.onGround()) {
			this.stopGliding();
			this.enterPhase(Phase.SEEKING_LAUNCH);
			this.nextLaunchSearchAt = this.now();
			return;
		}
		this.ensureGliding();
		Vec3 recoveryPoint = this.stagingPoint();
		this.aimAt(recoveryPoint);
		this.redirectVelocityToward(recoveryPoint, 0.10);

		double altitude = this.zombie.getY() - this.target.getY();
		double horizontal = horizontalDistanceSquared(this.zombie.position(), this.target.position());
		if (altitude >= DESIRED_ALTITUDE - 1.0 && horizontal >= 7.0 * 7.0) {
			this.beginOrbiting();
			return;
		}
		if (!this.recoveryEmergencyRocketUsed
			&& this.now() >= this.nextRocketAt
			&& altitude < DESIRED_ALTITUDE - 3.0
			&& this.zombie.getDeltaMovement().lengthSqr() < RECOVERY_EMERGENCY_SPEED_SQUARED) {
			// 俯冲后的恢复默认依靠已有动能；只有低空且接近失速时才允许一次救援推进。
			this.recoveryEmergencyRocketUsed = this.launchRocket();
		}
		if (this.ticksInPhase() >= RECOVERY_TIMEOUT_TICKS) {
			this.beginOrbiting();
		}
	}

	private void enterLanding() {
		this.stopUsingSpear();
		this.zombie.getNavigation().stop();
		this.zombie.setAggressive(false);
		this.enterPhase(Phase.LANDING);
	}

	private void tickLanding() {
		this.stopUsingSpear();
		this.zombie.setAggressive(false);
		if (this.zombie.onGround()) {
			this.stopGliding();
			this.enterPhase(Phase.IDLE);
			return;
		}

		if (ZombieAirAssault.hasUsableGlider(this.zombie)) {
			this.ensureGliding();
			Vec3 movement = this.zombie.getDeltaMovement();
			Vec3 horizontal = horizontalUnit(movement);
			if (horizontal.lengthSqr() < 1.0E-8) {
				horizontal = this.target == null
					? horizontalUnitOrFallback(Vec3.ZERO)
					: horizontalUnitOrFallback(this.zombie.position().subtract(this.target.position()));
			}
			Vec3 landingAim = this.zombie.getEyePosition().add(horizontal.scale(12.0)).add(0.0, -5.0, 0.0);
			this.aimAt(landingAim);
			this.redirectVelocityToward(landingAim, 0.04);
		}
	}

	private Vec3 climbPoint() {
		Vec3 away = horizontalUnitOrFallback(this.zombie.position().subtract(this.target.position()));
		return this.target.getEyePosition().add(away.scale(5.0)).add(0.0, DESIRED_ALTITUDE, 0.0);
	}

	private Vec3 stagingPoint() {
		Vec3 direction = this.approachDirection == null
			? horizontalUnitOrFallback(this.zombie.position().subtract(this.target.position()))
			: this.approachDirection;
		return this.target.getEyePosition().add(direction.scale(ATTACK_STAGING_DISTANCE)).add(0.0, DESIRED_ALTITUDE, 0.0);
	}

	private Vec3 orbitPoint(final double angle) {
		return this.target.getEyePosition().add(
			Math.cos(angle) * ORBIT_RADIUS,
			DESIRED_ALTITUDE,
			Math.sin(angle) * ORBIT_RADIUS
		);
	}

	private Vec3 predictedTargetPoint() {
		double distance = Math.sqrt(this.zombie.distanceToSqr(this.target));
		double speed = Math.max(0.55, this.zombie.getDeltaMovement().length());
		double leadTicks = Math.clamp(distance / speed, 0.0, 8.0);
		return this.target.getEyePosition().add(this.target.getDeltaMovement().scale(leadTicks));
	}

	private void ensureGliding() {
		if (!this.zombie.isFallFlying() && !this.zombie.onGround() && ZombieAirAssault.hasUsableGlider(this.zombie)) {
			flightAccess(this.zombie).mobsthinknow$startFallFlying();
		}
		if (this.zombie.isFallFlying() && !this.zombie.hasPose(Pose.FALL_FLYING)) {
			this.zombie.setPose(Pose.FALL_FLYING);
		}
	}

	private void stopGliding() {
		flightAccess(this.zombie).mobsthinknow$stopFallFlying();
	}

	private void stopUsingSpear() {
		if (this.zombie.isUsingItem()) {
			this.zombie.stopUsingItem();
		}
	}

	/**
	 * 执行当前盘旋阶段的限额推进计划。
	 *
	 * <p>这里不再根据“速度略低”每隔几十 tick 无上限补火箭，而是每轮先固定预算，再按较长间隔
	 * 消耗预算。若烟花被其他装备逻辑移走，则把现有发射数视为本轮上限，避免状态机永久卡在盘旋。
	 */
	private void tickOrbitRocketPlan(final long now) {
		if (this.isOrbitRocketPlanComplete()) {
			return;
		}
		if (this.availableRocketCount() <= 0) {
			this.orbitRocketBudget = this.orbitRocketsLaunched;
			return;
		}
		if (now < this.nextOrbitRocketAt || now < this.nextRocketAt) {
			return;
		}

		if (this.launchRocket()) {
			this.orbitRocketsLaunched++;
			this.nextOrbitRocketAt = now + orbitRocketGapTicks(this.zombie.getRandom().nextDouble());
			// 第二枚烟花排到原截止线之后时同步延后截止线，保证预算有机会正常完成。
			this.orbitDeadline = Math.max(
				this.orbitDeadline,
				this.nextOrbitRocketAt + ORBIT_LINE_OF_SIGHT_GRACE_TICKS
			);
		} else {
			// 实体加入世界偶发失败时短暂重试；不扣库存，也不消耗本轮预算。
			this.nextOrbitRocketAt = now + 10L;
		}
	}

	private boolean isOrbitRocketPlanComplete() {
		return this.orbitRocketsLaunched >= this.orbitRocketBudget;
	}

	private int availableRocketCount() {
		ItemStack rockets = this.zombie.getOffhandItem();
		return rockets.is(Items.FIREWORK_ROCKET) ? rockets.getCount() : 0;
	}

	private boolean launchRocket() {
		if (!(this.zombie.level() instanceof ServerLevel level)
			|| this.now() < this.nextRocketAt) {
			return false;
		}
		ItemStack rockets = this.zombie.getOffhandItem();
		if (!rockets.is(Items.FIREWORK_ROCKET) || rockets.isEmpty()) {
			return false;
		}

		ItemStack fired = rockets.copyWithCount(1);
		ZombieAirAssault.markRocketEfficiency(fired, ConfigManager.get().spearRocketEfficiency);
		FireworkRocketEntity firework = new FireworkRocketEntity(level, fired, this.zombie);
		if (!level.addFreshEntity(firework)) {
			return false;
		}
		rockets.shrink(1);
		statusAccess(this.zombie).mobsthinknow$recordRocketLaunch();
		this.zombie.swing(InteractionHand.OFF_HAND);
		this.nextRocketAt = this.now() + rocketCooldownTicks(this.zombie.getRandom().nextDouble());
		return true;
	}

	private void aimAt(final Vec3 point) {
		Vec3 delta = point.subtract(this.zombie.getEyePosition());
		double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		if (delta.lengthSqr() < 1.0E-8) {
			return;
		}
		float yaw = Mth.wrapDegrees((float)(Mth.atan2(delta.z, delta.x) * 180.0 / Math.PI) - 90.0F);
		float pitch = Mth.clamp((float)(-(Mth.atan2(delta.y, horizontal) * 180.0 / Math.PI)), -75.0F, 75.0F);
		this.zombie.setYRot(yaw);
		this.zombie.setYHeadRot(yaw);
		this.zombie.setYBodyRot(yaw);
		this.zombie.setXRot(pitch);
		this.zombie.getLookControl().setLookAt(point.x, point.y, point.z, 180.0F, 180.0F);
	}

	/** 只改变当前速度的方向，不凭空增加速度；真正的能量仍来自下落与烟花。 */
	private void redirectVelocityToward(final Vec3 point, final double blend) {
		Vec3 movement = this.zombie.getDeltaMovement();
		double speed = movement.length();
		if (speed < 0.08) {
			return;
		}
		Vec3 desired = point.subtract(this.zombie.getEyePosition());
		if (desired.lengthSqr() < 1.0E-8) {
			return;
		}
		Vec3 redirected = movement.normalize().scale(1.0 - blend).add(desired.normalize().scale(blend));
		if (redirected.lengthSqr() > 1.0E-8) {
			this.zombie.setDeltaMovement(redirected.normalize().scale(speed));
		}
	}

	private @Nullable LaunchPlan findLaunchPlan(final ServerLevel level) {
		BlockPos origin = BlockPos.containing(this.zombie.getX(), this.zombie.getBoundingBox().minY + 0.01, this.zombie.getZ());
		if (this.isLaunchSite(level, origin)) {
			return new LaunchPlan(origin.immutable(), null);
		}

		List<BlockPos> candidates = new ArrayList<>();
		int rawChecks = 0;
		outer:
		for (int radius = 1; radius <= LAUNCH_SEARCH_RADIUS; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
						continue;
					}
					for (int dy = -1; dy <= 1; dy++) {
						if (rawChecks++ >= MAXIMUM_RAW_LAUNCH_CHECKS) {
							break outer;
						}
						BlockPos candidate = origin.offset(dx, dy, dz);
						if (this.isLaunchSite(level, candidate)) {
							candidates.add(candidate.immutable());
						}
					}
				}
			}
		}

		candidates.sort(
			Comparator.comparingDouble((BlockPos pos) -> this.zombie.distanceToSqr(Vec3.atBottomCenterOf(pos)))
				.thenComparingLong(BlockPos::asLong)
		);
		int pathChecks = 0;
		for (BlockPos candidate : candidates) {
			if (pathChecks++ >= MAXIMUM_LAUNCH_PATH_CHECKS) {
				break;
			}
			Path path = this.zombie.getNavigation().createPath(candidate, 0);
			if (path != null && path.canReach()) {
				return new LaunchPlan(candidate, path);
			}
		}
		return null;
	}

	private boolean isLaunchSite(final ServerLevel level, final BlockPos feet) {
		if (!level.getChunkSource().hasChunk(
			SectionPos.blockToSectionCoord(feet.getX()),
			SectionPos.blockToSectionCoord(feet.getZ())
		)
			|| !level.canSeeSky(feet.above())
			|| !level.getBlockState(feet.below()).isCollisionShapeFullBlock(level, feet.below())) {
			return false;
		}
		for (int dy = 0; dy <= LAUNCH_CLEARANCE_HEIGHT; dy++) {
			BlockPos checked = feet.above(dy);
			if (!level.getBlockState(checked).getCollisionShape(level, checked).isEmpty()
				|| !level.getFluidState(checked).isEmpty()) {
				return false;
			}
		}

		Vec3 destination = Vec3.atBottomCenterOf(feet);
		AABB bodyAndTakeoffColumn = this.zombie.getBoundingBox()
			.move(
				destination.x - this.zombie.getX(),
				destination.y - this.zombie.getY(),
				destination.z - this.zombie.getZ()
			)
			.expandTowards(0.0, LAUNCH_CLEARANCE_HEIGHT - this.zombie.getBbHeight(), 0.0);
		return level.noCollision(this.zombie, bodyAndTakeoffColumn);
	}

	private void clearLaunchPlan() {
		this.launchSite = null;
	}

	private void enterPhase(final Phase next) {
		this.phase = next;
		this.phaseStartedAt = this.now();
		statusAccess(this.zombie).mobsthinknow$setAirAssaultPhase(next);
	}

	private long ticksInPhase() {
		return this.now() - this.phaseStartedAt;
	}

	private long now() {
		return this.zombie.level().getGameTime();
	}

	private ServerLevel serverLevel() {
		return (ServerLevel)this.zombie.level();
	}

	private static @Nullable LivingEntity validTarget(final @Nullable LivingEntity target) {
		return target != null && target.isAlive() && !target.isRemoved() ? target : null;
	}

	private static ZombieFlightAccess flightAccess(final Zombie zombie) {
		return (ZombieFlightAccess)zombie;
	}

	private static ZombieAirAssaultStatusAccess statusAccess(final Zombie zombie) {
		return (ZombieAirAssaultStatusAccess)zombie;
	}

	private Vec3 horizontalUnitOrFallback(final Vec3 vector) {
		Vec3 horizontal = horizontalUnit(vector);
		if (horizontal.lengthSqr() >= 1.0E-8) {
			return horizontal;
		}
		double angle = Math.floorMod(this.zombie.getId() * 73, 360) * Math.PI / 180.0;
		return new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
	}

	private static Vec3 horizontalUnit(final Vec3 vector) {
		Vec3 horizontal = new Vec3(vector.x, 0.0, vector.z);
		return horizontal.lengthSqr() < 1.0E-8 ? Vec3.ZERO : horizontal.normalize();
	}

	private static double horizontalDistanceSquared(final Vec3 first, final Vec3 second) {
		double x = first.x - second.x;
		double z = first.z - second.z;
		return x * x + z * z;
	}

	static int orbitDurationTicks(final double roll) {
		return rangedTicks(MINIMUM_ORBIT_TICKS, MAXIMUM_ORBIT_TICKS, roll);
	}

	static int orbitRocketCount(final double roll) {
		return rangedTicks(MINIMUM_ORBIT_ROCKETS, MAXIMUM_ORBIT_ROCKETS, roll);
	}

	static int orbitFirstRocketDelayTicks(final double roll) {
		return rangedTicks(
			MINIMUM_ORBIT_FIRST_ROCKET_DELAY_TICKS,
			MAXIMUM_ORBIT_FIRST_ROCKET_DELAY_TICKS,
			roll
		);
	}

	static int orbitRocketGapTicks(final double roll) {
		return rangedTicks(MINIMUM_ORBIT_ROCKET_GAP_TICKS, MAXIMUM_ORBIT_ROCKET_GAP_TICKS, roll);
	}

	static int rocketCooldownTicks(final double roll) {
		return rangedTicks(MINIMUM_ROCKET_COOLDOWN_TICKS, MAXIMUM_ROCKET_COOLDOWN_TICKS, roll);
	}

	private static int rangedTicks(final int minimum, final int maximum, final double roll) {
		double bounded = Double.isFinite(roll) ? Math.clamp(roll, 0.0, 1.0) : 0.0;
		return minimum + (int)Math.round((maximum - minimum) * bounded);
	}

	public enum Phase {
		IDLE,
		SEEKING_LAUNCH,
		LAUNCHING,
		CLIMBING,
		ORBITING,
		ARMING,
		DIVING,
		RECOVERING,
		LANDING
	}

	private record LaunchPlan(BlockPos site, @Nullable Path path) {
	}
}
