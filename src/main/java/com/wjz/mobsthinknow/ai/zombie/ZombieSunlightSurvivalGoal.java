package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.ai.zombie.squad.UtilityClass;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.Path;
import org.jspecify.annotations.Nullable;

/**
 * 日光生存状态机：露天受晒时先用主手水桶在脚下制造真实水源，再寻找可达阴影。
 *
 * <p>Goal 不会覆盖战斗反应。最近 40 tick 内被生物攻击时，canUse/canContinue 都立即返回
 * false，让撤退、跨沟或攻击 Goal 在下一次选择中接管。自救水源也不会在正午把已经躲好的僵尸
 * 拉回露天；流体事务会等夜晚、降雨或水源被遮挡后再回收。</p>
 */
public final class ZombieSunlightSurvivalGoal extends Goal {
	private static final double SHADE_SPEED = 1.18;
	private static final int SHADE_SEARCH_INTERVAL_TICKS = 20;
	private static final int FAILED_SEARCH_RETRY_TICKS = 10;
	private static final int MAXIMUM_SHADE_PATH_CHECKS = 6;
	private static final int SUN_WATER_MINIMUM_HOLD_TICKS = 60;
	private static final int[] VERTICAL_OFFSETS = {0, 1, -1, 2, -2, 3, -3};

	private final Zombie zombie;
	private long nextShadeSearchAt;
	private boolean waterDeployed;

	public ZombieSunlightSurvivalGoal(final Zombie zombie) {
		this.zombie = zombie;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!(this.zombie.level() instanceof ServerLevel level)
			|| !this.zombie.isAlive()
			|| !isEnabled(ConfigManager.get())
			|| ZombieCombatUrgency.wasRecentlyAttacked(this.zombie)) {
			return false;
		}

		ZombieFluidCarrierState state = ZombieSpecialEquipment.state(this.zombie);
		boolean continuingSavedEscape = state.isDeployed()
			&& state.isSunProtection()
			&& ZombieSunlightRules.requiresEscape(this.zombie, level);
		return ZombieSunlightRules.isExposed(this.zombie, level) || continuingSavedEscape;
	}

	@Override
	public boolean canContinueToUse() {
		if (!(this.zombie.level() instanceof ServerLevel level)
			|| !this.zombie.isAlive()
			|| !isEnabled(ConfigManager.get())
			|| ZombieCombatUrgency.wasRecentlyAttacked(this.zombie)
			|| !this.zombie.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
			|| ZombieSunlightRules.isShaded(this.zombie, level)) {
			return false;
		}
		return ZombieSunlightRules.requiresEscape(this.zombie, level);
	}

	@Override
	public void start() {
		this.zombie.getNavigation().stop();
		this.zombie.stopUsingItem();
		this.zombie.setAggressive(false);
		ZombieFluidCarrierState state = ZombieSpecialEquipment.state(this.zombie);
		this.waterDeployed = state.isDeployed() && state.isSunProtection();
		this.nextShadeSearchAt = this.zombie.level().getGameTime();
		this.tryDeployWater((ServerLevel)this.zombie.level());
		this.updateShadePath((ServerLevel)this.zombie.level(), true);
	}

	@Override
	public void tick() {
		this.zombie.setAggressive(false);
		ServerLevel level = (ServerLevel)this.zombie.level();
		if (!this.waterDeployed) {
			this.tryDeployWater(level);
		}
		if (ZombieSunlightRules.isShaded(this.zombie, level)) {
			this.zombie.getNavigation().stop();
			return;
		}
		this.updateShadePath(level, false);
	}

	@Override
	public void stop() {
		this.zombie.getNavigation().stop();
		this.zombie.setAggressive(false);
		this.nextShadeSearchAt = 0L;
		this.waterDeployed = false;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	private void tryDeployWater(final ServerLevel level) {
		if (this.waterDeployed
			|| !level.getGameRules().get(GameRules.MOB_GRIEFING)
			|| ZombieSpecialEquipment.state(this.zombie).isDeployed()) {
			return;
		}
		ItemStack held = this.zombie.getMainHandItem();
		if (!held.is(Items.WATER_BUCKET) || !(held.getItem() instanceof BucketItem bucketItem)) {
			return;
		}

		BlockPos placement = BlockPos.containing(
			this.zombie.getX(),
			this.zombie.getBoundingBox().minY + 0.01,
			this.zombie.getZ()
		);
		BlockState state = level.getBlockState(placement);
		if ((!state.isAir() && !state.canBeReplaced())
			|| !state.getFluidState().isEmpty()
			|| state.hasBlockEntity()
			|| !bucketItem.emptyContents(this.zombie, level, placement, null)) {
			return;
		}

		// emptyContents 已播放原版倒水声与 FLUID_PLACE；挥手和立即灭火让动作/结果同拍可见。
		this.zombie.swing(InteractionHand.MAIN_HAND);
		this.zombie.clearFire();
		long now = level.getGameTime();
		ZombieSpecialEquipment.markSunProtectionDeployed(
			this.zombie,
			placement,
			now + SUN_WATER_MINIMUM_HOLD_TICKS
		);
		SmartZombieMetrics.fluidDeployed(UtilityClass.WATER);
		this.waterDeployed = true;
	}

	private void updateShadePath(final ServerLevel level, final boolean force) {
		long now = level.getGameTime();
		PathNavigation navigation = this.zombie.getNavigation();
		// 路径提前耗尽也遵守重试间隔，避免大量露天僵尸每 tick 重新做阴影寻路。
		if (!force && now < this.nextShadeSearchAt) {
			return;
		}

		Path shade = this.findShadePath(level);
		if (shade != null) {
			navigation.moveTo(shade, SHADE_SPEED);
			this.nextShadeSearchAt = now + SHADE_SEARCH_INTERVAL_TICKS;
		} else {
			navigation.stop();
			this.nextShadeSearchAt = now + FAILED_SEARCH_RETRY_TICKS;
		}
	}

	private @Nullable Path findShadePath(final ServerLevel level) {
		BlockPos origin = this.zombie.blockPosition();
		int phase = this.zombie.getRandom().nextInt(16);
		int pathChecks = 0;
		for (int ring = 1; ring <= 4; ring++) {
			int radius = ring * 3;
			for (int step = 0; step < 16; step++) {
				double angle = (phase + step) * Math.PI * 2.0 / 16.0;
				int dx = (int)Math.round(Math.cos(angle) * radius);
				int dz = (int)Math.round(Math.sin(angle) * radius);
				for (int dy : VERTICAL_OFFSETS) {
					BlockPos candidate = origin.offset(dx, dy, dz);
					if (!level.getChunkSource().hasChunk(
						SectionPos.blockToSectionCoord(candidate.getX()),
						SectionPos.blockToSectionCoord(candidate.getZ())
					)
						|| level.canSeeSky(candidate.above())
						|| !ZombieTraversalRules.canStandAt(level, candidate)) {
						continue;
					}

					Path path = this.zombie.getNavigation().createPath(candidate, 0);
					if (path != null && path.canReach()) {
						return path;
					}
					if (++pathChecks >= MAXIMUM_SHADE_PATH_CHECKS) {
						return null;
					}
				}
			}
		}
		return null;
	}

	private static boolean isEnabled(final MobsThinkNowConfig config) {
		return config.enabled && config.zombieAiEnabled && config.sunlightSurvival;
	}
}
