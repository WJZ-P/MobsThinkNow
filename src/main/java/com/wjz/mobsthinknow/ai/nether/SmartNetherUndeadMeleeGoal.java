package com.wjz.mobsthinknow.ai.nether;

import com.wjz.mobsthinknow.config.ConfigManager;
import java.util.EnumSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 僵尸猪灵剑士与凋灵骷髅近战职业共用的可读战斗状态机。
 *
 * <p>原版近战 Goal 会在攻击冷却期间持续贴住碰撞箱。本 Goal 让决斗型职业命中后保持看向目标、
 * 侧后撤并重新找角度；狂战/收割者则会在中距离先停步蓄力，再进行一次有声效的短突进。每只实体
 * 只读取自身目标并按固定间隔重算路径，不查询附近实体，因此大量生成时仍是 O(1) 单体决策。</p>
 */
public final class SmartNetherUndeadMeleeGoal extends net.minecraft.world.entity.ai.goal.Goal {
	private static final double LUNGE_MINIMUM_DISTANCE_SQUARED = 3.2 * 3.2;
	private static final double LUNGE_MAXIMUM_DISTANCE_SQUARED = 7.0 * 7.0;
	private static final int LUNGE_TRAVEL_TICKS = 8;

	private final Monster mob;
	private @Nullable LivingEntity target;
	private Phase phase = Phase.APPROACHING;
	private int attackCooldown;
	private int repathCooldown;
	private int recoveryTicks;
	private int lungeWindupTicks;
	private int lungeTravelTicks;
	private int lungeCooldown;
	private int sideDirection = 1;

	public SmartNetherUndeadMeleeGoal(final Monster mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity candidate = this.mob.getTarget();
		if (!this.isEnabled() || !this.hasMeleeLoadout() || !isValidTarget(candidate)) {
			return false;
		}
		this.target = candidate;
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		LivingEntity current = this.target;
		return this.isEnabled()
			&& this.hasMeleeLoadout()
			&& isValidTarget(current)
			&& this.mob.getTarget() == current
			&& this.mob.isWithinHome(current.position());
	}

	@Override
	public void start() {
		this.phase = Phase.APPROACHING;
		this.attackCooldown = 0;
		this.repathCooldown = 0;
		this.recoveryTicks = 0;
		this.lungeWindupTicks = 0;
		this.lungeTravelTicks = 0;
		// 实体 ID 错峰可避免同批出生的狂战士同一拍全部蓄力，且不需要扫描同伴。
		this.lungeCooldown = 8 + Math.floorMod(this.mob.getId(), 8);
		this.sideDirection = (this.mob.getId() & 1) == 0 ? 1 : -1;
		this.mob.setAggressive(true);
	}

	@Override
	public void stop() {
		this.mob.getNavigation().stop();
		this.mob.setAggressive(false);
		this.target = null;
		this.phase = Phase.APPROACHING;
		this.attackCooldown = 0;
		this.recoveryTicks = 0;
		this.lungeWindupTicks = 0;
		this.lungeTravelTicks = 0;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity current = this.target;
		if (current == null) {
			return;
		}

		NetherProfession profession = NetherProfessionProfile.get(this.mob);
		this.attackCooldown = Math.max(0, this.attackCooldown - 1);
		this.lungeCooldown = Math.max(0, this.lungeCooldown - 1);
		this.face(current);

		if (this.phase == Phase.LUNGE_WINDUP) {
			this.mob.getNavigation().stop();
			if (--this.lungeWindupTicks <= 0) {
				this.beginLunge(current, profession);
			}
			return;
		}

		if (this.phase == Phase.LUNGING) {
			this.mob.getNavigation().moveTo(
				current,
				NetherProfessionTactics.undeadMoveSpeed(profession) * 1.24
			);
			if (this.tryAttack(current, profession)) {
				return;
			}
			if (--this.lungeTravelTicks <= 0) {
				this.enterRecovery(profession, false);
			}
			return;
		}

		if (this.phase == Phase.RECOVERING) {
			this.mob.getNavigation().stop();
			this.mob.getMoveControl().strafe(-0.46F, 0.42F * this.sideDirection);
			if (--this.recoveryTicks <= 0) {
				this.phase = Phase.APPROACHING;
				this.repathCooldown = 0;
			}
			return;
		}

		double distanceSquared = this.mob.distanceToSqr(current);
		if (this.shouldStartLunge(profession, current, distanceSquared)) {
			this.phase = Phase.LUNGE_WINDUP;
			this.lungeWindupTicks = NetherProfessionTactics.undeadLungeWindupTicks(profession);
			this.mob.getNavigation().stop();
			this.playLungeTelegraph(profession);
			return;
		}

		if (this.tryAttack(current, profession)) {
			return;
		}

		if (--this.repathCooldown <= 0) {
			this.repathCooldown = 6 + Math.floorMod(this.mob.getId() + this.mob.tickCount, 7);
			this.mob.getNavigation().moveTo(current, NetherProfessionTactics.undeadMoveSpeed(profession));
		}
	}

