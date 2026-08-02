package com.wjz.mobsthinknow.ai.enderman;

import com.wjz.mobsthinknow.config.ConfigManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.DropChances;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** 末影人职业的难度化分配、装备冻结、同步访问与存档入口。 */
public final class EndermanProfessionProfile {
	private static final String PROFESSION_TAG = "MobsThinkNowEndermanProfession";

	private EndermanProfessionProfile() {
	}

	public static EndermanProfession get(final EnderMan enderman) {
		return ((EndermanProfessionAccess)enderman).mobsthinknow$getEndermanProfession();
	}

	public static void set(final EnderMan enderman, final EndermanProfession profession) {
		((EndermanProfessionAccess)enderman).mobsthinknow$setEndermanProfession(
			profession == null ? EndermanProfession.NONE : profession
		);
	}

	/**
	 * 原版完成出生流程后调用一次。已有模组装备优先决定职业，空手个体才掷难度化职业并领取装备。
	 */
	public static EndermanProfession assignOnSpawn(
		final EnderMan enderman,
		final DifficultyInstance difficulty,
		final boolean inEndDimension,
		final RandomSource random
	) {
		if (!ConfigManager.get().enabled || !ConfigManager.get().endermanAiEnabled) {
			return EndermanProfession.NONE;
		}
		EndermanProfession existing = professionForExistingEquipment(enderman);
		EndermanProfession profession = existing != EndermanProfession.NONE
			? existing
			: choose(
				difficulty.getDifficulty(),
				EndermanIntelligence.get(enderman),
				inEndDimension,
				random.nextDouble()
			);
		set(enderman, profession);
		applyMissingLoadout(enderman, profession);
		return profession;
	}

	/** 测试指令使用显式职业与固定装备，不受当前维度的自然分布影响。 */
	public static void applyShowcaseLoadout(
		final EnderMan enderman,
		final EndermanProfession profession
	) {
		set(enderman, profession);
		enderman.stopUsingItem();
		enderman.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		enderman.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		applyMissingLoadout(enderman, profession);
	}

	/**
	 * 末地主场把精英概率再抬高 12%；难度与智力继续提高精英比例，但硬上限保持 88%。
	 */
	static EndermanProfession choose(
		final Difficulty difficulty,
		final int intelligence,
		final boolean inEndDimension,
		final double roll
	) {
		double baseEliteChance = switch (difficulty) {
			case PEACEFUL, EASY -> 0.38;
			case NORMAL -> 0.54;
			case HARD -> 0.72;
		};
		double intelligenceShift = (EndermanIntelligence.clamp(intelligence) - 5) * 0.015;
		double eliteChance = Mth.clamp(
			baseEliteChance + intelligenceShift + (inEndDimension ? 0.12 : 0.0),
			0.28,
			0.88
		);
		double boundedRoll = Mth.clamp(Double.isFinite(roll) ? roll : 1.0, 0.0, 1.0);
		if (boundedRoll >= eliteChance) {
			return EndermanProfession.RIFTBLADE;
		}
		double eliteRoll = boundedRoll / eliteChance;
		if (eliteRoll < 0.22) {
			return EndermanProfession.CREEPER_HERALD;
		}
		if (eliteRoll < 0.55) {
			return EndermanProfession.VOID_LANCER;
		}
		return EndermanProfession.VOID_GUARD;
	}

	public static void save(final EnderMan enderman, final ValueOutput output) {
		EndermanProfession profession = get(enderman);
		if (profession != EndermanProfession.NONE) {
			output.putByte(PROFESSION_TAG, profession.id());
		}
	}

	public static void load(final EnderMan enderman, final ValueInput input) {
		byte saved = input.getByteOr(PROFESSION_TAG, (byte)-1);
		EndermanProfession profession = saved >= 0
			? EndermanProfession.fromId(saved)
			: enabled() ? fallbackForOldSave(enderman) : EndermanProfession.NONE;
		set(enderman, profession);
		if (saved < 0 && profession != EndermanProfession.NONE) {
			applyMissingLoadout(enderman, profession);
		}
	}

	private static boolean enabled() {
		return ConfigManager.get().enabled && ConfigManager.get().endermanAiEnabled;
	}

	private static EndermanProfession professionForExistingEquipment(final EnderMan enderman) {
		if (enderman.getMainHandItem().has(DataComponents.KINETIC_WEAPON)) {
			return EndermanProfession.VOID_LANCER;
		}
		if (enderman.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS)) {
			return EndermanProfession.VOID_GUARD;
		}
		if (enderman.getMainHandItem().is(ItemTags.SWORDS)) {
			return EndermanProfession.RIFTBLADE;
		}
		return EndermanProfession.NONE;
	}

	private static EndermanProfession fallbackForOldSave(final EnderMan enderman) {
		EndermanProfession equipped = professionForExistingEquipment(enderman);
		return equipped != EndermanProfession.NONE ? equipped : EndermanProfession.RIFTBLADE;
	}

	private static void applyMissingLoadout(
		final EnderMan enderman,
		final EndermanProfession profession
	) {
		if (enderman.getMainHandItem().isEmpty()) {
			ItemStack mainHand = switch (profession) {
				case RIFTBLADE -> new ItemStack(Items.IRON_SWORD);
				case VOID_GUARD -> new ItemStack(Items.STONE_SWORD);
				case VOID_LANCER -> new ItemStack(Items.IRON_SPEAR);
				case CREEPER_HERALD, NONE -> ItemStack.EMPTY;
			};
			if (!mainHand.isEmpty()) {
				enderman.setItemSlot(EquipmentSlot.MAINHAND, mainHand);
				enderman.setDropChance(EquipmentSlot.MAINHAND, DropChances.DEFAULT_EQUIPMENT_DROP_CHANCE);
			}
		}
		if (profession == EndermanProfession.VOID_GUARD && enderman.getOffhandItem().isEmpty()) {
			enderman.setItemSlot(EquipmentSlot.OFFHAND, EndermanShieldDesign.create(enderman.registryAccess()));
			enderman.setDropChance(EquipmentSlot.OFFHAND, DropChances.DEFAULT_EQUIPMENT_DROP_CHANCE);
		}
	}
}
