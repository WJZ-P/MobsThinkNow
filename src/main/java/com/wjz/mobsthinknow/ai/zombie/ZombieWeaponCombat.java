package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.MobsThinkNow;
import com.wjz.mobsthinknow.ai.zombie.squad.WeaponClass;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import com.wjz.mobsthinknow.shared.ai.MeleeWeaponPlanner;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 剑、斧僵尸的近战节奏层。
 *
 * <p>原版 {@code MeleeAttackGoal} 对所有怪物都固定使用 20 tick 间隔，既不读取武器的
 * {@code ATTACK_SPEED}，冷却中也会持续向目标中心寻路。本类改为：</p>
 * <ul>
 *     <li>从主手物品属性计算玩家同口径的 {@code ceil(20 / attackSpeed)} 冷却；</li>
 *     <li>冷却期间维持约 2.8 格的环绕距离，而不是继续贴住目标碰撞箱；</li>
 *     <li>高智力剑士面对举盾目标时可执行零伤害佯攻，目标放盾后才提交真实攻击；</li>
 *     <li>斧在有空间时先建立起跳距离并执行 8 tick 可读前摇，只允许下落阶段命中并获得 1.5 倍暴击；
 *     地形持续不允许起跳时才退化为普通地面挥击，避免狭窄空间彻底失去攻击能力。</li>
 * </ul>
 *
 * <p>状态由服务器主线程独占，不扫描邻近实体；每只僵尸只维护常数大小的计时器。</p>
 */
final class ZombieWeaponCombat {
	private static final double PLAYER_BASE_ATTACK_SPEED = 4.0;
	private static final double SPACING_RADIUS = 2.8;
	private static final double SWORD_FEINT_MAXIMUM_DISTANCE_SQUARED = 4.2 * 4.2;
	private static final int SWORD_FEINT_COMMIT_TICK = 6;
	private static final int SWORD_FEINT_LUNGE_TICK = 3;
	private static final int SWORD_FEINT_BACKSTEP_TICK = 7;
	private static final int SWORD_FEINT_RETRY_TICKS = 24;
	private static final int SWORD_FEINT_FAILED_RECOVERY_TICKS = 6;
	private static final double SWORD_FEINT_LUNGE_SPEED = 0.12;
	private static final double SWORD_FEINT_BACKSTEP_SPEED = 0.18;
	private static final double AXE_LEAP_MINIMUM_DISTANCE_SQUARED = 1.8 * 1.8;
	private static final double AXE_LEAP_MAXIMUM_DISTANCE_SQUARED = 3.3 * 3.3;
	private static final double AXE_LEAP_MAXIMUM_VERTICAL_DIFFERENCE = 1.25;
	private static final double AXE_LEAP_HORIZONTAL_SPEED = 0.34;
	private static final int AXE_PREPARATION_TIMEOUT_TICKS = 30;
	private static final int AXE_WINDUP_TICKS = 8;
	private static final int AXE_LEAP_TIMEOUT_TICKS = 20;
	private static final int TAKEOFF_GRACE_TICKS = 3;
	private static final double DESCENDING_SPEED_THRESHOLD = -0.02;
	private static final Identifier AXE_CRITICAL_DAMAGE_ID = Identifier.fromNamespaceAndPath(
		MobsThinkNow.MOD_ID,
		"axe_leap_critical"
	);
	private static final AttributeModifier AXE_CRITICAL_DAMAGE = new AttributeModifier(
		AXE_CRITICAL_DAMAGE_ID,
		0.5,
		AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
	);

	private final Zombie zombie;
	private WeaponClass weaponClass = WeaponClass.NONE;
	private int targetId = Integer.MIN_VALUE;
	private long nextAttackAt;
	private long swordFeintStartedAt = Long.MIN_VALUE;
	private long nextSwordFeintAt;
	private long axePreparationDeadline = Long.MIN_VALUE;
	private long axeWindupStartedAt = Long.MIN_VALUE;
	private long axeLeapStartedAt = Long.MIN_VALUE;
	private long nextSpacingPathAt;
	private boolean clockwise;
	private boolean swordFeintLungeApplied;
	private boolean swordFeintBackstepApplied;

