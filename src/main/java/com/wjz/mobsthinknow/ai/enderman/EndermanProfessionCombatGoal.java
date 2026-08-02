package com.wjz.mobsthinknow.ai.enderman;

import com.wjz.mobsthinknow.config.ConfigManager;
import java.util.EnumSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * 裂隙猎手、虚空盾卫与苦力怕使者共用的近战状态机。
 *
 * <p>猎手在中距离尝试传送到目标后侧，命中后有概率闪离；盾卫则明确执行“举盾接近—观察—
 * 放盾反击—收招”循环。每只实体只读取自身目标，传送候选数固定，不扫描附近同伴。</p>
 */
public final class EndermanProfessionCombatGoal extends Goal {
	private static final double FLANK_MINIMUM_DISTANCE_SQUARED = 5.0 * 5.0;
	private static final double FLANK_MAXIMUM_DISTANCE_SQUARED = 18.0 * 18.0;
	private static final double GUARD_RAISE_DISTANCE_SQUARED = 12.0 * 12.0;
	private static final double GUARD_HOLD_DISTANCE_SQUARED = 8.0 * 8.0;

	private final EnderMan enderman;
	private @Nullable LivingEntity target;
	private Phase phase = Phase.APPROACHING;
	private int attackCooldown;
	private int repathCooldown;
	private int teleportCooldown;
	private int phaseTicks;
	private boolean counterFromBlock;

	public EndermanProfessionCombatGoal(final EnderMan enderman) {
		this.enderman = enderman;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity candidate = this.enderman.getTarget();
		EndermanProfession profession = EndermanProfessionProfile.get(this.enderman);
		if (!enabled()
			|| profession == EndermanProfession.NONE
			|| profession == EndermanProfession.VOID_LANCER
			|| EndermanCreeperDeliveryGoal.isCarryingCreeper(this.enderman)
			|| !isValidTarget(candidate)) {
			return false;
		}
		this.target = candidate;
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		LivingEntity current = this.target;
		EndermanProfession profession = EndermanProfessionProfile.get(this.enderman);
		return enabled()
			&& profession != EndermanProfession.NONE
			&& profession != EndermanProfession.VOID_LANCER
			&& !EndermanCreeperDeliveryGoal.isCarryingCreeper(this.enderman)
			&& isValidTarget(current)
			&& this.enderman.getTarget() == current;
	}

	@Override
	public void start() {
		this.phase = Phase.APPROACHING;
		this.attackCooldown = 0;
		this.repathCooldown = 0;
		this.teleportCooldown = 10 + Math.floorMod(this.enderman.getId(), 16);
		this.phaseTicks = 0;
		this.counterFromBlock = false;
		this.enderman.setAggressive(true);
	}

	@Override
	public void stop() {
		this.lowerShield();
		this.enderman.getNavigation().stop();
		this.enderman.setAggressive(false);
		this.target = null;
		this.phase = Phase.APPROACHING;
		this.phaseTicks = 0;
		this.counterFromBlock = false;
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
		this.attackCooldown = Math.max(0, this.attackCooldown - 1);
		this.teleportCooldown = Math.max(0, this.teleportCooldown - 1);
		this.face(current);

		EndermanProfession profession = EndermanProfessionProfile.get(this.enderman);
		if (profession == EndermanProfession.VOID_GUARD && this.hasUsableShield()) {
			this.tickGuard(current);
		} else {
			this.lowerShield();
			this.tickMelee(current, profession);
		}
	}

	/** LivingEntity 的真实盾牌阻挡回调；盾仍保持 2～4 tick，再给出清晰的放盾反击窗口。 */
	public void onShieldBlock(final LivingEntity attacker) {
		if (this.target == null
			|| attacker != this.target
			|| EndermanProfessionProfile.get(this.enderman) != EndermanProfession.VOID_GUARD
			|| !this.hasUsableShield()) {
			return;
		}
		this.phase = Phase.GUARDING;
		this.phaseTicks = 2 + this.enderman.getRandom().nextInt(3);
		this.counterFromBlock = true;
		SmartEndermanMetrics.shieldBlock();
	}

