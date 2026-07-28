package com.wjz.mobsthinknow;

import com.wjz.mobsthinknow.ai.zombie.ZombieArmory;
import com.wjz.mobsthinknow.ai.zombie.ZombieBuilderInventory;
import com.wjz.mobsthinknow.ai.zombie.ZombieEngineerEquipment;
import com.wjz.mobsthinknow.ai.zombie.ZombieFoodEquipment;
import com.wjz.mobsthinknow.ai.zombie.ZombieFireSupportMemory;
import com.wjz.mobsthinknow.ai.zombie.ZombieFluidThreatMemory;
import com.wjz.mobsthinknow.ai.zombie.ZombieIntelligenceName;
import com.wjz.mobsthinknow.ai.zombie.ZombieRetreatMemory;
import com.wjz.mobsthinknow.ai.zombie.ZombieShieldMemory;
import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import com.wjz.mobsthinknow.command.MtnCommands;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MobsThinkNow implements ModInitializer {
	public static final String MOD_ID = "mobsthinknow";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ConfigManager.load();
		MtnCommands.register();
		// 协调器统一在每个维度 tick 的末尾做一次决策，保证本 tick 的所有僵尸心跳已经收齐。
		ServerTickEvents.END_LEVEL_TICK.register(ZombieSquadCoordinator::tickLevel);
		ServerLevelEvents.UNLOAD.register((server, level) -> {
			ZombieSquadCoordinator.unloadLevel(level);
			ZombieFireSupportMemory.clearLevel(level);
			ZombieFluidThreatMemory.clearLevel(level);
		});
		// 关服保存前结束最多几十 tick 的临时换手，确保存档里永远是原武器/盾牌。
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ZombieEngineerEquipment.restoreAll();
			ZombieFoodEquipment.restoreAll();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ZombieSquadCoordinator.clearAll();
			ZombieArmory.clearShieldState();
			ZombieFoodEquipment.clear();
			ZombieEngineerEquipment.clear();
			ZombieRetreatMemory.clear();
			ZombieShieldMemory.clear();
			ZombieFireSupportMemory.clear();
			ZombieFluidThreatMemory.clear();
		});
		// 在 die() 记录“Named entity died”日志之前恢复职业名牌；只做表现清理，不改变死亡结果。
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
			if (entity instanceof Zombie zombie) {
				ZombieRetreatMemory.discard(zombie);
				ZombieShieldMemory.discard(zombie);
				ZombieFireSupportMemory.discard(zombie);
				ZombieFluidThreatMemory.discard(zombie);
				ZombieSquadCoordinator.onZombieDying(zombie);
				ZombieFoodEquipment.restore(zombie, true);
				ZombieEngineerEquipment.restore(zombie, true);
				ZombieIntelligenceName.removeSyntheticMarker(zombie);
			}
			return true;
		});
		// ALLOW_DEATH 仍可能被后注册的复活机制取消；材料只在不可撤销的死亡事件中掉落，避免复制或误掉落。
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (entity instanceof Zombie zombie) {
				ZombieBuilderInventory.dropAll(zombie);
			}
		});
		ServerLivingEntityEvents.MOB_CONVERSION.register((previous, converted, conversionContext) -> {
			if (previous instanceof Zombie oldZombie && converted instanceof Zombie newZombie) {
				ZombieBuilderInventory.transfer(oldZombie, newZombie);
			}
		});
		// 同一入口先快照生命值，并在斧击举盾僵尸时补上原版只对玩家生效的破盾冷却。
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, damageSource, damageAmount) -> {
			if (entity instanceof Zombie zombie) {
				MobsThinkNowConfig config = ConfigManager.get();
				// 必须先快照生命值：ZombieArmory 可能因斧击收盾，随后原版才会结算实际扣血。
				ZombieRetreatMemory.beginDamage(zombie);
				// 原版会给“伤害被盾牌完全归零”的实体也写入十 tick 受击动画，先保存本次伤害前的计时。
				ZombieShieldMemory.beginDamageAnimation(zombie);
				ZombieArmory.onZombieAttacked(zombie, damageSource, config);
				// 事件驱动地通知至多一支小队；水桶兵因此无需逐 tick 扫描“谁正在挨打”。
				if (damageSource.getEntity() instanceof LivingEntity attacker) {
					ZombieSquadCoordinator.onSquadMemberAttacked(zombie, attacker);
				}
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, damageSource, baseDamage, damage, blocked) -> {
			if (entity instanceof Zombie zombie) {
				// 仅在盾牌成功把最终伤害完全挡为零时恢复旧动画；真实受伤继续使用原版反馈。
				ZombieShieldMemory.finishDamageAnimation(zombie, damage, blocked);
				// 等原版结算确认“盾牌参与且零实伤”后再发反击信号，背刺与破盾不冒充成功格挡。
				ZombieShieldMemory.recordSuccessfulBlock(
					zombie,
					damageSource,
					damage,
					blocked,
					ConfigManager.get()
				);
				ZombieRetreatMemory.finishDamage(zombie, damageSource);
			}
		});
		LOGGER.info("Mobs Think Now initialized for Minecraft 26.1.2.");
	}
}
