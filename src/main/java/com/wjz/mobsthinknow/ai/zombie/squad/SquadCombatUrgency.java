package com.wjz.mobsthinknow.ai.zombie.squad;

import com.wjz.mobsthinknow.ai.creeper.CreeperCombatMath;
import com.wjz.mobsthinknow.ai.creeper.CreeperIntelligence;
import com.wjz.mobsthinknow.ai.skeleton.SkeletonCombatMath;
import com.wjz.mobsthinknow.ai.skeleton.SkeletonIntelligence;
import com.wjz.mobsthinknow.ai.spider.SpiderCombatMath;
import com.wjz.mobsthinknow.ai.spider.SpiderIntelligence;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * 小队命令与个体保命之间的统一仲裁层。
 *
 * <p>集结和部署会抢占 MOVE/LOOK；若目标已经进入当前兵种的有效攻击窗口，继续执行站位命令反而会
 * 让怪物显得迟钝。这里仅识别“必须本 tick 处理”的短距离威胁，不把普通追击都升级成紧急状态，
 * 因而不会破坏远距离编队。</p>
 */
public final class SquadCombatUrgency {
	private static final int RECENT_ATTACK_TICKS = 40;
	private static final int COMBAT_FORMATION_DEFENSE_TICKS = 10;
	private static final double SPIDER_MELEE_DISTANCE_SQUARED = 12.25;

	private SquadCombatUrgency() {
	}

	/** 当返回 true 时，调用方应在本 tick 暂停集结/部署命令并交还个体战斗 Goal。 */
	public static boolean shouldInterruptPreparation(final Mob mob, final LivingEntity target) {
		if (!mob.isAlive()
			|| !target.isAlive()
			|| !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target)) {
			return false;
		}

		if (wasRecentlyAttacked(mob, RECENT_ATTACK_TICKS)) {
			return true;
		}

		MobsThinkNowConfig config = ConfigManager.get();
		if (mob instanceof Creeper creeper) {
			return creeperUrgency(creeper, target, config);
		}
		if (mob instanceof Spider spider) {
			return spiderUrgency(spider, target, config);
		}
		if (mob instanceof AbstractSkeleton skeleton) {
			return skeletonUrgency(skeleton, target, config);
		}
		return mob instanceof Zombie zombie && zombie.isWithinMeleeAttackRange(target);
	}

	/**
	 * 已进入统一战斗节拍后，普通“走进攻击距离”不再打断阵位；只保留真实受击、已经点燃的苦力怕
	 * 和骷髅贴脸保命这三种不可等待事件。
	 */
	public static boolean shouldInterruptCombatFormation(final Mob mob, final LivingEntity target) {
		if (!mob.isAlive()
			|| !target.isAlive()
			|| !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target)) {
			return false;
		}
		if (wasRecentlyAttacked(mob, COMBAT_FORMATION_DEFENSE_TICKS)) {
			return true;
		}
		if (mob instanceof Creeper creeper) {
			return creeper.isIgnited() || creeper.getSwellDir() > 0;
		}
		return mob instanceof AbstractSkeleton skeleton
			&& skeletonUrgency(skeleton, target, ConfigManager.get());
	}

	public static boolean wasRecentlyAttackedBy(final Mob mob, final LivingEntity target) {
		return mob.getLastHurtByMob() == target
			&& target.isAlive()
			&& mob.tickCount - mob.getLastHurtByMobTimestamp() <= COMBAT_FORMATION_DEFENSE_TICKS;
	}

	private static boolean wasRecentlyAttacked(final Mob mob, final int maximumAgeTicks) {
		LivingEntity attacker = mob.getLastHurtByMob();
		return attacker != null
			&& attacker.isAlive()
			&& mob.tickCount - mob.getLastHurtByMobTimestamp() <= maximumAgeTicks;
	}

	private static boolean creeperUrgency(
		final Creeper creeper,
		final LivingEntity target,
		final MobsThinkNowConfig config
	) {
		if (creeper.isIgnited() || creeper.getSwellDir() > 0) {
			return true;
		}
		boolean visible = creeper.getSensing().hasLineOfSight(target);
		if (!visible) {
			return false;
		}

		int intelligence = CreeperIntelligence.get(creeper);
		double startDistance = CreeperCombatMath.fuseStartDistance(
			config.creeperMaximumFuseStartDistance,
			intelligence,
			creeper.isPowered(),
			creeper.level().getDifficulty().getId()
		);
		boolean watching = CreeperCombatMath.isTargetWatching(
			target.getLookAngle(),
			creeper.position().subtract(target.position())
		);
		return CreeperCombatMath.shouldStartFuse(
			creeper.distanceToSqr(target),
			startDistance,
			true,
			false,
			watching,
			target.isBlocking(),
			intelligence
		);
	}

	private static boolean spiderUrgency(
		final Spider spider,
		final LivingEntity target,
		final MobsThinkNowConfig config
	) {
		double distanceSquared = spider.distanceToSqr(target);
		if (distanceSquared <= SPIDER_MELEE_DISTANCE_SQUARED) {
			return true;
		}
		return config.spiderPredictivePounce
			&& SpiderCombatMath.canPredictivePounce(
				SpiderIntelligence.get(spider),
				spider.getSensing().hasLineOfSight(target),
				spider.onGround(),
				distanceSquared
			);
	}

	private static boolean skeletonUrgency(
		final AbstractSkeleton skeleton,
		final LivingEntity target,
		final MobsThinkNowConfig config
	) {
		double deltaX = target.getX() - skeleton.getX();
		double deltaZ = target.getZ() - skeleton.getZ();
		return SkeletonCombatMath.shouldStartEmergencyDisengage(
			deltaX * deltaX + deltaZ * deltaZ,
			config.skeletonPreferredRange,
			SkeletonIntelligence.get(skeleton)
		);
	}
}