	ZombieWeaponCombat(final Zombie zombie) {
		this.zombie = zombie;
		this.clockwise = (zombie.getId() & 1) == 0;
	}

	/**
	 * 每 tick 在原版近战 Goal 之前调用。返回 {@code true} 表示本 tick 需要原版追击/命中流程；
	 * 返回 {@code false} 表示本类已经接管移动（通常是冷却周旋或斧手建立起跳距离）。
	 */
	boolean tick(final LivingEntity target, final MobsThinkNowConfig config) {
		WeaponClass currentWeapon = ZombieArmory.weaponClassOf(this.zombie.getMainHandItem());
		if (!config.enabled || !config.zombieAiEnabled || !config.weaponCombatTactics || !isHandledWeapon(currentWeapon)) {
			this.resetTransientState();
			this.weaponClass = currentWeapon;
			return true;
		}

		long now = this.zombie.level().getGameTime();
		if (this.weaponClass != currentWeapon || this.targetId != target.getId()) {
			this.clearOwnedBodyActions();
			this.weaponClass = currentWeapon;
			this.targetId = target.getId();
			this.swordFeintStartedAt = Long.MIN_VALUE;
			this.nextSwordFeintAt = now;
			this.axePreparationDeadline = Long.MIN_VALUE;
			this.axeWindupStartedAt = Long.MIN_VALUE;
			this.axeLeapStartedAt = Long.MIN_VALUE;
			this.nextSpacingPathAt = now;
		}

		// 不用目标墙后的实时坐标做周旋；失去视线后继续服从控制器的最后目击/小队命令。
		if (!this.zombie.getSensing().hasLineOfSight(target)) {
			this.cancelSwordFeint();
			this.cancelAxeWindup();
			return true;
		}

		if (now < this.nextAttackAt) {
			this.circleTarget(target, config, now, SPACING_RADIUS);
			this.zombie.setAggressive(false);
			return false;
		}

		if (this.weaponClass == WeaponClass.SWORD) {
			if (this.isSwordFeintActive()) {
				return this.tickSwordFeint(target, config, now);
			}
			if (this.shouldStartSwordFeint(target, config, now)) {
				this.beginSwordFeint(target, now);
				return false;
			}
			this.ensureReadyStrikeApproach(target, now);
			return true;
		}

		if (this.isAxeWindupActive()) {
			return this.tickAxeWindup(target, config, now);
		}

		if (this.isAxeLeapActive()) {
			if (this.shouldAbortLeap(now)) {
				ZombieBodyLanguage.stopPersistent(this.zombie, ZombieBodyAction.AXE_LEAP);
				this.axeLeapStartedAt = Long.MIN_VALUE;
			} else {
				this.guideLeap(target);
				return true;
			}
		}

		if (!this.canAttemptLeap(target)) {
			// 水中、骑乘或明显高低差环境不假装能跳劈，直接使用正常地面攻击。
			return true;
		}

		if (this.axePreparationDeadline == Long.MIN_VALUE) {
			this.axePreparationDeadline = now + AXE_PREPARATION_TIMEOUT_TICKS;
		}
		if (now >= this.axePreparationDeadline) {
			return true;
		}

		if (this.canStartLeap(target)) {
			this.beginAxeWindup(target, now);
			return false;
		}

		this.circleTarget(target, config, now, SPACING_RADIUS);
		this.zombie.setAggressive(false);
		return false;
	}

	boolean handlesCurrentWeapon(final MobsThinkNowConfig config) {
		WeaponClass currentWeapon = ZombieArmory.weaponClassOf(this.zombie.getMainHandItem());
		boolean handled = config.enabled
			&& config.zombieAiEnabled
			&& config.weaponCombatTactics
			&& isHandledWeapon(currentWeapon);
		if (handled) {
			// 盾卫守势会跳过常规武器 tick；查询冷却时也要同步当前武器分类。
			this.weaponClass = currentWeapon;
		}
		return handled;
	}

