package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.ai.zombie.squad.UtilityClass;
import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 水桶辅助兵与岩浆骚扰兵的完整“投放—拉开—回收—冷却”状态机。
 *
 * <p>水桶兵在没有求援时保持支援距离；自己或队友被合法战斗目标攻击后把水放到追击路径上，收到同队着火
 * 请求时则忽略普通冷却并优先在队友脚下灭火。岩浆兵主动
 * 接近到合法交互距离，把岩浆放到当前目标脚下或相邻安全格，短暂拉开后回收。源方块由实体 NBT 记录：保存/重载不会
 * 遗忘；若玩家提前收走或堵掉流体，僵尸真实保留空桶并立即降级成普通近战。</p>
 */
public final class ZombieFluidTacticsGoal extends Goal {
	private static final double BUCKET_REACH_SQUARED = 4.25 * 4.25;
	private static final double SUPPORT_MINIMUM_DISTANCE_SQUARED = 4.5 * 4.5;
	private static final double SUPPORT_MAXIMUM_DISTANCE_SQUARED = 8.0 * 8.0;
	private static final int PATH_REFRESH_TICKS = 10;
	private static final int WATER_HOLD_TICKS = 45;
	private static final int LAVA_HOLD_TICKS = 32;
	private static final int WATER_COOLDOWN_TICKS = 100;
	private static final int LAVA_COOLDOWN_TICKS = 140;
	private static final int POST_LAVA_DISENGAGE_TICKS = 40;
	private static final double SUPPORT_SPEED = 1.12;
	private static final double DISENGAGE_SPEED = 1.35;

	private final Zombie zombie;
	private Phase phase = Phase.IDLE;
	private UtilityClass utility = UtilityClass.NONE;
	private @Nullable Zombie burningAlly;
	private @Nullable LivingEntity threat;
	private @Nullable Vec3 defendedPosition;
	private long nextPathAt;
	private long disengageUntil;

	public ZombieFluidTacticsGoal(final Zombie zombie) {
		this.zombie = zombie;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!(this.zombie.level() instanceof ServerLevel level) || !this.zombie.isAlive()) {
			return false;
		}

		ZombieFluidCarrierState state = ZombieSpecialEquipment.state(this.zombie);
		if (state.isDeployed()) {
			if (!this.shouldResumeDeployedTransaction(level, state)) {
				return false;
			}
			// 配置被热关闭时仍完成已经开始的回收事务，避免世界中遗留无限流体源。
			this.utility = state.utility();
			this.captureFireSupport();
			this.captureThreat();
			return true;
		}

		MobsThinkNowConfig config = ConfigManager.get();
		if (!isEnabled(config) || !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return false;
		}
		this.utility = ZombieSpecialEquipment.utilityClassOf(this.zombie);
		if (!ZombieSpecialEquipment.hasFullBucket(this.zombie, this.utility)) {
			return false;
		}

