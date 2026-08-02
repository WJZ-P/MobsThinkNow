package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.ai.zombie.squad.UtilityClass;
import com.wjz.mobsthinknow.ai.zombie.squad.WeaponClass;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** 职业外观的服务器分类、同步和存档入口。 */
public final class ZombieProfessionProfile {
	private static final String PROFESSION_TAG = "MobsThinkNowProfession";

	private ZombieProfessionProfile() {
	}

	public static ZombieProfession get(final Zombie zombie) {
		return ((ZombieProfessionAccess)zombie).mobsthinknow$getProfession();
	}

	public static void set(final Zombie zombie, final ZombieProfession profession) {
		((ZombieProfessionAccess)zombie).mobsthinknow$setProfession(profession);
	}

	/** 在所有出生装备和工程兵身份已经确定后调用一次。 */
	public static ZombieProfession assignFromLoadout(final Zombie zombie) {
		UtilityClass utility = ZombieSpecialEquipment.utilityClassOf(zombie);
		WeaponClass weapon = ZombieArmory.weaponClassOf(zombie.getMainHandItem());
		ZombieProfession profession = ZombieProfession.choose(
			zombie.getType() == EntityType.ZOMBIE,
			ZombieAirAssault.isAirAssaultLoadout(zombie),
			utility,
			ZombieEngineerProfile.isEngineer(zombie),
			ZombieArmory.hasShield(zombie),
			weapon
		);
		set(zombie, profession);
		return profession;
	}

	public static void save(final Zombie zombie, final ValueOutput output) {
		output.putByte(PROFESSION_TAG, get(zombie).id());
	}

	public static void load(final Zombie zombie, final ValueInput input) {
		byte saved = input.getByteOr(PROFESSION_TAG, (byte)-1);
		if (saved < 0) {
			// 旧存档没有职业字段；装备和工程兵状态已先恢复，安全地只补算一次。
			assignFromLoadout(zombie);
			return;
		}
		set(zombie, ZombieProfession.fromId(saved));
	}
}