	boolean isAttackCooldownReady(final MobsThinkNowConfig config) {
		return this.handlesCurrentWeapon(config) && this.zombie.level().getGameTime() >= this.nextAttackAt;
	}

	/** 周旋路径刚好走完时仍保持 Goal 运行，避免退回原版 20 tick 的重新启动检查。 */
	boolean hasTacticalIntent(final MobsThinkNowConfig config) {
		return config.enabled
			&& config.zombieAiEnabled
			&& config.weaponCombatTactics
			&& isHandledWeapon(ZombieArmory.weaponClassOf(this.zombie.getMainHandItem()));
	}

	/** 自定义武器冷却取代原版固定 20 tick；斧手还必须处于跳劈下落窗或超时降级窗。 */
	boolean canPerformAttack(final LivingEntity target) {
		long now = this.zombie.level().getGameTime();
		if (now < this.nextAttackAt || this.isSwordFeintActive() || this.isAxeWindupActive()) {
			return false;
		}
		if (this.weaponClass != WeaponClass.AXE) {
			return true;
		}
		if (this.isCriticalLeapWindow()) {
			return true;
		}
		if (this.isAxeLeapActive()) {
			return false;
		}
		// 水中、骑乘中或目标高差过大时 tick() 会明确降级为普通攻击；不能再要求一个永远不会创建的跳劈准备窗口。
		if (!this.canAttemptLeap(target)) {
			return true;
		}
		return this.axePreparationDeadline != Long.MIN_VALUE
			&& now >= this.axePreparationDeadline;
	}

	boolean isCriticalLeapWindow() {
		return this.weaponClass == WeaponClass.AXE
			&& this.isAxeLeapActive()
			&& !this.zombie.onGround()
			&& this.zombie.getDeltaMovement().y < DESCENDING_SPEED_THRESHOLD;
	}

	/** 一次挥击无论被盾挡下还是被其他事件取消，都消耗对应武器的完整攻击冷却。 */
	void onAttackPerformed(final LivingEntity target) {
		long now = this.zombie.level().getGameTime();
		this.nextAttackAt = now + attackCooldownTicks(this.zombie.getMainHandItem());
		this.cancelSwordFeint();
		this.axePreparationDeadline = Long.MIN_VALUE;
		this.cancelAxeWindup();
		ZombieBodyLanguage.stopPersistent(this.zombie, ZombieBodyAction.AXE_LEAP);
		this.axeLeapStartedAt = Long.MIN_VALUE;
		this.nextSpacingPathAt = now;
		this.clockwise = !this.clockwise;
		this.startDisengage(target);
	}

	/**
	 * 复用原版 {@link Zombie#doHurtTarget} 的伤害源、附魔、耐久、击退和攻击后效果；
	 * 跳劈时只临时给攻击伤害加玩家暴击同款 1.5 倍，结算后立即移除。
	 */
	boolean performAttack(final LivingEntity target, final boolean critical) {
		if (!(this.zombie.level() instanceof ServerLevel serverLevel)) {
			return false;
		}

		AttributeInstance attackDamage = this.zombie.getAttribute(Attributes.ATTACK_DAMAGE);
		if (critical && attackDamage != null) {
			attackDamage.removeModifier(AXE_CRITICAL_DAMAGE_ID);
			attackDamage.addTransientModifier(AXE_CRITICAL_DAMAGE);
		}

		boolean hit;
		try {
			hit = this.zombie.doHurtTarget(serverLevel, target);
		} finally {
			if (critical && attackDamage != null) {
				attackDamage.removeModifier(AXE_CRITICAL_DAMAGE_ID);
			}
		}

		if (critical && hit) {
			serverLevel.playSound(
				null,
				target.getX(),
				target.getY(),
				target.getZ(),
				SoundEvents.PLAYER_ATTACK_CRIT,
				SoundSource.HOSTILE,
				1.0F,
				0.9F + this.zombie.getRandom().nextFloat() * 0.2F
			);
			serverLevel.sendParticles(
				ParticleTypes.CRIT,
				target.getX(),
				target.getY(0.5),
				target.getZ(),
				12,
				target.getBbWidth() * 0.25,
				target.getBbHeight() * 0.2,
				target.getBbWidth() * 0.25,
				0.2
			);
		}
		return hit;
	}

