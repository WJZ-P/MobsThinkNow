package com.wjz.mobsthinknow.ai.zombie;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * 管理工程技能动画期间的临时工具换手。
 *
 * <p>TNT、打火石和铁锭只是技能过程的可见道具，不应覆盖僵尸真正拾取的武器。这里与进食
 * 换手采用同样的存档兜底：正常结束、Goal 打断、死亡和关服时恢复；自动保存恰好落在技能
 * 动画内时，额外写入真实装备，读档后用它替换临时工具。</p>
 */
public final class ZombieEngineerEquipment {
	private static final String SAVED_HAND_TAG = "MobsThinkNowEngineerStowedHand";
	private static final String SAVED_ITEM_TAG = "MobsThinkNowEngineerStowedItem";
	/** 所有访问都在服务器主线程；弱键只覆盖一次技能的短生命周期。 */
	private static final Map<Zombie, HandSwap> ACTIVE_SWAPS = new WeakHashMap<>();

	private ZombieEngineerEquipment() {
	}

	/** 优先使用空闲的手；双手都被占用时暂存副手，避免主手武器属性在动画中反复变化。 */
	public static InteractionHand begin(final Zombie zombie, final ItemStack visibleTool) {
		restore(zombie, false);
		InteractionHand hand = selectHand(zombie);
		ItemStack original = zombie.getItemInHand(hand).copy();
		zombie.stopUsingItem();
		zombie.setItemInHand(hand, visibleTool.copy());
		ACTIVE_SWAPS.put(zombie, new HandSwap(hand, original));
		return hand;
	}

	/** 在同一次技能里把 TNT 换成打火石，不覆盖最初暂存的真实装备。 */
	public static InteractionHand show(final Zombie zombie, final ItemStack visibleTool) {
		HandSwap swap = ACTIVE_SWAPS.get(zombie);
		if (swap == null) {
			return begin(zombie, visibleTool);
		}
		zombie.stopUsingItem();
		zombie.setItemInHand(swap.hand(), visibleTool.copy());
		return swap.hand();
	}

	public static void restore(final Zombie zombie, final boolean dropUnexpectedTool) {
		HandSwap swap = ACTIVE_SWAPS.remove(zombie);
		if (swap == null) {
			return;
		}
		ItemStack temporary = zombie.getItemInHand(swap.hand()).copy();
		zombie.stopUsingItem();
		zombie.setItemInHand(swap.hand(), swap.original().copy());
		if (dropUnexpectedTool && !temporary.isEmpty() && zombie.level() instanceof ServerLevel level) {
			// 技能自带的可见工具不是真实战利品，只有被其他系统换成未知物品时才应落地。
			if (!ZombieEngineerSkillGoal.isVisualTool(temporary)) {
				zombie.spawnAtLocation(level, temporary);
			}
		}
	}

	public static boolean isActive(final Zombie zombie) {
		return ACTIVE_SWAPS.containsKey(zombie);
	}

	public static void saveTemporaryEquipment(final Zombie zombie, final ValueOutput output) {
		HandSwap swap = ACTIVE_SWAPS.get(zombie);
		if (swap == null) {
			return;
		}
		output.putInt(SAVED_HAND_TAG, swap.hand() == InteractionHand.MAIN_HAND ? 0 : 1);
		output.store(SAVED_ITEM_TAG, ItemStack.OPTIONAL_CODEC, swap.original());
	}

	public static void restoreSavedEquipment(final Zombie zombie, final ValueInput input) {
		int encodedHand = input.getIntOr(SAVED_HAND_TAG, -1);
		if (encodedHand != 0 && encodedHand != 1) {
			return;
		}
		@Nullable ItemStack original = input.read(SAVED_ITEM_TAG, ItemStack.OPTIONAL_CODEC).orElse(null);
		if (original == null) {
			return;
		}
		InteractionHand hand = encodedHand == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
		zombie.stopUsingItem();
		zombie.setItemInHand(hand, original);
	}

	public static void restoreAll() {
		for (Zombie zombie : new ArrayList<>(ACTIVE_SWAPS.keySet())) {
			restore(zombie, false);
		}
	}

	public static void clear() {
		ACTIVE_SWAPS.clear();
	}

	private static InteractionHand selectHand(final Zombie zombie) {
		if (zombie.getMainHandItem().isEmpty()) {
			return InteractionHand.MAIN_HAND;
		}
		return InteractionHand.OFF_HAND;
	}

	private record HandSwap(InteractionHand hand, ItemStack original) {
	}
}
