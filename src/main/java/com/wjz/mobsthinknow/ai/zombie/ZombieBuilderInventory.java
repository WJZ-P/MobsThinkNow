package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 高智力僵尸的单槽建筑材料背包。
 *
 * <p>背包只接收白名单软方块的确定性“一方块换一材料”结果。挖掘时不生成原版随机掉落，
 * 避免草方块、沙砾等方块因为随机战利品造成复制或材料类型漂移；死亡时剩余材料会正常掉落。</p>
 */
public final class ZombieBuilderInventory {
	private static final String BUILDING_BLOCKS_TAG = "MobsThinkNowBuildingBlocks";

	private ZombieBuilderInventory() {
	}

	public static int count(final Zombie zombie) {
		return stack(zombie).getCount();
	}

	public static ItemStack stack(final Zombie zombie) {
		return ((ZombieBuilderInventoryAccess)zombie).mobsthinknow$getBuildingBlocks();
	}

	/** 返回目标方块被空手采集后进入建筑槽的标准材料。 */
	public static ItemStack harvestResult(final BlockState state) {
		if (state.is(BlockTags.DIRT)
			|| state.is(Blocks.GRASS_BLOCK)
			|| state.is(Blocks.PODZOL)
			|| state.is(Blocks.MYCELIUM)
			|| state.is(Blocks.DIRT_PATH)) {
			// 草方块、灰化土和菌丝在空手语义下统一收获为泥土，避免凭空获得精准采集结果。
			return Items.DIRT.getDefaultInstance();
		}
		if (state.is(BlockTags.SAND) || state.is(BlockTags.MUD)) {
			return state.getBlock().asItem().getDefaultInstance();
		}
		if (state.is(Blocks.GRAVEL)) {
			return Items.GRAVEL.getDefaultInstance();
		}
		if (state.is(Blocks.CLAY)) {
			return Items.CLAY.getDefaultInstance();
		}
		return ItemStack.EMPTY;
	}

	/**
	 * 严格限定“可空手采集”的基础软方块。硬度、工具要求、流体和方块实体四道检查
	 * 会拦住箱子、矿石、建筑方块以及数据型方块。
	 */
	public static boolean isHarvestable(
		final ServerLevel level,
		final BlockPos pos,
		final BlockState state,
		final Zombie zombie,
		final int capacity
	) {
		if (state.isAir()
			|| state.hasBlockEntity()
			|| !state.getFluidState().isEmpty()
			|| state.requiresCorrectToolForDrops()) {
			return false;
		}

		float destroySpeed = state.getDestroySpeed(level, pos);
		if (destroySpeed < 0.0F || destroySpeed > 1.0F) {
			return false;
		}

		ItemStack result = harvestResult(state);
		return !result.isEmpty() && canAccept(zombie, result, capacity);
	}

	public static boolean canAccept(final Zombie zombie, final ItemStack incoming, final int capacity) {
		if (incoming.isEmpty() || capacity <= 0) {
			return false;
		}
		ItemStack stored = stack(zombie);
		return stored.isEmpty()
			|| (ItemStack.isSameItemSameComponents(stored, incoming) && stored.getCount() < capacity);
	}

	/** 单次挖掘只增加一个材料，并始终服从配置容量上限。 */
	public static boolean addOne(final Zombie zombie, final ItemStack material, final int capacity) {
		if (!canAccept(zombie, material, capacity)) {
			return false;
		}
		ZombieBuilderInventoryAccess access = (ZombieBuilderInventoryAccess)zombie;
		ItemStack stored = access.mobsthinknow$getBuildingBlocks();
		if (stored.isEmpty()) {
			ItemStack first = material.copy();
			first.setCount(1);
			access.mobsthinknow$setBuildingBlocks(first);
		} else {
			stored.grow(1);
		}
		return true;
	}

	/** 读取下一块的默认放置状态；槽中出现非方块物品时返回空气并由加载校验清理。 */
	public static BlockState placementState(final Zombie zombie) {
		ItemStack stored = stack(zombie);
		if (stored.getItem() instanceof BlockItem blockItem) {
			return blockItem.getBlock().defaultBlockState();
		}
		return Blocks.AIR.defaultBlockState();
	}

	public static boolean consumeOne(final Zombie zombie) {
		ZombieBuilderInventoryAccess access = (ZombieBuilderInventoryAccess)zombie;
		ItemStack stored = access.mobsthinknow$getBuildingBlocks();
		if (stored.isEmpty()) {
			return false;
		}
		stored.shrink(1);
		if (stored.isEmpty()) {
			access.mobsthinknow$setBuildingBlocks(ItemStack.EMPTY);
		}
		return true;
	}

	public static void save(final Zombie zombie, final ValueOutput output) {
		output.store(BUILDING_BLOCKS_TAG, ItemStack.OPTIONAL_CODEC, stack(zombie));
	}

	public static void load(final Zombie zombie, final ValueInput input) {
		ItemStack loaded = input.read(BUILDING_BLOCKS_TAG, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		MobsThinkNowConfig config = ConfigManager.get();
		BlockState loadedState = loaded.getItem() instanceof BlockItem blockItem
			? blockItem.getBlock().defaultBlockState()
			: Blocks.AIR.defaultBlockState();
		if (loadedState.isAir()
			|| loadedState.hasBlockEntity()
			|| !loadedState.getFluidState().isEmpty()
			|| harvestResult(loadedState).isEmpty()) {
			loaded = ItemStack.EMPTY;
		} else {
			loaded = loaded.copyWithCount(Math.min(loaded.getCount(), config.terrainBlockInventoryLimit));
		}
		((ZombieBuilderInventoryAccess)zombie).mobsthinknow$setBuildingBlocks(loaded);
	}

	/** 死亡时把隐藏槽清空后生成一个掉落物，避免死亡回调被重复触发时复制材料。 */
	public static void dropAll(final Zombie zombie) {
		if (!(zombie.level() instanceof ServerLevel level)) {
			return;
		}
		ZombieBuilderInventoryAccess access = (ZombieBuilderInventoryAccess)zombie;
		ItemStack stored = access.mobsthinknow$getBuildingBlocks();
		if (stored.isEmpty()) {
			return;
		}
		ItemStack dropped = stored.copy();
		access.mobsthinknow$setBuildingBlocks(ItemStack.EMPTY);
		zombie.spawnAtLocation(level, dropped);
	}

	/** 原版类型转换创建新实体时原子转移隐藏槽；旧实体先清空，避免转换事件重入造成复制。 */
	public static void transfer(final Zombie previous, final Zombie converted) {
		ZombieBuilderInventoryAccess oldAccess = (ZombieBuilderInventoryAccess)previous;
		ItemStack stored = oldAccess.mobsthinknow$getBuildingBlocks();
		if (stored.isEmpty()) {
			return;
		}
		ItemStack transferred = stored.copy();
		oldAccess.mobsthinknow$setBuildingBlocks(ItemStack.EMPTY);
		((ZombieBuilderInventoryAccess)converted).mobsthinknow$setBuildingBlocks(transferred);
	}

	/** 近似原版空手破坏速度：硬度乘 30 tick，并限制异常自定义方块造成的极端时长。 */
	static int emptyHandBreakTicks(final float destroySpeed) {
		return Math.max(5, Math.min(40, (int)Math.ceil(destroySpeed * 30.0F)));
	}
}
