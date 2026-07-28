package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.ai.zombie.squad.UtilityClass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 让战斗、撤退、队友支援和自身灭火共享同一条真实桶投放管线。 */
final class ZombieFluidActions {
	private ZombieFluidActions() {
	}

	static boolean canDeployAt(
		final ServerLevel level,
		final Zombie zombie,
		final UtilityClass utility,
		final BlockPos placement
	) {
		BlockState state = level.getBlockState(placement);
		return level.getGameRules().get(GameRules.MOB_GRIEFING)
			&& (state.isAir() || state.canBeReplaced())
			&& state.getFluidState().isEmpty()
			&& !state.hasBlockEntity()
			&& (utility != UtilityClass.LAVA || !hasFriendlyOccupying(level, placement, zombie));
	}

	static boolean tryDeploy(
		final ServerLevel level,
		final Zombie zombie,
		final UtilityClass utility,
		final BlockPos placement,
		final long retrieveAt,
		final FluidDeploymentPurpose purpose
	) {
		if (utility == UtilityClass.NONE
			|| ZombieSpecialEquipment.state(zombie).isDeployed()
			|| !ZombieSpecialEquipment.hasFullBucket(zombie, utility)
			|| !canDeployAt(level, zombie, utility, placement)) {
			return false;
		}
		ItemStack held = zombie.getMainHandItem();
		if (!(held.getItem() instanceof BucketItem bucketItem)
			|| !bucketItem.emptyContents(zombie, level, placement, null)) {
			return false;
		}

		// emptyContents 播放原版桶声并广播 FLUID_PLACE；这里补实体挥手和持久化事务。
		zombie.swing(InteractionHand.MAIN_HAND);
		ZombieSpecialEquipment.markDeployed(zombie, utility, placement, retrieveAt, purpose);
		SmartZombieMetrics.fluidDeployed(utility);
		return true;
	}

	/**
	 * 工程兵使用技能工具投放流体：BucketItem 仍执行原版放置、声音和游戏事件，但不要求
	 * 主手永久携带桶；源位置进入持久事务，之后由工程技能状态机真实回收。
	 */
	static boolean tryDeployEngineer(
		final ServerLevel level,
		final Zombie zombie,
		final UtilityClass utility,
		final BlockPos placement,
		final long retrieveAt,
		final InteractionHand visibleHand
	) {
		if (utility == UtilityClass.NONE
			|| ZombieSpecialEquipment.state(zombie).isDeployed()
			|| !canDeployAt(level, zombie, utility, placement)) {
			return false;
		}
		ItemStack stack = new ItemStack(utility == UtilityClass.WATER ? Items.WATER_BUCKET : Items.LAVA_BUCKET);
		if (!(stack.getItem() instanceof BucketItem bucketItem)
			|| !bucketItem.emptyContents(zombie, level, placement, null)) {
			return false;
		}

		zombie.swing(visibleHand);
		ZombieSpecialEquipment.markEngineerDeployed(zombie, utility, placement, retrieveAt);
		SmartZombieMetrics.fluidDeployed(utility);
		SmartZombieMetrics.engineerFluidDeployment(utility);
		return true;
	}

	/**
	 * 撤退开始时只执行一次的应急水幕。脚下是最可靠候选，其次才是攻击者与僵尸之间及两侧；
	 * 因此只要周围存在一个合法格就一定尝试，而不是再掷一个低概率随机数。
	 */
	static boolean tryDeployRetreatWater(
		final ServerLevel level,
		final Zombie zombie,
		final LivingEntity attacker,
		final long now
	) {
		if (!ZombieSpecialEquipment.hasFullBucket(zombie, UtilityClass.WATER)
			|| ZombieSpecialEquipment.state(zombie).isDeployed()) {
			return false;
		}

		BlockPos feet = feetPosition(zombie);
		Direction towardAttacker = horizontalDirection(zombie.position(), attacker.position());
		BlockPos[] candidates = {
			feet,
			feet.relative(towardAttacker),
			feet.relative(towardAttacker.getClockWise()),
			feet.relative(towardAttacker.getCounterClockWise()),
			feet.relative(towardAttacker.getOpposite())
		};
		long retrieveAt = now + 55L + Math.floorMod(zombie.getId(), 16);
		for (BlockPos candidate : candidates) {
			if (tryDeploy(
				level,
				zombie,
				UtilityClass.WATER,
				candidate,
				retrieveAt,
				FluidDeploymentPurpose.COMBAT
			)) {
				return true;
			}
		}
		return false;
	}

	static BlockPos feetPosition(final Zombie zombie) {
		return BlockPos.containing(zombie.getX(), zombie.getBoundingBox().minY + 0.01, zombie.getZ());
	}

	private static boolean hasFriendlyOccupying(
		final ServerLevel level,
		final BlockPos pos,
		final Zombie source
	) {
		AABB danger = new AABB(pos).inflate(0.15, 0.25, 0.15);
		return !level.getEntitiesOfClass(
			Zombie.class,
			danger,
			zombie -> zombie != source && zombie.isAlive()
		).isEmpty();
	}

	private static Direction horizontalDirection(final Vec3 origin, final Vec3 destination) {
		double x = destination.x - origin.x;
		double z = destination.z - origin.z;
		return Math.abs(x) >= Math.abs(z)
			? (x >= 0.0 ? Direction.EAST : Direction.WEST)
			: (z >= 0.0 ? Direction.SOUTH : Direction.NORTH);
	}
}
