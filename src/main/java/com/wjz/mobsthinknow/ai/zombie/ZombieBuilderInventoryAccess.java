package com.wjz.mobsthinknow.ai.zombie;

import net.minecraft.world.item.ItemStack;

/**
 * 由 {@code ZombieMixin} 实现的内部建筑材料槽。
 *
 * <p>它不占用主手、副手或盔甲槽，因此挖掘和垫高不会吞掉僵尸原有的武器、盾牌；
 * 槽位仍随实体存档，并在死亡时像普通战利品一样掉出。</p>
 */
public interface ZombieBuilderInventoryAccess {
	ItemStack mobsthinknow$getBuildingBlocks();

	void mobsthinknow$setBuildingBlocks(ItemStack stack);
}
