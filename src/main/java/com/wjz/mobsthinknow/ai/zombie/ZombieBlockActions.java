package com.wjz.mobsthinknow.ai.zombie;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;

/** 采集库存的共享放置事务，供垫高和撤退阻挡共同使用。 */
public final class ZombieBlockActions {
	private ZombieBlockActions() {
	}

	public static boolean tryPlaceStoredBlock(final Zombie zombie, final ServerLevel level, final BlockPos pos) {
		BlockState material = ZombieBuilderInventory.placementState(zombie);
		if (material == null || material.isAir()) {
			return false;
		}
		BlockState existing = level.getBlockState(pos);
		if (!existing.canBeReplaced() || !existing.getFluidState().isEmpty() || existing.hasBlockEntity()) {
			return false;
		}
		if (!material.canSurvive(level, pos)
			|| !Block.canSupportCenter(level, pos.below(), Direction.UP)
			|| !level.isUnobstructed(material, pos, CollisionContext.empty())) {
			return false;
		}
		if (!level.setBlock(pos, material, Block.UPDATE_ALL)) {
			return false;
		}

		ZombieBuilderInventory.consumeOne(zombie);
		SmartZombieMetrics.terrainBlockPlaced();
		SoundType sound = material.getSoundType();
		level.playSound(
			null,
			pos,
			sound.getPlaceSound(),
			SoundSource.BLOCKS,
			(sound.getVolume() + 1.0F) / 2.0F,
			sound.getPitch() * 0.8F
		);
		level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(zombie, material));
		zombie.swing(InteractionHand.MAIN_HAND);
		return true;
	}
}