	public Phase phase() {
		return this.phase;
	}

	public boolean counterFromBlock() {
		return this.counterFromBlock;
	}

	private void tickGuard(final LivingEntity current) {
		double distanceSquared = this.enderman.distanceToSqr(current);
		if (this.phase == Phase.RECOVERING) {
			this.lowerShield();
			this.enderman.getNavigation().stop();
			this.enderman.getMoveControl().strafe(-0.34F, (this.enderman.getId() & 1) == 0 ? 0.28F : -0.28F);
			if (--this.phaseTicks <= 0) {
				this.phase = Phase.APPROACHING;
				this.repathCooldown = 0;
			}
			return;
		}
		if (this.phase == Phase.GUARDING && this.counterFromBlock) {
			// 真格挡后的 2～4 tick 延迟不要求目标仍卡在近战盒内；后退一步不能取消已经读到的反击。
			this.raiseShield();
			this.enderman.getNavigation().stop();
			if (--this.phaseTicks <= 0) {
				this.beginCounterWindup(true);
			}
			return;
		}
		if (this.phase == Phase.COUNTER_WINDUP) {
			this.lowerShield();
			this.enderman.getNavigation().stop();
			this.enderman.setAggressive(true);
			if (--this.phaseTicks > 0) {
				return;
			}
			if (this.counterFromBlock && this.teleportCooldown <= 0 && distanceSquared <= GUARD_HOLD_DISTANCE_SQUARED) {
				if (EndermanCombatTeleport.tryFlank(this.enderman, current, 2.4, 5)) {
					this.teleportCooldown = 55 + this.enderman.getRandom().nextInt(31);
				}
			}
			if (!this.tryAttack(current, EndermanProfession.VOID_GUARD)) {
				this.phase = Phase.APPROACHING;
				this.phaseTicks = this.counterFromBlock ? 20 : 0;
				this.repathCooldown = 0;
			}
			return;
		}
		if (this.phase == Phase.APPROACHING && this.counterFromBlock) {
			// 传送点受地形拒绝时保留一个短追击窗口，避免盾卫放盾后立即忘记反击。
			this.lowerShield();
			this.enderman.setAggressive(true);
			if (this.tryAttack(current, EndermanProfession.VOID_GUARD)) {
				return;
			}
			this.moveToward(current, 1.16);
			if (--this.phaseTicks <= 0) {
				this.counterFromBlock = false;
			}
			return;
		}

		if (distanceSquared <= GUARD_RAISE_DISTANCE_SQUARED) {
			this.raiseShield();
		} else {
			this.lowerShield();
		}
		if (this.enderman.isWithinMeleeAttackRange(current)) {
			this.enderman.getNavigation().stop();
			if (this.phase != Phase.GUARDING) {
				this.phase = Phase.GUARDING;
				this.phaseTicks = 14 + this.enderman.getRandom().nextInt(17);
			}
			if (--this.phaseTicks <= 0 && this.attackCooldown <= 0) {
				this.beginCounterWindup(false);
			}
			return;
		}

		this.phase = Phase.APPROACHING;
		this.moveToward(current, 1.08);
	}