	public Phase phase() {
		return this.phase;
	}

	private boolean tryAttack(final LivingEntity current, final NetherProfession profession) {
		if (this.attackCooldown > 0
			|| !this.mob.isWithinMeleeAttackRange(current)
			|| !this.mob.getSensing().hasLineOfSight(current)
			|| !(this.mob.level() instanceof ServerLevel serverLevel)) {
			return false;
		}

		this.mob.swing(InteractionHand.MAIN_HAND);
		boolean hurt = this.mob.doHurtTarget(serverLevel, current);
		this.attackCooldown = NetherProfessionTactics.undeadAttackIntervalTicks(profession);
		this.enterRecovery(profession, true);
		if (hurt) {
			SmartNetherMetrics.netherUndeadStrike();
		}
		return true;
	}

	private void enterRecovery(final NetherProfession profession, final boolean successfulStrike) {
		this.phase = Phase.RECOVERING;
		this.recoveryTicks = NetherProfessionTactics.undeadRecoveryTicks(profession);
		this.lungeCooldown = 42 + this.mob.getRandom().nextInt(35);
		this.sideDirection = -this.sideDirection;
		this.mob.getNavigation().stop();
		if (successfulStrike && this.recoveryTicks >= 9) {
			SmartNetherMetrics.netherUndeadFeint();
		}
	}

	private boolean shouldStartLunge(
		final NetherProfession profession,
		final LivingEntity current,
		final double distanceSquared
	) {
		return this.lungeCooldown <= 0
			&& NetherProfessionTactics.undeadUsesLunge(profession)
			&& distanceSquared >= LUNGE_MINIMUM_DISTANCE_SQUARED
			&& distanceSquared <= LUNGE_MAXIMUM_DISTANCE_SQUARED
			&& this.mob.onGround()
			&& this.mob.getSensing().hasLineOfSight(current);
	}

	private void beginLunge(final LivingEntity current, final NetherProfession profession) {
		Vec3 direction = NetherCombatMath.horizontalUnitOrEntityFallback(
			current.position().subtract(this.mob.position()),
			this.mob.getId()
		);
		double impulse = NetherProfessionTactics.undeadLungeImpulse(profession);
		double vertical = this.mob.onGround() ? Math.max(0.16, this.mob.getDeltaMovement().y) : this.mob.getDeltaMovement().y;
		this.mob.setDeltaMovement(direction.x * impulse, vertical, direction.z * impulse);
		this.mob.swing(InteractionHand.MAIN_HAND);
		this.phase = Phase.LUNGING;
		this.lungeTravelTicks = LUNGE_TRAVEL_TICKS;
		SmartNetherMetrics.netherUndeadLunge();
	}

	private void playLungeTelegraph(final NetherProfession profession) {
		if (profession.family() == NetherProfessionFamily.ZOMBIFIED_PIGLIN) {
			this.mob.playSound(SoundEvents.ZOMBIFIED_PIGLIN_ANGRY, 0.9F, 0.72F);
		} else {
			this.mob.playSound(SoundEvents.WITHER_SKELETON_AMBIENT, 0.9F, 0.78F);
		}
	}

	private void face(final LivingEntity current) {
		this.mob.getLookControl().setLookAt(current, 40.0F, 35.0F);
		this.mob.lookAt(current, 40.0F, 35.0F);
	}

	private boolean hasMeleeLoadout() {
		NetherProfessionFamily family = NetherProfessionProfile.familyOf(this.mob);
		if (family == NetherProfessionFamily.ZOMBIFIED_PIGLIN) {
			return !this.mob.getMainHandItem().has(DataComponents.KINETIC_WEAPON);
		}
		return family == NetherProfessionFamily.WITHER_SKELETON && !this.mob.isHolding(Items.BOW);
	}

	private boolean isEnabled() {
		var config = ConfigManager.get();
		return config.enabled && config.netherAiEnabled;
	}

	private static boolean isValidTarget(final @Nullable LivingEntity target) {
		return target != null
			&& target.isAlive()
			&& (!(target instanceof Player player) || (!player.isCreative() && !player.isSpectator()));
	}

	public enum Phase {
		APPROACHING,
		LUNGE_WINDUP,
		LUNGING,
		RECOVERING
	}
}
