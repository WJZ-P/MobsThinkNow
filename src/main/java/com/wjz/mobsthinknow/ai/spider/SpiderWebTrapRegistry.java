package com.wjz.mobsthinknow.ai.spider;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * 临时蛛网的按维度登记、限流与原状恢复。
 *
 * <p>登记表只保存真正由本 Mod 成功放置的方块。到期、维度卸载和服务器关停都会恢复旧状态；若玩家已经
 * 破坏或替换了蛛网，则只丢弃登记而不覆盖玩家的新方块。每个维度还有硬上限，避免大量蜘蛛制造无界状态。</p>
 */
public final class SpiderWebTrapRegistry {
	private static final int MAXIMUM_ACTIVE_TRAPS_PER_LEVEL = 128;
	private static final int MINIMUM_HORIZONTAL_SPACING_SQUARED = 2;
	private static final Map<ServerLevel, LinkedHashMap<BlockPos, Trap>> ACTIVE = new IdentityHashMap<>();

	private SpiderWebTrapRegistry() {
	}

	public static synchronized boolean canPlace(final ServerLevel level, final BlockPos pos) {
		if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return false;
		}
		BlockState current = level.getBlockState(pos);
		BlockPos support = pos.below();
		if (!current.isAir()
			|| !current.getFluidState().isEmpty()
			|| current.hasBlockEntity()
			|| !level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)) {
			return false;
		}
		LinkedHashMap<BlockPos, Trap> traps = ACTIVE.get(level);
		if (traps == null || traps.isEmpty()) {
			return true;
		}
		for (BlockPos reserved : traps.keySet()) {
			int dx = reserved.getX() - pos.getX();
			int dz = reserved.getZ() - pos.getZ();
			if (Math.abs(reserved.getY() - pos.getY()) <= 1
				&& dx * dx + dz * dz <= MINIMUM_HORIZONTAL_SPACING_SQUARED) {
				return false;
			}
		}
		return true;
	}

	public static synchronized boolean tryPlace(
		final ServerLevel level,
		final BlockPos pos,
		final UUID owner,
		final long now,
		final int lifetimeTicks
	) {
		tickLevel(level);
		LinkedHashMap<BlockPos, Trap> traps = ACTIVE.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
		if (traps.size() >= MAXIMUM_ACTIVE_TRAPS_PER_LEVEL || !canPlace(level, pos)) {
			if (traps.isEmpty()) {
				ACTIVE.remove(level);
			}
			return false;
		}
		BlockState previous = level.getBlockState(pos);
		BlockState web = Blocks.COBWEB.defaultBlockState();
		if (!level.setBlock(pos, web, Block.UPDATE_ALL)) {
			return false;
		}
		traps.put(pos.immutable(), new Trap(owner, previous, now + Math.max(40, lifetimeTicks)));
		playPlacementFeedback(level, pos, web);
		SmartSpiderMetrics.webTrapPlaced();
		return true;
	}

	/** 每个维度 tick 只线性扫描最多 128 个登记项。 */
	public static synchronized void tickLevel(final ServerLevel level) {
		LinkedHashMap<BlockPos, Trap> traps = ACTIVE.get(level);
		if (traps == null) {
			return;
		}
		long now = level.getGameTime();
		Iterator<Map.Entry<BlockPos, Trap>> iterator = traps.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<BlockPos, Trap> entry = iterator.next();
			if (entry.getValue().expiresAt() > now) {
				continue;
			}
			restoreIfStillOwned(level, entry.getKey(), entry.getValue(), true);
			iterator.remove();
		}
		if (traps.isEmpty()) {
			ACTIVE.remove(level);
		}
	}

	public static synchronized void unloadLevel(final ServerLevel level) {
		LinkedHashMap<BlockPos, Trap> traps = ACTIVE.remove(level);
		if (traps == null) {
			return;
		}
		for (Map.Entry<BlockPos, Trap> entry : traps.entrySet()) {
			restoreIfStillOwned(level, entry.getKey(), entry.getValue(), false);
		}
	}

	/** SERVER_STOPPING 时世界仍可写，必须在最后一次保存前撤掉所有临时蛛网。 */
	public static synchronized void restoreAll() {
		for (Map.Entry<ServerLevel, LinkedHashMap<BlockPos, Trap>> levelEntry : new ArrayList<>(ACTIVE.entrySet())) {
			ServerLevel level = levelEntry.getKey();
			for (Map.Entry<BlockPos, Trap> trapEntry : levelEntry.getValue().entrySet()) {
				restoreIfStillOwned(level, trapEntry.getKey(), trapEntry.getValue(), false);
			}
		}
		ACTIVE.clear();
	}

	public static synchronized void clearAll() {
		ACTIVE.clear();
	}

	public static synchronized int activeCount() {
		return ACTIVE.values().stream().mapToInt(Map::size).sum();
	}

	public static synchronized boolean isOwnedTrap(final ServerLevel level, final BlockPos pos) {
		Map<BlockPos, Trap> traps = ACTIVE.get(level);
		return traps != null && traps.containsKey(pos);
	}

	private static void restoreIfStillOwned(
		final ServerLevel level,
		final BlockPos pos,
		final Trap trap,
		final boolean feedback
	) {
		if (!level.getBlockState(pos).is(Blocks.COBWEB)) {
			return;
		}
		if (!level.setBlock(pos, trap.previousState(), Block.UPDATE_ALL)) {
			return;
		}
		SmartSpiderMetrics.webTrapExpired();
		if (feedback) {
			SoundType sound = Blocks.COBWEB.defaultBlockState().getSoundType();
			level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS, 0.55F, sound.getPitch() * 1.15F);
			level.sendParticles(
				new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBWEB.defaultBlockState()),
				pos.getX() + 0.5,
				pos.getY() + 0.45,
				pos.getZ() + 0.5,
				8,
				0.25,
				0.25,
				0.25,
				0.02
			);
		}
	}

	private static void playPlacementFeedback(final ServerLevel level, final BlockPos pos, final BlockState web) {
		SoundType sound = web.getSoundType();
		level.playSound(
			null,
			pos,
			sound.getPlaceSound(),
			SoundSource.HOSTILE,
			0.85F,
			sound.getPitch() * 1.20F
		);
		level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(web));
		level.sendParticles(
			new BlockParticleOption(ParticleTypes.BLOCK, web),
			pos.getX() + 0.5,
			pos.getY() + 0.5,
			pos.getZ() + 0.5,
			14,
			0.30,
			0.30,
			0.30,
			0.03
		);
	}

	private record Trap(UUID owner, BlockState previousState, long expiresAt) {
	}
}