	void stop() {
		this.resetTransientState();
	}

	private void resetTransientState() {
		this.cancelSwordFeint();
		this.axePreparationDeadline = Long.MIN_VALUE;
		this.cancelAxeWindup();
		ZombieBodyLanguage.stopPersistent(this.zombie, ZombieBodyAction.AXE_LEAP);
		this.axeLeapStartedAt = Long.MIN_VALUE;
	}

	private boolean shouldStartSwordFeint(
		final LivingEntity target,
		final MobsThinkNowConfig config,
		final long now
	) {
		if (now < this.nextSwordFeintAt) {
			return false;
		}
		this.nextSwordFeintAt = now + SWORD_FEINT_RETRY_TICKS;
		return shouldStartSwordFeint(
			config.swordFeints,
			ZombieIntelligence.get(this.zombie),
			config.swordFeintMinimumIntelligence,
			target.isBlocking(),
			this.zombie.distanceToSqr(target),
			this.zombie.getRandom().nextDouble(),
			config.swordFeintChance
		);
	}

	private void beginSwordFeint(final LivingEntity target, final long now) {
		this.zombie.getNavigation().stop();
		this.zombie.stopUsingItem();
		this.zombie.setAggressive(false);
		this.zombie.getLookControl().setLookAt(target, 35.0F, 35.0F);
		this.swordFeintStartedAt = now;
		this.swordFeintLungeApplied = false;
		this.swordFeintBackstepApplied = false;
		ZombieBodyLanguage.play(this.zombie, ZombieBodyAction.SWORD_FEINT);
		SmartZombieMetrics.swordFeint();
	}

	/**
	 * 佯攻期间本层完全接管移动，且从不调用真实挥击。目标在可读前摇后主动放盾，才立刻把控制权
	 * 交回近战 Goal；目标坚持格挡则剑士后撤半步并进入短恢复，避免每 tick 重抽概率。
	 */
	private boolean tickSwordFeint(
		final LivingEntity target,
		final MobsThinkNowConfig config,
		final long now
	) {
		long elapsed = now - this.swordFeintStartedAt;
		if (!target.isAlive() || !this.zombie.getSensing().hasLineOfSight(target)) {
			this.cancelSwordFeint();
			return true;
		}

		this.zombie.getNavigation().stop();
		this.zombie.setAggressive(false);
		this.zombie.getLookControl().setLookAt(target, 35.0F, 35.0F);
		Vec3 towardTarget = horizontalUnit(target.position().subtract(this.zombie.position()));
		if (!this.swordFeintLungeApplied && elapsed >= SWORD_FEINT_LUNGE_TICK) {
			this.addHorizontalImpulse(towardTarget, SWORD_FEINT_LUNGE_SPEED);
			this.zombie.playSound(SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.65F, 0.90F);
			this.swordFeintLungeApplied = true;
		}
		if (!this.swordFeintBackstepApplied && elapsed >= SWORD_FEINT_BACKSTEP_TICK) {
			this.addHorizontalImpulse(towardTarget.scale(-1.0), SWORD_FEINT_BACKSTEP_SPEED);
			this.swordFeintBackstepApplied = true;
		}

		if (elapsed >= SWORD_FEINT_COMMIT_TICK && !target.isBlocking()) {
			this.cancelSwordFeint();
			this.zombie.setAggressive(true);
			this.ensureReadyStrikeApproach(target, now);
			return true;
		}
		if (elapsed < ZombieBodyAction.SWORD_FEINT.durationTicks()) {
			return false;
		}

		this.cancelSwordFeint();
		this.nextAttackAt = now + SWORD_FEINT_FAILED_RECOVERY_TICKS;
		this.circleTarget(target, config, now, SPACING_RADIUS);
		return false;
	}

	private boolean isSwordFeintActive() {
		return this.swordFeintStartedAt != Long.MIN_VALUE;
	}

