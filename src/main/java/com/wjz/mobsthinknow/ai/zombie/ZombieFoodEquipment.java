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
 * 管理进食期间的临时换手，保证武器或盾牌在进食结束、Goal 被打断、死亡及读档后都能恢复。
 */
public final class ZombieFoodEquipment {
	private static final String SAVED_HAND_TAG = "MobsThinkNowFoodStowedHand";
	private static final String SAVED_ITEM_TAG = "MobsThinkNowFoodStowedItem";
	/** 所有访问都发生在服务器主线程；弱键避免异常移除的实体被这张短生命周期表保活。 */
	private static final Map<Zombie, HandSwap> ACTIVE_SWAPS = new WeakHashMap<>();

	private ZombieFoodEquipment() {
	}

	/** 把指定手原有物品收好，只把单份食物暴露给原版 use-item 同步与渲染链。 */
	public static void begin(final Zombie zombie, final InteractionHand hand, final ItemStack food) {
		restore(zombie, true);
		ItemStack original = zombie.getItemInHand(hand).copy();
		zombie.stopUsingItem();
		zombie.setItemInHand(hand, food);
		ACTIVE_SWAPS.put(zombie, new HandSwap(hand, original));
	}

	/**
	 * 恢复原装备；临时手里若仍有食物或吃完生成了碗等容器，则按调用方要求掉到脚边。
	 */
	public static void restore(final Zombie zombie, final boolean dropTemporaryItem) {
		HandSwap swap = ACTIVE_SWAPS.remove(zombie);
		if (swap == null) {
			return;
		}
		ItemStack temporary = zombie.getItemInHand(swap.hand()).copy();
		zombie.stopUsingItem();
		zombie.setItemInHand(swap.hand(), swap.original().copy());
		if (dropTemporaryItem && !temporary.isEmpty() && zombie.level() instanceof ServerLevel level) {
			zombie.spawnAtLocation(level, temporary);
		}
	}

	public static boolean isActive(final Zombie zombie) {
		return ACTIVE_SWAPS.containsKey(zombie);
	}

	/** 活跃换手发生在实体装备序列化之前时，把被藏起的真实装备额外写入恢复标签。 */
	public static void saveTemporaryEquipment(final Zombie zombie, final ValueOutput output) {
		HandSwap swap = ACTIVE_SWAPS.get(zombie);
		if (swap == null) {
			return;
		}
		output.putInt(SAVED_HAND_TAG, swap.hand() == InteractionHand.MAIN_HAND ? 0 : 1);
		output.store(SAVED_ITEM_TAG, ItemStack.OPTIONAL_CODEC, swap.original());
	}

	/** 崩溃或自动保存恰好落在进食动画内时，读档后优先恢复原武器/盾牌而不是临时食物。 */
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

	/** SERVER_STOPPING 前恢复装备，让正常关服存档也始终写入真实装备。 */
	public static void restoreAll() {
		for (Zombie zombie : new ArrayList<>(ACTIVE_SWAPS.keySet())) {
			restore(zombie, true);
		}
	}

	public static void clear() {
		ACTIVE_SWAPS.clear();
	}

	private record HandSwap(InteractionHand hand, ItemStack original) {
	}
}
