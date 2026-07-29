package com.wjz.mobsthinknow.ai.skeleton;

import com.wjz.mobsthinknow.ai.skeleton.SkeletonCombatMath.MovementMode;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.EnumSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;

/**
 * 普通骷髅专用弩战状态机：在射程外接近、射程内侧移、近身时保持瞄准拉扯，完成真实装填后
 * 再射出箭或副手里的爆炸烟花。贴脸全力逃跑仍由优先级 1 的独立 Goal 抢占。
 */
public final class SmartSkeletonCrossbowAttackGoal extends Goal {
	private static final double MOVE_SPEED = 1.0;
	private static final double MINIMUM_FIREWORK_DISTANCE_SQUARED = 36.0;

	private final AbstractSkeleton skeleton;
	private CrossbowState state = CrossbowState.UNCHARGED;
	private int attackDelay;
	private int repathCooldown;
	private int strafeDirection = 1;
	private int strafeSwitchTicks;

	public SmartSkeletonCrossbowAttackGoal(final AbstractSkeleton skeleton) {
		this.skeleton = skeleton;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = this.skeleton.getTarget();
		return target != null && target.isAlive() && this.skeleton.isHolding(Items.CROSSBOW);
	}

	@Override
	public boolean canContinueToUse() {
		return this.canUse();
	}

	@Override
	public void start() {
		ItemStack crossbow = crossbow();
		this.state = CrossbowItem.isCharged(crossbow) ? CrossbowState.CHARGED : CrossbowState.UNCHARGED;
		this.attackDelay = this.state == CrossbowState.CHARGED ? nextAttackDelay() : 0;
		this.repathCooldown = 0;
		this.strafeDirection = this.skeleton.getRandom().nextBoolean() ? 1 : -1;
		this.strafeSwitchTicks = nextStrafeSwitch();
		this.skeleton.setAggressive(true);
	}

	@Override
	public void stop() {
		if (this.skeleton.isUsingItem()) {
			this.skeleton.stopUsingItem();
		}
		this.skeleton.getNavigation().stop();
		this.skeleton.setAggressive(false);
		this.state = CrossbowState.UNCHARGED;
		this.attackDelay = 0;
	}