	private void cancelSwordFeint() {
		ZombieBodyLanguage.stop(this.zombie, ZombieBodyAction.SWORD_FEINT);
		this.swordFeintStartedAt = Long.MIN_VALUE;
		this.swordFeintLungeApplied = false;
		this.swordFeintBackstepApplied = false;
	}

	private boolean isAxeWindupActive() {
		return this.axeWindupStartedAt != Long.MIN_VALUE;
	}

	private void beginAxeWindup(final LivingEntity target, final long now) {
		if (this.zombie.isUsingItem()) {
			this.zombie.stopUsingItem();
		}
		this.zombie.getNavigation().stop();
		this.zombie.setAggressive(false);
		this.zombie.getLookControl().setLookAt(target, 35.0F, 35.0F);
		this.axeWindupStartedAt = now;
		this.zombie.playSound(SoundEvents.ZOMBIE_STEP, 0.55F, 0.72F);
		ZombieBodyLanguage.play(this.zombie, ZombieBodyAction.AXE_WINDUP);
		SmartZombieMetrics.axeWindup();
	}

	/** @return 是否把控制权交回原版 Goal；真正起跳后允许其继续转向，但命中仍被下降窗口约束。 */
	private boolean tickAxeWindup(
		final LivingEntity target,
		final MobsThinkNowConfig config,
		final long now
	) {
		this.zombie.getNavigation().stop();
		this.zombie.setAggressive(false);
		this.zombie.getLookControl().setLookAt(target, 35.0F, 35.0F);
		if (now - this.axeWindupStartedAt < AXE_WINDUP_TICKS) {
			return false;
		}

		this.cancelAxeWindup();
		if (!this.canAttemptLeap(target) || !this.canStartLeap(target)) {
			if (now >= this.axePreparationDeadline) {
				return true;
			}
			this.circleTarget(target, config, now, SPACING_RADIUS);
			return false;
		}
		this.startLeap(target, now);
		return true;
	}

	private void cancelAxeWindup() {
		ZombieBodyLanguage.stop(this.zombie, ZombieBodyAction.AXE_WINDUP);
		this.axeWindupStartedAt = Long.MIN_VALUE;
	}

	private void clearOwnedBodyActions() {
		ZombieBodyLanguage.stop(this.zombie, ZombieBodyAction.SWORD_FEINT);
		ZombieBodyLanguage.stop(this.zombie, ZombieBodyAction.AXE_WINDUP);
		ZombieBodyLanguage.stopPersistent(this.zombie, ZombieBodyAction.AXE_LEAP);
	}

	private void addHorizontalImpulse(final Vec3 direction, final double speed) {
		Vec3 movement = this.zombie.getDeltaMovement();
		this.zombie.setDeltaMovement(
			movement.x * 0.35 + direction.x * speed,
			movement.y,
			movement.z * 0.35 + direction.z * speed
		);
	}

