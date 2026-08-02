package com.wjz.mobsthinknow.ai.enderman;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/** 有限候选、可回滚的职业战斗传送；每次调用最多尝试八个落点。 */
public final class EndermanCombatTeleport {
	private EndermanCombatTeleport() {
	}

	public static boolean tryFlank(
		final EnderMan enderman,
		final LivingEntity target,
		final double distance,
		final int attempts
	) {
		Vec3 forward = horizontalUnit(target.getLookAngle(), target.position().subtract(enderman.position()));
		Vec3 lateral = new Vec3(-forward.z, 0.0, forward.x);
		int preferredSide = (enderman.getId() & 1) == 0 ? 1 : -1;
		Vec3 oldPosition = enderman.position();
		int boundedAttempts = Mth.clamp(attempts, 1, 8);
		for (int attempt = 0; attempt < boundedAttempts; attempt++) {
			int side = (attempt & 1) == 0 ? preferredSide : -preferredSide;
			double rearWeight = 0.68 + enderman.getRandom().nextDouble() * 0.16;
			double sideWeight = Math.sqrt(Math.max(0.0, 1.0 - rearWeight * rearWeight));
			Vec3 direction = forward.scale(-rearWeight).add(lateral.scale(sideWeight * side));
			double spread = (enderman.getRandom().nextDouble() - 0.5) * 0.22;
			direction = direction.yRot((float)spread).normalize();
			double radius = distance * (0.88 + enderman.getRandom().nextDouble() * 0.20);
			Vec3 candidate = target.position().add(direction.scale(radius));
			double candidateY = target.getY() + enderman.getRandom().nextInt(5) - 1;
			if (tryCandidate(enderman, target, oldPosition, candidate.x, candidateY, candidate.z)) {
				SmartEndermanMetrics.combatTeleport();
				return true;
			}
		}
		return false;
	}

	public static boolean tryRetreat(
		final EnderMan enderman,
		final LivingEntity target,
		final double distance,
		final int attempts
	) {
		Vec3 away = horizontalUnit(enderman.position().subtract(target.position()), enderman.getLookAngle());
		Vec3 oldPosition = enderman.position();
		int boundedAttempts = Mth.clamp(attempts, 1, 8);
		for (int attempt = 0; attempt < boundedAttempts; attempt++) {
			double fan = ((attempt + 1) / 2) * 0.28 * ((attempt & 1) == 0 ? 1.0 : -1.0);
			Vec3 direction = away.yRot((float)fan);
			double radius = distance * (0.90 + enderman.getRandom().nextDouble() * 0.25);
			Vec3 candidate = oldPosition.add(direction.scale(radius));
			double candidateY = oldPosition.y + enderman.getRandom().nextInt(7) - 2;
			if (tryCandidate(enderman, target, oldPosition, candidate.x, candidateY, candidate.z)) {
				SmartEndermanMetrics.combatTeleport();
				return true;
			}
		}
		return false;
	}

	static Vec3 horizontalUnit(final Vec3 preferred, final Vec3 fallback) {
		Vec3 horizontal = preferred.multiply(1.0, 0.0, 1.0);
		if (horizontal.lengthSqr() < 1.0E-6) {
			horizontal = fallback.multiply(1.0, 0.0, 1.0);
		}
		return horizontal.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : horizontal.normalize();
	}

	private static boolean tryCandidate(
		final EnderMan enderman,
		final LivingEntity target,
		final Vec3 oldPosition,
		final double x,
		final double y,
		final double z
	) {
		if (!(enderman.level() instanceof ServerLevel level)
			|| !enderman.randomTeleport(x, y, z, false)) {
			return false;
		}
		if (level.containsAnyLiquid(enderman.getBoundingBox())
			|| enderman.distanceToSqr(target) < 1.75 * 1.75
			|| !enderman.hasLineOfSight(target)) {
			enderman.teleportTo(oldPosition.x, oldPosition.y, oldPosition.z);
			return false;
		}
		playEffects(level, enderman, oldPosition);
		return true;
	}

	private static void playEffects(
		final ServerLevel level,
		final EnderMan enderman,
		final Vec3 oldPosition
	) {
		level.gameEvent(GameEvent.TELEPORT, oldPosition, GameEvent.Context.of(enderman));
		level.sendParticles(ParticleTypes.PORTAL, oldPosition.x, oldPosition.y + 1.5, oldPosition.z, 24, 0.4, 0.8, 0.4, 0.15);
		level.sendParticles(ParticleTypes.PORTAL, enderman.getX(), enderman.getY() + 1.5, enderman.getZ(), 24, 0.4, 0.8, 0.4, 0.15);
		if (!enderman.isSilent()) {
			level.playSound(null, oldPosition.x, oldPosition.y, oldPosition.z, SoundEvents.ENDERMAN_TELEPORT, enderman.getSoundSource(), 1.0F, 1.0F);
			enderman.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
		}
	}
}