	@Override
	public void tick() {
		LivingEntity target = this.skeleton.getTarget();
		if (target == null || !target.isAlive()) {
			return;
		}

		MobsThinkNowConfig config = ConfigManager.get();
		if (SkeletonSquadOrders.obeyPreparationOrder(this.skeleton, target, MOVE_SPEED)) {
			if (this.state == CrossbowState.CHARGING) {
				this.state = CrossbowState.UNCHARGED;
			}
			return;
		}
		this.skeleton.setAggressive(true);
		int intelligence = SkeletonIntelligence.get(this.skeleton);
		double preferredRange = SkeletonCombatMath.intelligenceAdjustedPreferredRange(
			config.skeletonPreferredRange,
			intelligence
		);
		boolean hasLineOfSight = this.skeleton.getSensing().hasLineOfSight(target);
		double distanceSquared = this.skeleton.distanceToSqr(target);
		MovementMode movement = SkeletonCombatMath.chooseMovement(
			distanceSquared,
			hasLineOfSight,
			preferredRange,
			false
		);
		this.skeleton.getLookControl().setLookAt(target, 30.0F, 30.0F);
		this.moveForCombat(target, movement, intelligence, distanceSquared, preferredRange);
		this.tickCrossbow(target, hasLineOfSight, distanceSquared, intelligence);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	/** 仅供 GameTest 失败诊断，不参与状态机决策。 */
	public String diagnosticState() {
		ItemStack crossbow = crossbow();
		return "state=" + this.state
			+ ",using=" + this.skeleton.isUsingItem()
			+ ",useTicks=" + this.skeleton.getTicksUsingItem()
			+ ",charged=" + CrossbowItem.isCharged(crossbow)
			+ ",delay=" + this.attackDelay;
	}

	private void moveForCombat(
		final LivingEntity target,
		final MovementMode movement,
		final int intelligence,
		final double distanceSquared,
		final double preferredRange
	) {
		if (--this.strafeSwitchTicks <= 0) {
			if (this.skeleton.getRandom().nextFloat() < 0.45F) {
				this.strafeDirection = -this.strafeDirection;
			}
			this.strafeSwitchTicks = nextStrafeSwitch();
		}

		switch (movement) {
			case APPROACH -> {
				if (this.state == CrossbowState.UNCHARGED && this.repathCooldown-- <= 0) {
					this.repathCooldown = Math.max(5, 11 - intelligence / 2);
					this.skeleton.getNavigation().moveTo(target, MOVE_SPEED);
				}
			}
			case KITE -> {
				this.skeleton.getNavigation().stop();
				this.faceTarget(target);
				this.skeleton.getMoveControl().strafe(
					-SkeletonCombatMath.kiteBackwardInput(intelligence),
					SkeletonCombatMath.kiteSidewaysInput(intelligence) * this.strafeDirection
				);
			}
			case STRAFE, DODGE -> {
				this.skeleton.getNavigation().stop();
				this.faceTarget(target);
				double distance = Math.sqrt(Math.max(0.0, distanceSquared));
				float forward = distance < preferredRange * 0.85 ? -0.45F
					: distance > preferredRange * 1.10 ? 0.35F : 0.0F;
				this.skeleton.getMoveControl().strafe(forward, 0.52F * this.strafeDirection);
			}
		}
	}

	private void tickCrossbow(
		final LivingEntity target,
		final boolean hasLineOfSight,
		final double distanceSquared,
		final int intelligence
	) {
		ItemStack crossbow = crossbow();
		if (!(crossbow.getItem() instanceof CrossbowItem crossbowItem)) {
			return;
		}

		switch (this.state) {
			case UNCHARGED -> {
				if (hasLineOfSight && !this.skeleton.isUsingItem()) {
					this.skeleton.startUsingItem(ProjectileUtil.getWeaponHoldingHand(this.skeleton, Items.CROSSBOW));
					this.state = CrossbowState.CHARGING;
				}
			}
			case CHARGING -> {
				this.faceTarget(target);
				if (!this.skeleton.isUsingItem()) {
					this.state = CrossbowState.UNCHARGED;
					return;
				}
				// onUseTick 在蓄力计时到线后的下一次物品 tick 才写入 CHARGED_PROJECTILES；
				// 直接按 getTicksUsingItem() 松手会早一拍，形成永远装不上的循环。
				if (CrossbowItem.isCharged(crossbow)) {
					this.skeleton.releaseUsingItem();
					this.state = CrossbowState.CHARGED;
					this.attackDelay = nextAttackDelay();
				}
			}
			case CHARGED -> {
				this.faceTarget(target);
				if (this.attackDelay > 0) {
					this.attackDelay--;
					return;
				}
				ChargedProjectiles charged = crossbow.getOrDefault(
					DataComponents.CHARGED_PROJECTILES,
					ChargedProjectiles.EMPTY
				);
				boolean explosive = charged.contains(Items.FIREWORK_ROCKET);
				if (!hasLineOfSight || (explosive && distanceSquared < MINIMUM_FIREWORK_DISTANCE_SQUARED)) {
					return;
				}
				InteractionHand hand = ProjectileUtil.getWeaponHoldingHand(this.skeleton, Items.CROSSBOW);
				float power = explosive ? 1.6F : 3.15F;
				float uncertainty = Math.max(
					1.0F,
					14.0F - this.skeleton.level().getDifficulty().getId() * 4.0F - (intelligence - 1) * 0.30F
				);
				crossbowItem.performShooting(
					this.skeleton.level(),
					this.skeleton,
					hand,
					crossbow,
					power,
					uncertainty,
					target
				);
				SmartSkeletonMetrics.crossbowShot(explosive);
				this.state = CrossbowState.UNCHARGED;
				this.attackDelay = 10 + this.skeleton.getRandom().nextInt(9);
			}
		}
	}

	private ItemStack crossbow() {
		return this.skeleton.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this.skeleton, Items.CROSSBOW));
	}

	private void faceTarget(final LivingEntity target) {
		this.skeleton.getLookControl().setLookAt(target, 30.0F, 30.0F);
		this.skeleton.lookAt(target, 30.0F, 30.0F);
		this.skeleton.setYBodyRot(this.skeleton.getYRot());
		this.skeleton.setYHeadRot(this.skeleton.getYRot());
	}

	private int nextAttackDelay() {
		int intelligence = SkeletonIntelligence.get(this.skeleton);
		return Math.max(5, 16 - intelligence) + this.skeleton.getRandom().nextInt(8);
	}

	private int nextStrafeSwitch() {
		return 16 + this.skeleton.getRandom().nextInt(16);
	}

	private enum CrossbowState {
		UNCHARGED,
		CHARGING,
		CHARGED
	}
}