	/**
	 * 周旋路径可能恰好停在 2～3 格的圆弧节点；冷却完成后显式把目的地切回目标，避免原版导航把
	 * “旧路径已走完”误当成已经处于攻击距离。近距离直线 MoveControl 只在有视线时补最后半格。
	 */
	private void ensureReadyStrikeApproach(final LivingEntity target, final long now) {
		if (this.zombie.isWithinMeleeAttackRange(target)) {
			return;
		}
		if (this.zombie.getNavigation().isDone() || now >= this.nextSpacingPathAt) {
			this.zombie.getNavigation().moveTo(target, 1.15);
			this.nextSpacingPathAt = now + 4L;
		}
		if (this.zombie.distanceToSqr(target) <= 3.5 * 3.5
			&& this.zombie.getSensing().hasLineOfSight(target)) {
			this.zombie.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.15);
		}
	}

	private boolean isAxeLeapActive() {
		return this.axeLeapStartedAt != Long.MIN_VALUE;
	}

	private boolean shouldAbortLeap(final long now) {
		if (now - this.axeLeapStartedAt > AXE_LEAP_TIMEOUT_TICKS || this.zombie.isInWater() || this.zombie.isPassenger()) {
			return true;
		}
		return this.zombie.onGround() && now - this.axeLeapStartedAt > TAKEOFF_GRACE_TICKS;
	}

	private boolean canAttemptLeap(final LivingEntity target) {
		return this.zombie.onGround()
			&& !this.zombie.isInWater()
			&& !this.zombie.isPassenger()
			&& Math.abs(target.getY() - this.zombie.getY()) <= AXE_LEAP_MAXIMUM_VERTICAL_DIFFERENCE;
	}

	private boolean canStartLeap(final LivingEntity target) {
		if (!MeleeWeaponPlanner.isAxeLaunchBand(
			toShared(this.zombie.position()),
			toShared(target.position()),
			Math.sqrt(AXE_LEAP_MINIMUM_DISTANCE_SQUARED),
			Math.sqrt(AXE_LEAP_MAXIMUM_DISTANCE_SQUARED),
			AXE_LEAP_MAXIMUM_VERTICAL_DIFFERENCE
		)) {
			return false;
		}

		Vec3 direction = horizontalUnit(target.position().subtract(this.zombie.position()));
		AABB sweptJumpBox = this.zombie
			.getBoundingBox()
			.expandTowards(direction.x * 1.2, 1.1, direction.z * 1.2)
			.inflate(-0.05);
		return this.zombie.level().noCollision(this.zombie, sweptJumpBox);
	}

	private void startLeap(final LivingEntity target, final long now) {
		// 斧手跳劈前主动收起副手盾，避免一边格挡一边挥斧的视觉与规则冲突。
		if (this.zombie.isUsingItem()) {
			this.zombie.stopUsingItem();
		}
		this.zombie.getLookControl().setLookAt(target, 30.0F, 30.0F);
		this.zombie.getNavigation().moveTo(target, 1.15);
		this.zombie.getJumpControl().jump();

		Vec3 movement = this.zombie.getDeltaMovement();
		com.wjz.mobsthinknow.shared.math.Vec3d velocity = MeleeWeaponPlanner.axeLeapVelocity(
			toShared(this.zombie.position()),
			toShared(target.position()),
			movement.y,
			AXE_LEAP_HORIZONTAL_SPEED
		);
		this.zombie.setDeltaMovement(velocity.x(), velocity.y(), velocity.z());
		this.axeLeapStartedAt = now;
		ZombieBodyLanguage.startPersistent(this.zombie, ZombieBodyAction.AXE_LEAP);
	}

	/** 空中只做轻微航向修正，保留真正的抛物线和玩家可读的闪避窗口。 */
	private void guideLeap(final LivingEntity target) {
		this.zombie.getLookControl().setLookAt(target, 30.0F, 30.0F);
		if (this.zombie.onGround()) {
			return;
		}

		Vec3 movement = this.zombie.getDeltaMovement();
		com.wjz.mobsthinknow.shared.math.Vec3d guided = MeleeWeaponPlanner.guideAxeLeap(
			toShared(movement),
			toShared(this.zombie.position()),
			toShared(target.position()),
			AXE_LEAP_HORIZONTAL_SPEED,
			0.20
		);
		this.zombie.setDeltaMovement(guided.x(), guided.y(), guided.z());
	}

	/** 命中后立即后撤并带少量侧移，随后再由寻路接成圆弧，避免短 CD 武器仍黏在碰撞箱上。 */
	private void startDisengage(final LivingEntity target) {
		Vec3 away = horizontalUnit(this.zombie.position().subtract(target.position()));
		Vec3 lateral = this.clockwise
			? new Vec3(-away.z, 0.0, away.x)
			: new Vec3(away.z, 0.0, -away.x);
		Vec3 movement = this.zombie.getDeltaMovement();
		this.zombie.setDeltaMovement(
			movement.x * 0.35 + away.x * 0.24 + lateral.x * 0.10,
			movement.y,
			movement.z * 0.35 + away.z * 0.24 + lateral.z * 0.10
		);
	}

	/**
	 * 在目标周围选择下一段圆弧点。路径更新有 6～8 tick 的实体 ID 错峰，不会每 tick 重寻路。
	 */
	private void circleTarget(
		final LivingEntity target,
		final MobsThinkNowConfig config,
		final long now,
		final double radius
	) {
		this.zombie.getLookControl().setLookAt(target, 30.0F, 30.0F);
		if (now < this.nextSpacingPathAt) {
			return;
		}

		Vec3 destination = spacingDestination(this.zombie.position(), target.position(), radius, this.clockwise);
		boolean pathFound = this.zombie.getNavigation().moveTo(
			destination.x,
			destination.y,
			destination.z,
			Math.max(1.0, config.tacticalSpeedModifier)
		);
		if (!pathFound) {
			this.clockwise = !this.clockwise;
			destination = spacingDestination(this.zombie.position(), target.position(), radius, this.clockwise);
			this.zombie.getNavigation().moveTo(
				destination.x,
				destination.y,
				destination.z,
				Math.max(1.0, config.tacticalSpeedModifier)
			);
			SmartZombieMetrics.failedPath();
		}
		this.nextSpacingPathAt = now + 6L + Math.floorMod(this.zombie.getId(), 3);
	}

	static int attackCooldownTicks(final ItemStack stack) {
		ItemAttributeModifiers modifiers = stack.getOrDefault(
			DataComponents.ATTRIBUTE_MODIFIERS,
			ItemAttributeModifiers.EMPTY
		);
		boolean hasAttackSpeed = modifiers
			.modifiers()
			.stream()
			.anyMatch(entry -> entry.slot().test(EquipmentSlot.MAINHAND) && entry.attribute() == Attributes.ATTACK_SPEED);
		double attackSpeed = modifiers.compute(Attributes.ATTACK_SPEED, PLAYER_BASE_ATTACK_SPEED, EquipmentSlot.MAINHAND);
		return attackCooldownTicksFromSpeed(attackSpeed, hasAttackSpeed);
	}

	static int attackCooldownTicksFromSpeed(final double attackSpeed, final boolean hasAttackSpeedModifier) {
		return MeleeWeaponPlanner.attackCooldownTicks(attackSpeed, hasAttackSpeedModifier);
	}

	static Vec3 spacingDestination(
		final Vec3 zombiePosition,
		final Vec3 targetPosition,
		final double radius,
		final boolean clockwise
	) {
		com.wjz.mobsthinknow.shared.math.Vec3d result = MeleeWeaponPlanner.spacingDestination(
			toShared(zombiePosition),
			toShared(targetPosition),
			radius,
			clockwise
		);
		return new Vec3(result.x(), result.y(), result.z());
	}

	static double horizontalDistanceSquared(final Vec3 first, final Vec3 second) {
		return MeleeWeaponPlanner.horizontalDistanceSquared(toShared(first), toShared(second));
	}

	static boolean shouldStartSwordFeint(
		final boolean enabled,
		final int intelligence,
		final int minimumIntelligence,
		final boolean targetBlocking,
		final double distanceSquared,
		final double randomRoll,
		final double chance
	) {
		return enabled
			&& intelligence >= minimumIntelligence
			&& targetBlocking
			&& distanceSquared <= SWORD_FEINT_MAXIMUM_DISTANCE_SQUARED
			&& randomRoll >= 0.0
			&& randomRoll < chance;
	}

	private static boolean isHandledWeapon(final WeaponClass weaponClass) {
		return weaponClass == WeaponClass.SWORD || weaponClass == WeaponClass.AXE;
	}

	private static Vec3 horizontalUnit(final Vec3 vector) {
		Vec3 horizontal = new Vec3(vector.x, 0.0, vector.z);
		if (horizontal.horizontalDistanceSqr() < 1.0E-6) {
			return new Vec3(1.0, 0.0, 0.0);
		}
		return horizontal.normalize();
	}

	private static com.wjz.mobsthinknow.shared.math.Vec3d toShared(final Vec3 vector) {
		return new com.wjz.mobsthinknow.shared.math.Vec3d(vector.x, vector.y, vector.z);
	}
}
