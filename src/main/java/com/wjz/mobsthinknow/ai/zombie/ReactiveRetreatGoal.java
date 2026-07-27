package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 独立于追击 Goal 的受击反应式撤退行为。
 *
 * <p>撤退不能放在 {@link SmartZombieAttackGoal#tick()} 里：原版近战 Goal 在目标不可达时
 * 根本不会启动，最需要逃生的被困僵尸反而收不到受击事件。本 Goal 以更高优先级直接占用
 * {@link Flag#MOVE} 和 {@link Flag#LOOK}，所以无论僵尸当时在追击、开会、包抄还是使用长矛，
 * 受击撤退都会先接管；结束后释放控制权，原战斗 Goal 再重新竞争执行。</p>
 */
public final class ReactiveRetreatGoal extends Goal {
	private static final double DESTINATION_REACHED_DISTANCE_SQUARED = 2.25;
	/** 只在攻击者背向半平面挑选可行走点，并定期重算以响应追击者移动。 */
	private static final double RETREAT_MINIMUM_DISTANCE = 5.0;
	private static final double RETREAT_MAXIMUM_DISTANCE = 9.0;
	private static final int RETREAT_VERTICAL_SEARCH = 4;
	private static final long RETREAT_PATH_REFRESH_TICKS = 8L;
	private final Zombie zombie;
	private @Nullable LivingEntity attacker;
	private @Nullable Vec3 retreatDestination;
	private long retreatDeadline;
	private long nextPathUpdateAt;
	private long nextBarrierAttemptAt;
	private int barrierBlocksPlaced;
	private boolean barrierIntent;

	public ReactiveRetreatGoal(final Zombie zombie) {
		this.zombie = zombie;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		MobsThinkNowConfig config = ConfigManager.get();
		if (!isEnabled(config)) {
			// 关闭期间丢弃积压事件，重新开启时不会响应很久以前的伤害。
			ZombieRetreatMemory.discard(this.zombie);
			return false;
		}
		LivingEntity freshAttacker = this.captureFreshRetreatAttack(config);
		if (freshAttacker == null) {
			return false;
		}

		// 远程攻击者本来就在安全半径外时，无需先启动 Goal 再于下一 tick 退出。
		if (hasReachedSafeDistance(
			this.zombie.position(),
			freshAttacker.position(),
			config.retreatSafeDistance
		)) {
			this.attacker = null;
			return false;
		}
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		MobsThinkNowConfig config = ConfigManager.get();
		return isEnabled(config)
			&& this.zombie.isAlive()
			&& this.attacker != null
			&& this.attacker.isAlive()
			&& shouldContinueRetreat(
				this.zombie.level().getGameTime(),
				this.retreatDeadline,
				this.zombie.position(),
				this.attacker.position(),
				config.retreatSafeDistance
			);
	}

	@Override
	public void start() {
		MobsThinkNowConfig config = ConfigManager.get();
		long now = this.zombie.level().getGameTime();
		this.retreatDeadline = now + config.retreatMaximumTicks;
		this.retreatDestination = null;
		this.nextPathUpdateAt = now;
		this.nextBarrierAttemptAt = now;
		this.barrierBlocksPlaced = 0;
		this.barrierIntent = shouldAttemptBarrier(
			ZombieIntelligence.get(this.zombie),
			config.terrainMinimumIntelligence,
			this.zombie.getRandom().nextFloat()
		);
		this.zombie.getNavigation().stop();
		this.zombie.stopUsingItem();
		SmartZombieMetrics.retreatTriggered();

		if (this.zombie.level() instanceof ServerLevel serverLevel) {
			// 受惊短叫让玩家能读出“它开始脱离接触”这一状态变化。
			serverLevel.playSound(
				null,
				this.zombie,
				SoundEvents.ZOMBIE_AMBIENT,
				SoundSource.HOSTILE,
				0.8F,
				ZombieVoiceProfile.expressivePitch(this.zombie, 1.4F)
			);
		}
	}

	@Override
	public void tick() {
		MobsThinkNowConfig config = ConfigManager.get();
		long now = this.zombie.level().getGameTime();
		LivingEntity freshAttacker = this.captureFreshRetreatAttack(config);
		if (freshAttacker != null) {
			// 逃跑中又受击：更新真正威胁与逃跑方向，但绝不延长从 start() 算起的硬时限。
			this.attacker = freshAttacker;
			this.retreatDestination = null;
			this.nextPathUpdateAt = now;
			this.zombie.getNavigation().stop();
		}

		LivingEntity currentAttacker = this.attacker;
		if (currentAttacker == null
			|| hasReachedSafeDistance(
				this.zombie.position(),
				currentAttacker.position(),
				config.retreatSafeDistance
			)) {
			// freshAttacker 可能在本 tick 把威胁切换到安全半径外；立即停路，不多跑一拍。
			this.zombie.getNavigation().stop();
			return;
		}

		this.maintainSquadHeartbeat(config, now);
		this.tryPlacePursuitBarrier(config, currentAttacker, now);
		this.updateEscapePath(config, now);
	}

	@Override
	public void stop() {
		// 明确停掉逃跑路径，使攻击 Goal 在下一次启动时立刻朝敌人重寻路，而不是继续向外跑。
		this.zombie.getNavigation().stop();
		this.attacker = null;
		this.retreatDestination = null;
		this.retreatDeadline = 0L;
		this.barrierBlocksPlaced = 0;
		this.barrierIntent = false;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	private @Nullable LivingEntity captureFreshRetreatAttack(final MobsThinkNowConfig config) {
		ZombieRetreatMemory.AttackSnapshot attack = ZombieRetreatMemory.consume(this.zombie);
		if (attack == null) {
			return null;
		}

		float maximumHealth = this.zombie.getMaxHealth();
		boolean lowHealth = isRetreatHealth(
			this.zombie.getHealth(),
			maximumHealth,
			config.retreatHealthThreshold
		);
		boolean heavyHit = isHeavyHit(
			attack.largestHealthDamage(),
			maximumHealth,
			config.retreatHeavyHitThreshold
		);
		if (!lowHealth && !heavyHit) {
			return null;
		}

		// 重伤优先远离造成该次重击的实体；低血则优先响应最近仍在施压的实体。
		LivingEntity preferred = heavyHit ? attack.largestDamageAttacker() : attack.latestAttacker();
		LivingEntity fallback = heavyHit ? attack.latestAttacker() : attack.largestDamageAttacker();
		LivingEntity freshAttacker = preferred.isAlive() ? preferred : fallback;
		if (!freshAttacker.isAlive()) {
			return null;
		}
		this.attacker = freshAttacker;
		return freshAttacker;
	}

	private void updateEscapePath(final MobsThinkNowConfig config, final long now) {
		LivingEntity currentAttacker = this.attacker;
		if (currentAttacker == null) {
			return;
		}

		boolean reachedDestination = this.retreatDestination == null
			|| this.zombie.position().distanceToSqr(this.retreatDestination) <= DESTINATION_REACHED_DISTANCE_SQUARED;
		if (now < this.nextPathUpdateAt && !reachedDestination && !this.zombie.getNavigation().isDone()) {
			return;
		}

		// 原版算法会采样可站立、无寻路惩罚的陆地点，比把坐标生硬平移更少撞墙或冲下悬崖。
		Vec3 destination = LandRandomPos.getPosAway(
			this.zombie,
			RETREAT_MINIMUM_DISTANCE,
			RETREAT_MAXIMUM_DISTANCE,
			RETREAT_VERTICAL_SEARCH,
			currentAttacker.position()
		);
		if (destination == null) {
			// 极端地形保留严格背向的退化候选；失败后两 tick 再搜索，而不是提前结束撤退。
			Vec3 away = horizontalUnit(
				this.zombie.position().subtract(currentAttacker.position()),
				currentAttacker.getLookAngle()
			);
			destination = this.zombie.position().add(away.scale(RETREAT_MINIMUM_DISTANCE));
		}

		boolean foundPath = this.zombie
			.getNavigation()
			.moveTo(destination.x, destination.y, destination.z, config.retreatSpeedModifier);
		this.retreatDestination = foundPath ? destination : null;
		this.nextPathUpdateAt = now + (foundPath ? RETREAT_PATH_REFRESH_TICKS : 2L);
		if (foundPath) {
			this.zombie.getLookControl().setLookAt(destination.add(0.0, 1.0, 0.0));
		} else if (this.zombie.onGround()) {
			SmartZombieMetrics.failedPath();
		}
	}

	/**
	 * 玩家确实朝僵尸追来时才消耗隐藏库存，在二者之间尝试放一到两块墙；扫描范围为固定两个候选格，
	 * 不查询附近全部实体。放置成功后立即重算逃跑路径，让导航绕开自己刚建的障碍。
	 */
	private void tryPlacePursuitBarrier(
		final MobsThinkNowConfig config,
		final LivingEntity currentAttacker,
		final long now
	) {
		if (!this.barrierIntent
			|| !config.terrainTactics
			|| !config.pursuitBarriers
			|| this.barrierBlocksPlaced >= ZombieTerrainTacticsGoal.PLAYER_BARRIER_RESERVE
			|| now < this.nextBarrierAttemptAt
			|| ZombieBuilderInventory.count(this.zombie) <= 0
			|| !(currentAttacker instanceof Player player)
			|| player.isCreative()
			|| player.isSpectator()
			|| !(this.zombie.level() instanceof ServerLevel level)
			|| !level.getGameRules().get(GameRules.MOB_GRIEFING)
			|| !isPursuing(player.position(), player.getDeltaMovement(), player.getLookAngle(), this.zombie.position())) {
			return;
		}

		Vec3 towardPlayer = horizontalUnit(player.position().subtract(this.zombie.position()), player.getLookAngle());
		for (double distance : new double[] {1.35, 1.85}) {
			BlockPos bottom = BlockPos.containing(
				this.zombie.getX() + towardPlayer.x * distance,
				this.zombie.getY(),
				this.zombie.getZ() + towardPlayer.z * distance
			);
			if (bottom.equals(this.zombie.blockPosition())
				|| !ZombieBlockActions.tryPlaceStoredBlock(this.zombie, level, bottom)) {
				continue;
			}

			this.barrierBlocksPlaced++;
			SmartZombieMetrics.pursuitBarrierPlaced();
			if (this.barrierBlocksPlaced < ZombieTerrainTacticsGoal.PLAYER_BARRIER_RESERVE
				&& ZombieBuilderInventory.count(this.zombie) > 0
				&& ZombieBlockActions.tryPlaceStoredBlock(this.zombie, level, bottom.above())) {
				this.barrierBlocksPlaced++;
				SmartZombieMetrics.pursuitBarrierPlaced();
			}
			this.zombie.getNavigation().stop();
			this.retreatDestination = null;
			this.nextPathUpdateAt = now + 1L;
			this.nextBarrierAttemptAt = now + 20L;
			return;
		}
		this.nextBarrierAttemptAt = now + 8L;
	}

	/** 撤退期间攻击 Goal 暂停，但小队成员仍要保活，避免 40 tick 后被协调器误判离队。 */
	private void maintainSquadHeartbeat(final MobsThinkNowConfig config, final long now) {
		if (!config.packSurrounding || !(this.zombie.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		LivingEntity combatTarget = this.zombie.getTarget();
		if (combatTarget == null || !combatTarget.isAlive()) {
			combatTarget = this.attacker;
		}
		if (combatTarget == null || !combatTarget.isAlive()) {
			return;
		}

		boolean hasLineOfSight = this.zombie.getSensing().hasLineOfSight(combatTarget);
		ZombieSquadCoordinator.forLevel(serverLevel).heartbeat(
			this.zombie,
			combatTarget,
			hasLineOfSight,
			hasLineOfSight ? combatTarget.position() : null,
			hasLineOfSight ? now : Long.MIN_VALUE
		);
	}

	private static boolean isEnabled(final MobsThinkNowConfig config) {
		return config.enabled && config.zombieAiEnabled && config.retreatTactics;
	}

	static boolean isRetreatHealth(final float health, final float maximumHealth, final double threshold) {
		return health > 0.0F && maximumHealth > 0.0F && health <= maximumHealth * threshold;
	}

	static boolean isHeavyHit(final float healthDamage, final float maximumHealth, final double threshold) {
		return healthDamage > 0.0F
			&& maximumHealth > 0.0F
			&& healthDamage >= maximumHealth * threshold;
	}

	static boolean shouldContinueRetreat(
		final long now,
		final long deadline,
		final Vec3 zombiePosition,
		final Vec3 attackerPosition,
		final double safeDistance
	) {
		return now < deadline && !hasReachedSafeDistance(zombiePosition, attackerPosition, safeDistance);
	}

	/** 只计算水平距离，避免攻击者站在头顶或坑底时被垂直高差误判为已经成功脱离。 */
	static boolean hasReachedSafeDistance(
		final Vec3 zombiePosition,
		final Vec3 attackerPosition,
		final double safeDistance
	) {
		double x = zombiePosition.x - attackerPosition.x;
		double z = zombiePosition.z - attackerPosition.z;
		return x * x + z * z >= safeDistance * safeDistance;
	}

	static boolean shouldAttemptBarrier(final int intelligence, final int minimumIntelligence, final double roll) {
		if (intelligence < minimumIntelligence) {
			return false;
		}
		double chance = Math.min(0.75, 0.35 + (intelligence - minimumIntelligence) * 0.15);
		return roll < chance;
	}

	static boolean isPursuing(
		final Vec3 playerPosition,
		final Vec3 playerMovement,
		final Vec3 playerLook,
		final Vec3 zombiePosition
	) {
		Vec3 toZombie = new Vec3(
			zombiePosition.x - playerPosition.x,
			0.0,
			zombiePosition.z - playerPosition.z
		);
		double distanceSquared = toZombie.horizontalDistanceSqr();
		if (distanceSquared < 1.5 * 1.5 || distanceSquared > 5.75 * 5.75) {
			return false;
		}
		Vec3 direction = toZombie.normalize();
		Vec3 movement = new Vec3(playerMovement.x, 0.0, playerMovement.z);
		Vec3 look = new Vec3(playerLook.x, 0.0, playerLook.z);
		boolean movingToward = movement.horizontalDistanceSqr() > 1.0E-4
			&& movement.normalize().dot(direction) > 0.45;
		boolean lookingToward = look.horizontalDistanceSqr() > 1.0E-6
			&& look.normalize().dot(direction) > 0.72;
		return movingToward || lookingToward;
	}

	private static Vec3 horizontalUnit(final Vec3 preferred, final Vec3 fallback) {
		Vec3 horizontal = new Vec3(preferred.x, 0.0, preferred.z);
		if (horizontal.horizontalDistanceSqr() < 1.0E-6) {
			horizontal = new Vec3(fallback.x, 0.0, fallback.z);
		}
		if (horizontal.horizontalDistanceSqr() < 1.0E-6) {
			return new Vec3(0.0, 0.0, 1.0);
		}
		return horizontal.normalize();
	}
}