	private void tickMelee(final LivingEntity current, final EndermanProfession profession) {
		if (this.phase == Phase.RECOVERING) {
			this.enderman.getNavigation().stop();
			this.enderman.getMoveControl().strafe(-0.38F, (this.enderman.getId() & 1) == 0 ? 0.34F : -0.34F);
			if (--this.phaseTicks <= 0) {
				this.phase = Phase.APPROACHING;
				this.repathCooldown = 0;
			}
			return;
		}

		double distanceSquared = this.enderman.distanceToSqr(current);
		if (profession == EndermanProfession.RIFTBLADE
			&& this.teleportCooldown <= 0
			&& distanceSquared >= FLANK_MINIMUM_DISTANCE_SQUARED
			&& distanceSquared <= FLANK_MAXIMUM_DISTANCE_SQUARED
			&& this.enderman.hasLineOfSight(current)) {
			if (EndermanCombatTeleport.tryFlank(this.enderman, current, 3.8, 6)) {
				int intelligence = EndermanIntelligence.get(this.enderman);
				this.teleportCooldown = Math.max(36, 78 - intelligence * 3) + this.enderman.getRandom().nextInt(21);
				distanceSquared = this.enderman.distanceToSqr(current);
			}
		}
		if (this.tryAttack(current, profession)) {
			if (profession == EndermanProfession.RIFTBLADE
				&& EndermanIntelligence.get(this.enderman) >= 7
				&& this.enderman.getRandom().nextDouble() < 0.42) {
				EndermanCombatTeleport.tryRetreat(this.enderman, current, 6.5, 5);
			}
			return;
		}
		double speed = profession == EndermanProfession.CREEPER_HERALD ? 1.0 : 1.14;
		this.moveToward(current, speed);
	}

	private boolean tryAttack(final LivingEntity current, final EndermanProfession profession) {
		if (this.attackCooldown > 0
			|| !this.enderman.isWithinMeleeAttackRange(current)
			|| !this.enderman.hasLineOfSight(current)
			|| !(this.enderman.level() instanceof ServerLevel level)) {
			return false;
		}
		this.lowerShield();
		this.enderman.swing(InteractionHand.MAIN_HAND);
		boolean hurt = this.enderman.doHurtTarget(level, current);
		this.attackCooldown = profession == EndermanProfession.VOID_GUARD
			? 22
			: profession == EndermanProfession.CREEPER_HERALD ? 24 : 18;
		this.phase = Phase.RECOVERING;
		this.phaseTicks = profession == EndermanProfession.VOID_GUARD
			? 6 + this.enderman.getRandom().nextInt(4)
			: 5 + this.enderman.getRandom().nextInt(4);
		this.enderman.getNavigation().stop();
		if (hurt) {
			SmartEndermanMetrics.professionHit();
			if (this.counterFromBlock) {
				SmartEndermanMetrics.shieldCounterHit();
			}
		}
		this.counterFromBlock = false;
		return true;
	}

	private void beginCounterWindup(final boolean fromBlock) {
		this.lowerShield();
		this.phase = Phase.COUNTER_WINDUP;
		this.phaseTicks = 2;
		this.counterFromBlock = fromBlock || this.counterFromBlock;
		this.enderman.setAggressive(true);
	}

	private void moveToward(final LivingEntity current, final double speed) {
		if (--this.repathCooldown <= 0 || this.enderman.getNavigation().isDone()) {
			this.repathCooldown = 5 + Math.floorMod(this.enderman.getId() + this.enderman.tickCount, 6);
			this.enderman.getNavigation().moveTo(current, speed);
		}
	}

	private void face(final LivingEntity current) {
		this.enderman.getLookControl().setLookAt(current, 70.0F, 60.0F);
		this.enderman.lookAt(current, 70.0F, 60.0F);
	}

	private boolean hasUsableShield() {
		return this.enderman.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS);
	}

	private void raiseShield() {
		this.enderman.setAggressive(false);
		if (!this.enderman.isUsingItem()) {
			this.enderman.startUsingItem(InteractionHand.OFF_HAND);
		}
	}

	private void lowerShield() {
		if (this.enderman.isUsingItem() && this.enderman.getUsedItemHand() == InteractionHand.OFF_HAND) {
			this.enderman.stopUsingItem();
		}
	}

	private static boolean enabled() {
		var config = ConfigManager.get();
		return config.enabled && config.endermanAiEnabled;
	}

	private static boolean isValidTarget(final @Nullable LivingEntity target) {
		return target != null
			&& target.isAlive()
			&& (!(target instanceof Player player) || (!player.isCreative() && !player.isSpectator()));
	}

	public enum Phase {
		APPROACHING,
		GUARDING,
		COUNTER_WINDUP,
		RECOVERING
	}
}
