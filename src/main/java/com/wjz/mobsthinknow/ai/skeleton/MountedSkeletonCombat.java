package com.wjz.mobsthinknow.ai.skeleton;

import com.wjz.mobsthinknow.ai.giant.GiantPassengerLayout;
import com.wjz.mobsthinknow.ai.spider.SpiderSquadTransportAccess;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import org.jspecify.annotations.Nullable;

/**
 * 小队射手骑乘巨人或蜘蛛时共用的目标与职责边界。
 *
 * <p>坐骑负责接敌、寻路和位移，骷髅只负责观察、装填和射击。巨人头顶挂点本身高出约十二格，
 * 若继续使用骷髅自己的 16 格三维 FOLLOW_RANGE，目标横向离开十余格便会被提前清空；蜘蛛骑手
 * 虽没有高度问题，也会把无效导航与逃跑输入持续写给乘客。两种受管理骑乘关系因此共用同一套
 * O(1) 载具目标镜像，普通蜘蛛骑士等原版关系保持原样。</p>
 */
public final class MountedSkeletonCombat {
	private MountedSkeletonCombat() {
	}

	/** 返回真正由本模组管理的射手坐骑；抓取乘客及原版蜘蛛骑士均被排除。 */
	public static @Nullable Mob mountOf(final AbstractSkeleton skeleton) {
		if (skeleton.getVehicle() instanceof Giant giant
			&& GiantPassengerLayout.headRider(giant) == skeleton) {
			return giant;
		}
		if (skeleton.getVehicle() instanceof Spider spider
			&& spider instanceof SpiderSquadTransportAccess transport
			&& transport.mobsthinknow$isSquadPassenger(skeleton.getId())) {
			return spider;
		}
		return null;
	}

	public static boolean isManagedRider(final AbstractSkeleton skeleton) {
		return mountOf(skeleton) != null;
	}

	/**
	 * 返回坐骑可继续攻击且射手也允许攻击的共享目标，不再重复施加骷髅自身的追踪距离。
	 */
	public static @Nullable LivingEntity sharedTarget(final AbstractSkeleton skeleton) {
		Mob mount = mountOf(skeleton);
		if (mount == null || !mount.isAlive() || !enabledFor(mount)) {
			return null;
		}
		LivingEntity target = mount.getTarget();
		if (target == null
			|| !target.isAlive()
			|| target.isSpectator()
			|| target == skeleton
			|| target == mount
			|| target.getVehicle() == mount
			|| mount.hasPassenger(target)
			|| mount.isAlliedTo(target)
			|| skeleton.isAlliedTo(target)
			|| !mount.canAttack(target)
			|| !skeleton.canAttack(target)) {
			return null;
		}
		return target;
	}

	private static boolean enabledFor(final Mob mount) {
		MobsThinkNowConfig config = ConfigManager.get();
		if (!config.enabled || !config.skeletonAiEnabled) {
			return false;
		}
		if (mount instanceof Giant) {
			return config.giantZombieAiEnabled;
		}
		return mount instanceof Spider && config.spiderAiEnabled && config.packSurrounding;
	}
}