		this.captureFireSupport();
		this.captureThreat();
		return this.burningAlly != null || this.threat != null;
	}

	@Override
	public boolean canContinueToUse() {
		if (!this.zombie.isAlive() || !(this.zombie.level() instanceof ServerLevel)) {
			return false;
		}
		ZombieFluidCarrierState state = ZombieSpecialEquipment.state(this.zombie);
		if (state.isDeployed()) {
			return this.shouldResumeDeployedTransaction((ServerLevel)this.zombie.level(), state);
		}
		return isEnabled(ConfigManager.get())
			&& ZombieSpecialEquipment.hasFullBucket(this.zombie, this.utility)
			&& (isUsableBurningAlly(this.burningAlly)
				|| this.threat != null
				|| this.currentCombatTarget() != null)
			&& this.phase != Phase.DONE;
	}

	@Override
	public void start() {
		long now = this.zombie.level().getGameTime();
		this.nextPathAt = now;
		this.disengageUntil = 0L;
		this.zombie.stopUsingItem();
		this.zombie.setAggressive(false);
		ZombieFluidCarrierState state = ZombieSpecialEquipment.state(this.zombie);
		this.phase = state.isDeployed()
			? (now >= state.retrieveAt() ? Phase.RETRIEVING : Phase.DEPLOYED)
			: Phase.STAGING;
	}

	@Override
	public void tick() {
		this.zombie.setAggressive(false);
		this.captureFireSupport();
		this.captureThreat();
		this.maintainSquadHeartbeat();

		ZombieFluidCarrierState state = ZombieSpecialEquipment.state(this.zombie);
		if (state.isDeployed()) {
			this.tickDeployed(state);
			return;
		}

		if (!ZombieSpecialEquipment.hasFullBucket(this.zombie, this.utility)) {
			this.phase = Phase.DONE;
			return;
		}

		long now = this.zombie.level().getGameTime();
		if (this.utility == UtilityClass.WATER && this.tickFireSupport((ServerLevel)this.zombie.level(), now)) {
			return;
		}
		if (this.phase == Phase.DISENGAGING && now < this.disengageUntil) {
			this.moveAwayFromThreat(DISENGAGE_SPEED);
			return;
		}
		this.phase = Phase.STAGING;

		LivingEntity target = this.resolveThreat();
		if (target == null) {
			this.phase = Phase.DONE;
			return;
		}
		this.zombie.getLookControl().setLookAt(target, 30.0F, 30.0F);

		boolean hasAttackAlert = this.defendedPosition != null;
		boolean shouldDeploy = this.utility == UtilityClass.LAVA || hasAttackAlert;
		boolean mayModifyTerrain = ((ServerLevel)this.zombie.level())
			.getGameRules()
			.get(GameRules.MOB_GRIEFING);
		if (shouldDeploy && mayModifyTerrain && now >= state.cooldownUntil()) {
			BlockPos placement = this.findPlacement(target);
			if (placement != null) {
				if (Vec3.atCenterOf(placement).distanceToSqr(this.zombie.getEyePosition()) <= BUCKET_REACH_SQUARED) {
					if (this.tryDeploy((ServerLevel)this.zombie.level(), placement, now)) {
						return;
					}
				} else {
					this.approachPlacement(placement, now);
					return;
				}
			}
		}

		// 满桶但尚无投放窗口：水桶兵不贴脸，岩浆兵在冷却期也保持骚扰距离。
		this.maintainSupportDistance(target, now);
	}

	@Override
	public void stop() {
		this.zombie.getNavigation().stop();
		this.zombie.setAggressive(false);
		this.phase = Phase.IDLE;
		if (!isUsableBurningAlly(this.burningAlly)) {
			this.burningAlly = null;
		}
		this.threat = null;
		this.defendedPosition = null;
		this.nextPathAt = 0L;
		this.disengageUntil = 0L;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	private void captureThreat() {
		ZombieFluidThreatMemory.Alert alert = ZombieFluidThreatMemory.consume(this.zombie);
		if (alert != null) {
			this.threat = alert.attacker();
			this.defendedPosition = alert.defendedPosition();
			return;
		}
		if (this.threat == null || !isUsableTarget(this.threat)) {
			this.threat = this.currentCombatTarget();
			this.defendedPosition = null;
		}
	}

	private void captureFireSupport() {
		if (this.utility != UtilityClass.WATER
			|| !ZombieSpecialEquipment.hasFullBucket(this.zombie, UtilityClass.WATER)) {
			if (!isUsableBurningAlly(this.burningAlly)) {
				this.burningAlly = null;
			}
			return;
		}
		if (isUsableBurningAlly(this.burningAlly)) {
			return;
		}
		ZombieFireSupportMemory.Request request = ZombieFireSupportMemory.consume(this.zombie);
		this.burningAlly = request == null ? null : request.burningMember();
	}

	/** 辅助 Goal 长期占用 MOVE 时仍提交 O(1) 心跳，使工具兵真正参与选举、开会和职位展示。 */
	private void maintainSquadHeartbeat() {
		MobsThinkNowConfig config = ConfigManager.get();
		if (!config.packSurrounding || !(this.zombie.level() instanceof ServerLevel level)) {
			return;
		}
		LivingEntity combatTarget = this.zombie.getTarget();
		if (combatTarget == null || !combatTarget.isAlive()) {
			combatTarget = this.resolveThreat();
		}
		if (combatTarget == null || !combatTarget.isAlive()) {
			return;
		}
		boolean visible = this.zombie.getSensing().hasLineOfSight(combatTarget);
		long now = level.getGameTime();
		ZombieSquadCoordinator.forLevel(level).heartbeat(
			this.zombie,
			combatTarget,
			visible,
			visible ? combatTarget.position() : null,
			visible ? now : Long.MIN_VALUE
		);
	}

	private @Nullable LivingEntity resolveThreat() {
		if (isUsableTarget(this.threat)) {
			return this.threat;
		}
		this.threat = this.currentCombatTarget();
		this.defendedPosition = null;
		return this.threat;
	}

	private @Nullable LivingEntity currentCombatTarget() {
		LivingEntity target = this.zombie.getTarget();
		return isUsableTarget(target) ? target : null;
	}

	/**
	 * 队友救火优先于普通攻击支援和流体冷却。请求已经由协调器限定在同一小队，因此这里只做
	 * O(1) 的目标跟随与最多五个落点判定，不再查询附近实体。
	 */
	private boolean tickFireSupport(final ServerLevel level, final long now) {
		Zombie ally = this.burningAlly;
		if (!isUsableBurningAlly(ally)) {
			this.burningAlly = null;
			return false;
		}

		this.phase = Phase.APPROACHING;
		this.zombie.getLookControl().setLookAt(ally, 30.0F, 30.0F);
		BlockPos placement = this.findFireSupportPlacement(level, ally);
		if (placement == null) {
			if (now >= this.nextPathAt) {
				this.zombie.getNavigation().moveTo(ally, SUPPORT_SPEED);
				this.nextPathAt = now + PATH_REFRESH_TICKS;
			}
			return true;
		}

		if (Vec3.atCenterOf(placement).distanceToSqr(this.zombie.getEyePosition()) > BUCKET_REACH_SQUARED) {
			this.approachPlacement(placement, now);
			return true;
		}
		long retrieveAt = now + WATER_HOLD_TICKS + Math.floorMod(this.zombie.getId(), 16);
		if (!ZombieFluidActions.tryDeploy(
			level,
			this.zombie,
			UtilityClass.WATER,
			placement,
			retrieveAt,
			FluidDeploymentPurpose.COMBAT
		)) {
			this.nextPathAt = now + 2L;
			return true;
		}

		// 水直接落在队友脚部时立即播放原版熄火反馈；相邻落点则让流动水和队友自己的寻水 Goal 接力。
		if (placement.equals(ZombieFluidActions.feetPosition(ally))) {
			ally.extinguishFire();
		}
		this.burningAlly = null;
		this.defendedPosition = null;
		this.zombie.getNavigation().stop();
		this.phase = Phase.DEPLOYED;
		this.nextPathAt = now;
		return true;
	}

	private @Nullable BlockPos findFireSupportPlacement(final ServerLevel level, final Zombie ally) {
		BlockPos feet = ZombieFluidActions.feetPosition(ally);
		Direction towardCarrier = horizontalDirection(ally, this.zombie.position());
		BlockPos[] candidates = {
			feet,
			feet.relative(towardCarrier),
			feet.relative(towardCarrier.getClockWise()),
			feet.relative(towardCarrier.getCounterClockWise()),
			feet.relative(towardCarrier.getOpposite())
		};
		for (BlockPos candidate : candidates) {
			if (ZombieFluidActions.canDeployAt(level, this.zombie, UtilityClass.WATER, candidate)) {
				return candidate.immutable();
			}
		}
		return null;
	}

	private void tickDeployed(final ZombieFluidCarrierState state) {
		ServerLevel level = (ServerLevel)this.zombie.level();
		BlockPos source = state.source();
		if (source == null || !isMatchingSource(level, source, state.utility())) {
			ZombieSpecialEquipment.markFluidLost(this.zombie);
			SmartZombieMetrics.fluidSourceLost();
			this.phase = Phase.DONE;
			return;
		}

		long now = level.getGameTime();
		this.zombie.getLookControl().setLookAt(Vec3.atCenterOf(source));
		if (now < state.retrieveAt()) {
			this.phase = Phase.DEPLOYED;
			this.moveAwayFromThreat(DISENGAGE_SPEED);
			return;
		}

		this.phase = Phase.RETRIEVING;
		if (Vec3.atCenterOf(source).distanceToSqr(this.zombie.getEyePosition()) > BUCKET_REACH_SQUARED) {
			if (now >= this.nextPathAt) {
				this.zombie.getNavigation().moveTo(
					source.getX() + 0.5,
					source.getY(),
					source.getZ() + 0.5,
					SUPPORT_SPEED
				);
				this.nextPathAt = now + PATH_REFRESH_TICKS;
			}
			return;
		}

		BlockState blockState = level.getBlockState(source);
		if (!(blockState.getBlock() instanceof BucketPickup pickup)) {
			ZombieSpecialEquipment.markFluidLost(this.zombie);
			SmartZombieMetrics.fluidSourceLost();
			this.phase = Phase.DONE;
			return;
		}
		ItemStack recovered = pickup.pickupBlock(this.zombie, level, source, blockState);
		if (!isExpectedBucket(recovered, state.utility())) {
			ZombieSpecialEquipment.markFluidLost(this.zombie);
			SmartZombieMetrics.fluidSourceLost();
			this.phase = Phase.DONE;
			return;
		}

		pickup.getPickupSound().ifPresent(sound -> level.playSound(
			null, source, sound, SoundSource.BLOCKS, 1.0F, 1.0F
		));
		level.gameEvent(this.zombie, GameEvent.FLUID_PICKUP, source);
		this.zombie.swing(InteractionHand.MAIN_HAND);
		long cooldown = state.utility() == UtilityClass.WATER ? WATER_COOLDOWN_TICKS : LAVA_COOLDOWN_TICKS;
		ZombieSpecialEquipment.markRecovered(this.zombie, state.utility(), recovered, now + cooldown);
		SmartZombieMetrics.fluidRecovered();
		this.phase = state.utility() == UtilityClass.LAVA ? Phase.DISENGAGING : Phase.STAGING;
		this.disengageUntil = now + POST_LAVA_DISENGAGE_TICKS;
		this.nextPathAt = now;
	}

	private boolean tryDeploy(final ServerLevel level, final BlockPos placement, final long now) {
		int holdTicks = this.utility == UtilityClass.WATER
			? WATER_HOLD_TICKS + Math.floorMod(this.zombie.getId(), 16)
			: LAVA_HOLD_TICKS + Math.floorMod(this.zombie.getId(), 10);
		if (!ZombieFluidActions.tryDeploy(
			level,
			this.zombie,
			this.utility,
			placement,
			now + holdTicks,
			FluidDeploymentPurpose.COMBAT
		)) {
			return false;
		}
		// 一次求援只触发一次投放；回收后必须等新的受击事件，水桶兵不会无休止倒水。
		this.defendedPosition = null;
		this.zombie.getNavigation().stop();
		this.phase = Phase.DEPLOYED;
		this.nextPathAt = now;
		return true;
	}

	private @Nullable BlockPos findPlacement(final LivingEntity target) {
		ServerLevel level = (ServerLevel)this.zombie.level();
		BlockPos feet = target.blockPosition();
		BlockPos towardDefender = feet;
		if (this.utility == UtilityClass.WATER && this.defendedPosition != null) {
			Vec3 direction = horizontalUnit(
				this.defendedPosition.subtract(target.position()),
				this.zombie.position().subtract(target.position())
			);
			towardDefender = BlockPos.containing(target.position().add(direction.scale(1.15)));
		}

		Direction towardCarrier = horizontalDirection(target, this.zombie.position());
		BlockPos[] candidates = this.utility == UtilityClass.WATER
			? new BlockPos[] {towardDefender, feet, feet.relative(towardCarrier)}
			: new BlockPos[] {
				feet,
				feet.relative(towardCarrier),
				feet.relative(towardCarrier.getClockWise()),
				feet.relative(towardCarrier.getCounterClockWise()),
				feet.relative(towardCarrier.getOpposite())
		};
		for (BlockPos candidate : candidates) {
			if (ZombieFluidActions.canDeployAt(level, this.zombie, this.utility, candidate)) {
				return candidate.immutable();
			}
		}
		return null;
	}

	private void approachPlacement(final BlockPos placement, final long now) {
		if (now < this.nextPathAt) {
			return;
		}
		this.phase = Phase.APPROACHING;
		this.zombie.getNavigation().moveTo(
			placement.getX() + 0.5,
			placement.getY(),
			placement.getZ() + 0.5,
			SUPPORT_SPEED
		);
		this.nextPathAt = now + PATH_REFRESH_TICKS;
	}

	private void maintainSupportDistance(final LivingEntity target, final long now) {
		double distance = horizontalDistanceSquared(this.zombie.position(), target.position());
		if (distance < SUPPORT_MINIMUM_DISTANCE_SQUARED) {
			this.moveAwayFrom(target.position(), SUPPORT_SPEED);
			return;
		}
		if (distance > SUPPORT_MAXIMUM_DISTANCE_SQUARED && now >= this.nextPathAt) {
			this.zombie.getNavigation().moveTo(target, SUPPORT_SPEED);
			this.nextPathAt = now + PATH_REFRESH_TICKS;
			return;
		}
		if (distance >= SUPPORT_MINIMUM_DISTANCE_SQUARED && distance <= SUPPORT_MAXIMUM_DISTANCE_SQUARED) {
			this.zombie.getNavigation().stop();
		}
	}

	private void moveAwayFromThreat(final double speed) {
		LivingEntity target = this.resolveThreat();
		Vec3 danger = target == null ? this.defendedPosition : target.position();
		if (danger != null) {
			this.moveAwayFrom(danger, speed);
		}
	}

	private void moveAwayFrom(final Vec3 danger, final double speed) {
		long now = this.zombie.level().getGameTime();
		if (now < this.nextPathAt && !this.zombie.getNavigation().isDone()) {
			return;
		}
		Vec3 destination = LandRandomPos.getPosAway(this.zombie, 5.0, 7.0, 3, danger);
		if (destination != null) {
			this.zombie.getNavigation().moveTo(destination.x, destination.y, destination.z, speed);
		}
		this.nextPathAt = now + PATH_REFRESH_TICKS;
	}

	private static boolean isMatchingSource(
		final ServerLevel level,
		final BlockPos source,
		final UtilityClass utility
	) {
		return level.getFluidState(source).isSource()
			&& switch (utility) {
				case WATER -> level.getFluidState(source).is(FluidTags.WATER);
				case LAVA -> level.getFluidState(source).is(FluidTags.LAVA);
				case NONE -> false;
			};
	}

	private static boolean isExpectedBucket(final ItemStack stack, final UtilityClass utility) {
		return utility == UtilityClass.WATER ? stack.is(Items.WATER_BUCKET)
			: utility == UtilityClass.LAVA && stack.is(Items.LAVA_BUCKET);
	}

	/**
	 * 生存自救水不能在正午立刻把已经躲进阴影的僵尸重新拖回露天水源；等待夜晚、下雨或水源被
	 * 遮住后再回收。若刚受生物攻击，同样立即让出 MOVE/LOOK，让战斗或撤退 Goal 接管。
	 */
	private boolean shouldResumeDeployedTransaction(
		final ServerLevel level,
		final ZombieFluidCarrierState state
	) {
		if (!state.isSurvivalProtection()) {
			return true;
		}
		// 生存水的后续回收服从“近期受击时战斗优先”，包括源被玩家拿走后的清理。
		if (ZombieCombatUrgency.wasRecentlyAttacked(this.zombie)) {
			return false;
		}
		BlockPos source = state.source();
		if (source == null || !isMatchingSource(level, source, state.utility())) {
			// 丢失的源仍要启动一拍完成状态清理，不能留下永久空桶事务。
			return true;
		}
		return !ZombieSunlightRules.isDangerousSource(this.zombie, level, source);
	}

	private static Direction horizontalDirection(final LivingEntity target, final Vec3 destination) {
		double x = destination.x - target.getX();
		double z = destination.z - target.getZ();
		return Math.abs(x) >= Math.abs(z)
			? (x >= 0.0 ? Direction.EAST : Direction.WEST)
			: (z >= 0.0 ? Direction.SOUTH : Direction.NORTH);
	}

	private static boolean isUsableTarget(final @Nullable LivingEntity target) {
		return target != null
			&& target.isAlive()
			&& (!(target instanceof Player player)
				|| (!player.isCreative() && !player.isSpectator()));
	}

	private boolean isUsableBurningAlly(final @Nullable Zombie ally) {
		return ally != null
			&& ally.isAlive()
			&& ally.isOnFire()
			&& ally.level() == this.zombie.level();
	}

	private static boolean isEnabled(final MobsThinkNowConfig config) {
		return config.enabled && config.zombieAiEnabled && config.specialEquipment && config.fluidTactics;
	}

	private static double horizontalDistanceSquared(final Vec3 first, final Vec3 second) {
		double x = first.x - second.x;
		double z = first.z - second.z;
		return x * x + z * z;
	}

	private static Vec3 horizontalUnit(final Vec3 preferred, final Vec3 fallback) {
		Vec3 horizontal = new Vec3(preferred.x, 0.0, preferred.z);
		if (horizontal.horizontalDistanceSqr() < 1.0E-6) {
			horizontal = new Vec3(fallback.x, 0.0, fallback.z);
		}
		return horizontal.horizontalDistanceSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : horizontal.normalize();
	}

	private enum Phase {
		IDLE,
		STAGING,
		APPROACHING,
		DEPLOYED,
		RETRIEVING,
		DISENGAGING,
		DONE
	}
}
