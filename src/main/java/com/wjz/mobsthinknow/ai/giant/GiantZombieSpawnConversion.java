package com.wjz.mobsthinknow.ai.giant;

import com.wjz.mobsthinknow.ai.zombie.ZombieIntelligence;
import com.wjz.mobsthinknow.config.ConfigManager;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * 把已经通过概率判定并成功加入世界的普通僵尸，在维度 tick 末事务式替换为巨人。
 * 延迟一拍可避免在 Fabric ENTITY_LOAD 回调内部递归修改实体管理器。
 */
public final class GiantZombieSpawnConversion {
	private static final Map<ServerLevel, List<Zombie>> PENDING = new IdentityHashMap<>();

	private GiantZombieSpawnConversion() {
	}

	public static void queueIfMarked(final Zombie zombie, final ServerLevel level) {
		if (((GiantZombieSpawnAccess)zombie).mobsthinknow$consumeGiantReplacement()) {
			PENDING.computeIfAbsent(level, ignored -> new ArrayList<>()).add(zombie);
		}
	}

	public static void tickLevel(final ServerLevel level) {
		List<Zombie> queued = PENDING.remove(level);
		if (queued == null) {
			return;
		}
		for (Zombie source : queued) {
			tryReplace(level, source);
		}
	}

	public static void unloadLevel(final ServerLevel level) {
		PENDING.remove(level);
	}

	public static void clear() {
		PENDING.clear();
	}

	private static void tryReplace(final ServerLevel level, final Zombie source) {
		if (!source.isAlive() || source.isRemoved() || source.level() != level) {
			return;
		}
		Giant giant = EntityType.GIANT.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (giant == null) {
			return;
		}
		giant.snapTo(source.getX(), source.getY(), source.getZ(), source.getYRot(), source.getXRot());
		giant.setYBodyRot(source.yBodyRot);
		giant.setYHeadRot(source.getYHeadRot());
		if (!level.noBlockCollision(giant, giant.getBoundingBox())
			|| !level.getWorldBorder().isWithinBounds(giant.getBoundingBox())) {
			giant.discard();
			return;
		}

		giant.finalizeSpawn(
			level,
			level.getCurrentDifficultyAt(source.blockPosition()),
			EntitySpawnReason.MOB_SUMMONED,
			null
		);
		GiantZombieProfile.applyAttributes(giant, ConfigManager.get());
		giant.setHealth(giant.getMaxHealth());
		GiantIntelligence.set(giant, ZombieIntelligence.get(source));
		if (source.isPersistenceRequired()) {
			giant.setPersistenceRequired();
		}
		LivingEntity target = source.getTarget();
		if (target != null && target.isAlive()) {
			giant.setTarget(target);
		}
		if (!level.addFreshEntity(giant)) {
			giant.discard();
			return;
		}
		source.discard();
		SmartGiantMetrics.zombieConverted();
	}
}
