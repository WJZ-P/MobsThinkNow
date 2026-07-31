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
import net.minecraft.world.level.levelgen.Heightmap;
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
	private static final int CLIMB_TIMEOUT_TICKS = 80;
	static final int MINIMUM_ORBIT_TICKS = 24;
	static final int MAXIMUM_ORBIT_TICKS = 40;
	/** 即使视线、距离或烟花计划异常，本轮盘旋也必须在 3.6 秒内交给蓄力阶段。 */
	static final int MAXIMUM_ORBIT_BEFORE_ARMING_TICKS = 72;
	static final int MINIMUM_ORBIT_ROCKETS = 1;
	static final int MAXIMUM_ORBIT_ROCKETS = 2;
	static final int MINIMUM_ORBIT_FIRST_ROCKET_DELAY_TICKS = 10;
	static final int MAXIMUM_ORBIT_FIRST_ROCKET_DELAY_TICKS = 18;
	static final int MINIMUM_ORBIT_ROCKET_GAP_TICKS = 32;
	static final int MAXIMUM_ORBIT_ROCKET_GAP_TICKS = 44;
	private static final int MINIMUM_ROCKET_COOLDOWN_TICKS = 28;
	private static final int MAXIMUM_ROCKET_COOLDOWN_TICKS = 40;
	static final int MAXIMUM_DIVE_ACCELERATION_WAIT_TICKS = 30;
	/** 包含最坏盘旋、原版长矛准备和补推等待，再留 20 tick 调度余量。 */
	static final int MAXIMUM_ORBIT_TO_DIVE_TICKS = MAXIMUM_ORBIT_BEFORE_ARMING_TICKS
		+ MAXIMUM_DIVE_ACCELERATION_WAIT_TICKS + 20;
	private static final int DIVE_TIMEOUT_TICKS = 80;
	private static final int RECOVERY_TIMEOUT_TICKS = 100;
	private static final int GROUNDED_POSE_SETTLE_TICKS = 20;
	private static final int LANDING_GROUND_SCAN_DEPTH = 32;
	private static final double LAUNCH_MOVE_SPEED = 1.10;
	private static final double LAUNCH_REACHED_SQUARED = 0.85 * 0.85;
	private static final double DESIRED_ALTITUDE = 6.5;
	private static final double ORBIT_RADIUS = 10.0;
	private static final double ORBIT_ANGULAR_SPEED = 0.045;
	private static final double ORBIT_LOOK_AHEAD = 0.42;
	private static final double ATTACK_STAGING_DISTANCE = 10.0;
	private static final double DIVE_BOOST_DISTANCE_SQUARED = 8.0 * 8.0;
	private static final double DIVE_REBOOST_SPEED_SQUARED = 1.0;
	static final double MINIMUM_DIVE_ENTRY_SPEED_SQUARED = 0.62 * 0.62;
	private static final double POST_ATTACK_CLEAR_DISTANCE_SQUARED = 6.0 * 6.0;
	private static final double RECOVERY_EMERGENCY_SPEED_SQUARED = 0.55 * 0.55;
	private static final double MAXIMUM_LANDING_HORIZONTAL_SPEED = 0.62;
	private static final double LANDING_HORIZONTAL_BRAKE_FACTOR = 0.94;
	private static final double LANDING_DESCENT_SPEED = -0.12;
	private static final double MAXIMUM_LANDING_DESCENT_SPEED = -0.24;
	private static final double TAKEOFF_HORIZONTAL_DAMPING = 0.12;
	private static final float TAKEOFF_PITCH_DEGREES = -75.0F;
	private static final double[] LANDING_SEARCH_DISTANCES = {6.0, 10.0, 14.0, 0.0};
	private static final double[] LANDING_SEARCH_YAW_OFFSETS = {0.0, -35.0, 35.0};

	private final Zombie zombie;
	private Phase phase = Phase.IDLE;
	private @Nullable LivingEntity target;
	private @Nullable BlockPos launchSite;
	private @Nullable Vec3 approachDirection;
	private @Nullable Vec3 diveExitDirection;
	/** 失去目标时一次性冻结的绝对落点；不能每 tick 跟着僵尸向前移动。 */
	private @Nullable Vec3 landingPoint;
	private @Nullable Vec3 landingDirection;
	private boolean landingPointHasSupport;
	private long phaseStartedAt;
	private long nextLaunchSearchAt;
	private long nextPathRefreshAt;
	private long nextRocketAt;
	private long nextOrbitRocketAt;
	private long attackAllowedAt;
	private long orbitDeadline;
	private long spearReadyAt;
	/** 抵消落地碰撞后 onGround 在相邻物理拍之间抖动；有新目标正式起飞时清零。 */
	private long groundedPoseUntil;
	private int orbitRocketBudget;
	private int orbitRocketsLaunched;
	private double orbitAngle;
	private double orbitDirection;
	private double closestDiveDistanceSquared = Double.MAX_VALUE;
	private boolean diveHitConfirmed;
	private boolean armingBoostLaunched;
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

		boolean touchingGround = this.isLandingTouchdown();
		boolean staleFlyingPose = this.zombie.isFallFlying() || this.zombie.hasPose(Pose.FALL_FLYING);
		if (touchingGround && staleFlyingPose) {
			// 清理读档、碰撞顺序或旧版本遗留的落地滑翔位；重复 canUse 不再把该位先置真再置假。
			this.stopGliding();
			this.latchGroundedPose();
		} else if (touchingGround && this.isGroundedPoseSettling()) {
			this.stabilizeGroundedPose();
		}
		LivingEntity currentTarget = validTarget(this.zombie.getTarget());
		if (currentTarget != null && ZombieAirAssault.isFlightReady(this.zombie, config)) {
			this.target = currentTarget;
			return true;
		}
		// 只接管已经真实进入滑翔的无目标实体。持鞘翅僵尸走台阶或普通跳跃时也会短暂
		// onGround=false；旧判断把这种一个 tick 的离地误认成飞行，导致客户端姿态反复横置/站立。
		return !this.isGroundedPoseSettling() && !touchingGround && this.zombie.isFallFlying();
	}

	@Override
	public boolean canContinueToUse() {
		MobsThinkNowConfig config = ConfigManager.get();
		if (!ZombieAirAssault.isEnabled(config)
			|| !ZombieAirAssault.isAirAssaultLoadout(this.zombie)
			|| !this.zombie.isAlive()) {
			return false;
		}

		LivingEntity currentTarget = validTarget(this.zombie.getTarget());
		if (currentTarget == null && this.isGroundedPoseSettling()) {
			return false;
		}
		if (currentTarget == null && this.isLandingTouchdown()) {
			this.latchGroundedPose();
			return false;
		}
		if (!this.zombie.onGround() || this.zombie.isFallFlying()) {
			return true;
		}
		return currentTarget != null
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
		if (this.target != null && ZombieAirAssault.isFlightReady(this.zombie, config)) {
			this.groundedPoseUntil = 0L;
		}
		if (this.target == null || !ZombieAirAssault.isFlightReady(this.zombie, config)) {
			this.enterLanding();
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
		if (this.target == null && this.isGroundedPoseSettling()) {
			this.stopGliding();
			this.enterPhase(Phase.IDLE);
			return;
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
		boolean landed = this.isLandingTouchdown();
		this.zombie.getNavigation().stop();
		this.zombie.stopUsingItem();
		this.zombie.setAggressive(false);
		// Goal 被配置热切换、目标规则或其他高优先级 Goal 中断时，也必须交还
		// 原版重力控制；不能把已经失去控制器的僵尸留在滑翔标志中。
		this.stopGliding();
		if (landed) {
			this.latchGroundedPose();
		}
		this.enterPhase(Phase.IDLE);
		this.target = null;
		this.launchSite = null;
		this.approachDirection = null;
		this.diveExitDirection = null;
		this.landingPoint = null;
		this.landingDirection = null;
		this.landingPointHasSupport = false;
		this.attackAllowedAt = 0L;
		this.orbitDeadline = 0L;
		this.nextOrbitRocketAt = 0L;
		this.orbitRocketBudget = 0;
		this.orbitRocketsLaunched = 0;
		this.diveHitConfirmed = false;
		this.armingBoostLaunched = false;
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
		Vec3 movement = this.zombie.getDeltaMovement();
		this.zombie.setDeltaMovement(
			movement.x * TAKEOFF_HORIZONTAL_DAMPING,
			movement.y,
			movement.z * TAKEOFF_HORIZONTAL_DAMPING
		);
		this.enterPhase(Phase.LAUNCHING);
		// 首枚烟花只负责拔高：不再朝目标注入水平速度，也不经过普通 6°/tick 的平滑俯仰。
		this.aimForVerticalTakeoff();
		this.zombie.getJumpControl().jump();
	}

	private void tickLaunching() {
		this.aimForVerticalTakeoff();
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
		// FireworkRocketEntity 每 tick 都读取载体的 look vector；发射前必须直接锁到近垂直方向，
		// 否则普通平滑转向会让第一枚火箭沿地面把僵尸带走。
		this.aimForVerticalTakeoff();
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
		Vec3 climbPoint = this.verticalClimbPoint();
		this.aimAt(climbPoint);
		this.redirectVelocityToward(climbPoint, 5.0);

		double altitude = this.zombie.getY() - this.target.getY();
		if (altitude >= DESIRED_ALTITUDE - 1.0) {
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
	 * 每轮攻击前只做一段短盘旋：随机等待 1.2～2 秒，并制定至多 1～2 枚烟花的推进计划。
	 * 第二枚烟花之间仍保留 1.6～2.2 秒惯性窗口；若视线、距离或推进计划异常，3.6 秒硬截止
	 * 会直接交给蓄力阶段，杜绝在目标上空永久绕圈。
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
		this.orbitDeadline = now + MAXIMUM_ORBIT_BEFORE_ARMING_TICKS;
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
		this.redirectVelocityToward(orbitPoint, 5.0);
		if (this.zombie.horizontalCollision) {
			this.orbitDirection = -this.orbitDirection;
			this.beginRecovery();
			return;
		}

		long now = this.now();
		this.tickOrbitRocketPlan(now);
		/*
		 * 公共烟花冷却在极端航线中可能一直压到硬截止的最后一拍。若本轮仍一枚都没发，
		 * 提前一拍开放最低预算并保留这一帧 ORBITING：玩家能看见最后一次助推，监控端也不会
		 * 在同一服务端 tick 的“发射后立刻切 ARMING”里漏掉这次事件。下一拍仍严格执行硬截止。
		 */
		if (now >= this.orbitDeadline - 1L
			&& this.orbitRocketsLaunched == 0
			&& this.availableRocketCount() > 0) {
			this.nextRocketAt = Math.min(this.nextRocketAt, now);
			this.nextOrbitRocketAt = Math.min(this.nextOrbitRocketAt, now);
			this.tickOrbitRocketPlan(now);
			if (this.orbitRocketsLaunched > 0) {
				return;
			}
		}
		boolean hasLineOfSight = this.zombie.getSensing().hasLineOfSight(this.target);
		if (shouldBeginArming(
			now,
			this.attackAllowedAt,
			this.orbitDeadline,
			this.isOrbitRocketPlanComplete(),
			hasLineOfSight
		)) {
			this.beginArming();
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
		this.armingBoostLaunched = false;
		// 冻结本次突击的进场方向；蓄力期间保持在目标外圈，不再沿盘旋点继续漂移。
		this.approachDirection = horizontalUnitOrFallback(this.zombie.position().subtract(this.target.position()));
		this.zombie.startUsingItem(InteractionHand.MAIN_HAND);
		KineticWeapon kineticWeapon = this.zombie.getMainHandItem().get(DataComponents.KINETIC_WEAPON);
		if (kineticWeapon != null) {
			kineticWeapon.makeSound(this.zombie);
		}
		this.spearReadyAt = this.now() + (kineticWeapon == null ? 0L : kineticWeapon.delayTicks()) + 1L;
		if (ZombieAirAssault.hasRockets(this.zombie)) {
			// 攻击助推和盘旋维持不是同一用途：最迟在长矛就绪后 4 tick 开放一次补推，
			// 避免刚用过盘旋烟花后还要悬停等待整段公共冷却。
			this.nextRocketAt = Math.min(this.nextRocketAt, this.spearReadyAt + 4L);
		}
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
		// 蓄力期间位置仍留在攻击圈外，但机头提前平滑转向目标；这样进入俯冲时无需瞬间折转 90°。
		Vec3 attackAim = this.predictedTargetPoint();
		this.aimAt(attackAim);
		if (!this.zombie.isUsingItem() || !this.zombie.getUseItem().has(DataComponents.KINETIC_WEAPON)) {
			// 装备被其他 Mod 临时改动时重新计时，避免跳过原版长矛的准备窗口。
			this.beginArming();
			return;
		}
		if (this.zombie.horizontalCollision) {
			this.beginRecovery();
			return;
		}
		long now = this.now();
		if (now < this.spearReadyAt) {
			this.redirectVelocityToward(holdingPoint, 6.0);
			return;
		}

		double speedSquared = this.zombie.getDeltaMovement().lengthSqr();
		if (speedSquared >= MINIMUM_DIVE_ENTRY_SPEED_SQUARED) {
			this.beginDive();
			return;
		}
		if (!ZombieAirAssault.hasRockets(this.zombie)) {
			this.enterLanding();
			return;
		}
		// 盘旋末尾的烟花仍可能占用公共冷却。低速时不立即切入 DIVING，
		// 而是在攻击圈外保持举矛并只补推一次；烟花真正把速度拉起来后才正常放行俯冲。
		if (!this.armingBoostLaunched && now >= this.nextRocketAt) {
			this.armingBoostLaunched = this.launchRocket();
		}
		// 补推前继续守住外圈；补推后则让速度与机头共同朝攻击线收敛，避免两套控制互相抵消动能。
		Vec3 steeringPoint = this.armingBoostLaunched ? attackAim : holdingPoint;
		this.redirectVelocityToward(steeringPoint, this.armingBoostLaunched ? 4.0 : 6.0);
		if (now - this.spearReadyAt >= MAXIMUM_DIVE_ACCELERATION_WAIT_TICKS) {
			// 补推实体异常或复杂地形不能再把状态机送回盘旋形成闭环；到点必须执行本次突击。
			this.beginDive();
		}
	}

	private void beginDive() {
		this.zombie.setAggressive(true);
		this.aimAt(this.predictedTargetPoint());
		this.closestDiveDistanceSquared = this.zombie.distanceToSqr(this.target);
		this.diveHitConfirmed = false;
		this.diveExitDirection = null;
		statusAccess(this.zombie).mobsthinknow$recordDiveStart();
		this.enterPhase(Phase.DIVING);
		// 恢复首版的攻击助推：盘旋烟花只负责维持航线，真正进入远距离俯冲时再补一枚动能。
		// 若刚在盘旋末尾发射过烟花，公共冷却会阻止重复消耗，而仍在附着的烟花会继续提供推力。
		if (this.closestDiveDistanceSquared >= DIVE_BOOST_DISTANCE_SQUARED
			&& this.now() >= this.nextRocketAt) {
			this.launchRocket();
		}
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
		Vec3 aimPoint;
		if (this.diveHitConfirmed && this.diveExitDirection != null) {
			aimPoint = this.zombie.getEyePosition().add(this.diveExitDirection.scale(16.0));
			// 命中后保持穿出方向，不再把速度重新拽回目标中心，否则会绕着目标打转而永远拉不开六格。
			this.aimAt(aimPoint);
		} else {
			aimPoint = this.predictedTargetPoint();
			this.aimAt(aimPoint);
			this.redirectVelocityToward(aimPoint, 9.0);
		}
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
			Vec3 movement = this.zombie.getDeltaMovement();
			this.diveExitDirection = movement.lengthSqr() >= 1.0E-8
				? movement.normalize()
				: this.predictedTargetPoint().subtract(this.zombie.getEyePosition()).normalize();
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
		// 首版同款远距离低速补推，避免 0.5 倍烟花在真正接敌前已经耗尽速度。
		if (ZombieAirAssault.hasRockets(this.zombie)
			&& distanceSquared >= DIVE_BOOST_DISTANCE_SQUARED
			&& this.now() >= this.nextRocketAt
			&& this.zombie.getDeltaMovement().lengthSqr() < DIVE_REBOOST_SPEED_SQUARED) {
			this.launchRocket();
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
		this.redirectVelocityToward(recoveryPoint, 6.0);

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
		this.landingDirection = this.chooseLandingDirection();
		this.landingPoint = this.findLandingPoint(this.serverLevel(), this.landingDirection);
		this.landingPointHasSupport = this.landingPoint != null;
		if (this.landingPoint == null) {
			// 极深峡谷或虚空上方也使用固定的绝对下降点；重点是不能继续追逐一个随自身移动的假落点。
			this.landingPoint = this.zombie.position()
				.add(this.landingDirection.scale(10.0))
				.add(0.0, -6.0, 0.0);
		}
		this.enterPhase(Phase.LANDING);
	}

	private void tickLanding() {
		this.stopUsingSpear();
		this.zombie.setAggressive(false);
		if (this.isLandingTouchdown()) {
			this.finishLanding();
			return;
		}

		if (ZombieAirAssault.hasUsableGlider(this.zombie)) {
			this.ensureGliding();
			if (!this.landingPointHasSupport
				&& this.landingPoint != null
				&& (this.zombie.getY() <= this.landingPoint.y + 1.5
					|| horizontalDistanceSquared(this.zombie.position(), this.landingPoint) <= 1.5 * 1.5)) {
				// 虚空或整片液面没有合法落点时仍持续下降，而不是抵达首个临时点后原地盘旋。
				Vec3 direction = this.landingDirection == null
					? this.horizontalUnitOrFallback(Vec3.ZERO)
					: this.landingDirection;
				this.landingPoint = this.landingPoint.add(direction.scale(4.0)).add(0.0, -6.0, 0.0);
			}
			Vec3 landingAim = this.landingPoint == null
				? this.zombie.position().add(0.0, -6.0, 0.0)
				: this.landingPoint.add(0.0, 0.6, 0.0);
			this.aimAt(landingAim);
			this.redirectVelocityToward(landingAim, 3.5);
			this.applyLandingBrakes();
		}
	}

	private Vec3 chooseLandingDirection() {
		Vec3 direction = horizontalUnit(this.zombie.getDeltaMovement());
		if (direction.lengthSqr() < 1.0E-8) {
			direction = horizontalUnit(this.zombie.getLookAngle());
		}
		if (direction.lengthSqr() < 1.0E-8 && this.target != null) {
			direction = horizontalUnit(this.zombie.position().subtract(this.target.position()));
		}
		return direction.lengthSqr() < 1.0E-8 ? this.horizontalUnitOrFallback(Vec3.ZERO) : direction;
	}

	/**
	 * 在当前高度向前方扇区做一次有界落点搜索。候选点必须有真实承重面、两格净空且位于已加载区块，
	 * 因而丢失目标不会触发区块加载，也不会把栅栏门或水面误当跑道。
	 */
	private @Nullable Vec3 findLandingPoint(final ServerLevel level, final Vec3 forward) {
		int startY = Mth.clamp(Mth.floor(this.zombie.getY()) + 1, level.getMinY() + 1, level.getMaxY() - 2);
		int minimumY = Math.max(level.getMinY() + 1, startY - LANDING_GROUND_SCAN_DEPTH);
		for (double distance : LANDING_SEARCH_DISTANCES) {
			for (double yawOffset : LANDING_SEARCH_YAW_OFFSETS) {
				if (distance == 0.0 && yawOffset != 0.0) {
					continue;
				}
				Vec3 direction = rotateHorizontal(forward, yawOffset);
				int x = Mth.floor(this.zombie.getX() + direction.x * distance);
				int z = Mth.floor(this.zombie.getZ() + direction.z * distance);
				if (!level.getChunkSource().hasChunk(
					SectionPos.blockToSectionCoord(x),
					SectionPos.blockToSectionCoord(z)
				)) {
					continue;
				}
				for (int y = startY; y >= minimumY; y--) {
					BlockPos feet = new BlockPos(x, y, z);
					if (ZombieTraversalRules.canStandAt(level, feet)) {
						return Vec3.atBottomCenterOf(feet);
					}
				}
				// 高空丢失目标时无需逐格扫描整根世界柱：高度图在已加载区块内 O(1) 给出地表脚部高度。
				int surfaceFeetY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				if (surfaceFeetY < minimumY && surfaceFeetY >= level.getMinY() + 1) {
					BlockPos surfaceFeet = new BlockPos(x, surfaceFeetY, z);
					if (ZombieTraversalRules.canStandAt(level, surfaceFeet)) {
						return Vec3.atBottomCenterOf(surfaceFeet);
					}
				}
			}
		}
		return null;
	}

	/** 滑翔减速而不是瞬间刹停：限制过高水平速度，并把垂直速度逐步收敛到温和下降。 */
	private void applyLandingBrakes() {
		Vec3 movement = this.zombie.getDeltaMovement();
		double horizontalSpeed = movement.horizontalDistance();
		double brakedHorizontalSpeed = horizontalSpeed > MAXIMUM_LANDING_HORIZONTAL_SPEED
			? Math.max(MAXIMUM_LANDING_HORIZONTAL_SPEED, horizontalSpeed * LANDING_HORIZONTAL_BRAKE_FACTOR)
			: horizontalSpeed;
		double horizontalScale = horizontalSpeed > 1.0E-8 ? brakedHorizontalSpeed / horizontalSpeed : 1.0;
		double vertical = Mth.lerp(0.12, movement.y, LANDING_DESCENT_SPEED);
		vertical = Mth.clamp(vertical, MAXIMUM_LANDING_DESCENT_SPEED, 0.04);
		this.zombie.setDeltaMovement(
			movement.x * horizontalScale,
			vertical,
			movement.z * horizontalScale
		);
	}

	private void finishLanding() {
		this.stopGliding();
		this.latchGroundedPose();
		this.landingPoint = null;
		this.landingDirection = null;
		this.landingPointHasSupport = false;
		this.enterPhase(Phase.IDLE);
	}

	private boolean isLandingTouchdown() {
		return this.zombie.onGround() || this.zombie.verticalCollisionBelow;
	}

	private boolean isGroundedPoseSettling() {
		return this.now() < this.groundedPoseUntil;
	}

	private void latchGroundedPose() {
		this.groundedPoseUntil = Math.max(this.groundedPoseUntil, this.now() + GROUNDED_POSE_SETTLE_TICKS);
		this.stabilizeGroundedPose();
	}

	private void stabilizeGroundedPose() {
		this.zombie.setOnGround(true);
		Vec3 movement = this.zombie.getDeltaMovement();
		this.zombie.setDeltaMovement(movement.x * 0.20, 0.0, movement.z * 0.20);
	}

	private Vec3 verticalClimbPoint() {
		// 点始终冻结在当前实体正上方，因此第一枚附着烟花不会被目标的水平位置带偏。
		return this.zombie.getEyePosition().add(0.0, 16.0, 0.0);
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
		if (this.phase == Phase.LANDING && this.isLandingTouchdown()) {
			return;
		}
		if (!this.zombie.isFallFlying() && !this.zombie.onGround() && ZombieAirAssault.hasUsableGlider(this.zombie)) {
			flightAccess(this.zombie).mobsthinknow$startFallFlying();
		}
		if (this.zombie.isFallFlying() && !this.zombie.hasPose(Pose.FALL_FLYING)) {
			this.zombie.setPose(Pose.FALL_FLYING);
		}
	}

	private void stopGliding() {
		if (this.zombie.isFallFlying() || this.zombie.hasPose(Pose.FALL_FLYING)) {
			flightAccess(this.zombie).mobsthinknow$stopFallFlying();
		}
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

	/**
	 * 首次起飞是刻意的“近垂直拔高”，这里直接写入 -75° 俯仰而不走普通平滑器。
	 * 烟花实体会立刻使用 {@link Zombie#getLookAngle()} 计算助推方向，所以先发射再慢慢抬头已经太晚。
	 */
	private void aimForVerticalTakeoff() {
		float yaw = this.zombie.getYRot();
		this.zombie.setXRot(TAKEOFF_PITCH_DEGREES);
		this.zombie.setYHeadRot(yaw);
		this.zombie.setYBodyRot(yaw);
		Vec3 lookTarget = this.zombie.getEyePosition()
			.add(this.zombie.calculateViewVector(TAKEOFF_PITCH_DEGREES, yaw).scale(16.0));
		this.zombie.getLookControl().setLookAt(
			lookTarget.x,
			lookTarget.y,
			lookTarget.z,
			180.0F,
			180.0F
		);
	}

	private void aimAt(final Vec3 point) {
		Vec3 delta = point.subtract(this.zombie.getEyePosition());
		double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		if (delta.lengthSqr() < 1.0E-8) {
			return;
		}
		float desiredYaw = Mth.wrapDegrees((float)(Mth.atan2(delta.z, delta.x) * 180.0 / Math.PI) - 90.0F);
		float desiredPitch = Mth.clamp(
			(float)(-(Mth.atan2(delta.y, horizontal) * 180.0 / Math.PI)),
			-75.0F,
			this.phase == Phase.LANDING ? 22.0F : 75.0F
		);
		float yawStep = this.maximumAimYawStep();
		float pitchStep = this.maximumAimPitchStep();
		float nextYaw = approachRotation(this.zombie.getYRot(), desiredYaw, yawStep);
		float nextPitch = Mth.approach(this.zombie.getXRot(), desiredPitch, pitchStep);
		this.zombie.setYRot(nextYaw);
		this.zombie.setYHeadRot(approachRotation(this.zombie.getYHeadRot(), nextYaw, yawStep + 2.0F));
		this.zombie.setYBodyRot(approachRotation(this.zombie.yBodyRot, nextYaw, yawStep));
		this.zombie.setXRot(nextPitch);

		// LookControl 会在 Goal 之后运行并先重置俯仰角；把它的目标设为“本 tick 的中间朝向”，
		// 而不是最终目标，防止控制器在同一 tick 又把平滑结果瞬间掰回去。
		Vec3 intermediateLook = this.zombie.getEyePosition()
			.add(this.zombie.calculateViewVector(nextPitch, nextYaw).scale(16.0));
		this.zombie.getLookControl().setLookAt(
			intermediateLook.x,
			intermediateLook.y,
			intermediateLook.z,
			180.0F,
			180.0F
		);
	}

	private float maximumAimYawStep() {
		return switch (this.phase) {
			case ORBITING -> 5.0F;
			case ARMING, RECOVERING -> 6.0F;
			case DIVING -> 9.0F;
			case LANDING -> 4.0F;
			default -> 8.0F;
		};
	}

	private float maximumAimPitchStep() {
		return switch (this.phase) {
			case ORBITING, LANDING -> 3.5F;
			case ARMING, RECOVERING -> 5.0F;
			case DIVING -> 7.0F;
			default -> 6.0F;
		};
	}

	static float approachRotation(final float current, final float target, final float maximumStep) {
		return Mth.approachDegrees(current, target, Math.max(0.0F, maximumStep));
	}

	/** 只限角改变当前速度方向，不凭空增加速度；真正的能量仍来自下落与烟花。 */
	private void redirectVelocityToward(final Vec3 point, final double maximumTurnDegrees) {
		Vec3 movement = this.zombie.getDeltaMovement();
		double speed = movement.length();
		if (speed < 0.08) {
			return;
		}
		Vec3 desired = point.subtract(this.zombie.getEyePosition());
		if (desired.lengthSqr() < 1.0E-8) {
			return;
		}
		Vec3 redirected = turnDirectionToward(movement, desired, maximumTurnDegrees);
		if (redirected.lengthSqr() > 1.0E-8) {
			this.zombie.setDeltaMovement(redirected.normalize().scale(speed));
		}
	}

	/** 球面限角转向；即使目标方向相反，也不会因线性插值接近零向量而突然翻面。 */
	static Vec3 turnDirectionToward(final Vec3 current, final Vec3 desired, final double maximumTurnDegrees) {
		if (current.lengthSqr() < 1.0E-12 || desired.lengthSqr() < 1.0E-12) {
			return Vec3.ZERO;
		}
		Vec3 from = current.normalize();
		Vec3 to = desired.normalize();
		double dot = Mth.clamp(from.dot(to), -1.0, 1.0);
		double angle = Math.acos(dot);
		double maximumTurn = Math.toRadians(Math.max(0.0, maximumTurnDegrees));
		if (angle <= maximumTurn || angle < 1.0E-7) {
			return to;
		}
		if (maximumTurn <= 0.0) {
			return from;
		}

		Vec3 axis = from.cross(to);
		if (axis.lengthSqr() < 1.0E-10) {
			axis = from.cross(new Vec3(0.0, 1.0, 0.0));
			if (axis.lengthSqr() < 1.0E-10) {
				axis = from.cross(new Vec3(1.0, 0.0, 0.0));
			}
		}
		axis = axis.normalize();
		double cosine = Math.cos(maximumTurn);
		double sine = Math.sin(maximumTurn);
		Vec3 turned = from.scale(cosine)
			.add(axis.cross(from).scale(sine))
			.add(axis.scale(axis.dot(from) * (1.0 - cosine)));
		return turned.normalize();
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

	private static Vec3 rotateHorizontal(final Vec3 vector, final double degrees) {
		double radians = Math.toRadians(degrees);
		double cosine = Math.cos(radians);
		double sine = Math.sin(radians);
		return horizontalUnit(new Vec3(
			vector.x * cosine - vector.z * sine,
			0.0,
			vector.x * sine + vector.z * cosine
		));
	}

	private static double horizontalDistanceSquared(final Vec3 first, final Vec3 second) {
		double x = first.x - second.x;
		double z = first.z - second.z;
		return x * x + z * z;
	}

	/** 正常条件满足即可蓄力；硬截止到达时不得再因视线或未完成的第二枚烟花永久绕圈。 */
	static boolean shouldBeginArming(
		final long now,
		final long attackAllowedAt,
		final long hardDeadline,
		final boolean rocketPlanComplete,
		final boolean hasLineOfSight
	) {
		return now >= hardDeadline
			|| now >= attackAllowedAt && rocketPlanComplete && hasLineOfSight;
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
