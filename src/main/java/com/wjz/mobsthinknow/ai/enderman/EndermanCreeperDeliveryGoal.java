package com.wjz.mobsthinknow.ai.enderman;

import com.wjz.mobsthinknow.ai.creeper.CreeperIntelligence;
import com.wjz.mobsthinknow.ai.spider.CreeperTransportAccess;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 末影人苦力怕投送状态机。它不参与末影人的仇恨建立：只有原版机制已经给出存活的生存玩家目标后，
 * 才会限频搜索一只未进入引信的普通苦力怕，抱到胸前，传送到玩家近身位，放下并点燃载荷。
 * 搜索每轮最多检查 24 个候选，且与蜘蛛运输共用单目标租约，避免两类载具争抢同一实体。
 */
public final class EndermanCreeperDeliveryGoal extends Goal {
	private static final int MAXIMUM_CANDIDATE_CHECKS = 24;
	private static final int RESERVATION_TICKS = 80;
	private static final int APPROACH_TIMEOUT_TICKS = 100;
	private static final int MINIMUM_HOLD_TICKS = 8;
	private static final int ARRIVAL_REVEAL_TICKS = 8;
	private static final int MINIMUM_RETREAT_DELAY_TICKS = 5;
	private static final int MAXIMUM_RETREAT_DELAY_TICKS = 8;
	private static final int RETREAT_RETRY_INTERVAL_TICKS = 2;
	private static final int RETREAT_ESCALATION_TICKS = 20;
	private static final int ABANDON_FINISHED_PAYLOAD_TICKS = 40;
	private static final double SAFE_RETREAT_DISTANCE_SQUARED = 12.0 * 12.0;
	private static final double FRONT_SPREAD_RADIANS = 0.32;
	private static final double REAR_SPREAD_RADIANS = 0.75;
	private static final double MINIMUM_FRONT_ALIGNMENT = 0.90;
	private static final double MAXIMUM_REAR_ALIGNMENT = -0.72;
	private static final double[] DROP_FALLBACK_SPREADS = {0.0, 0.16, -0.16, 0.30, -0.30, 0.46, -0.46};
	private static final double[] DROP_FALLBACK_SCALES = {1.0, 0.88, 1.12};
	private static final double PICKUP_DISTANCE_SQUARED = 2.35 * 2.35;
	private static final double MINIMUM_USE_DISTANCE_SQUARED = 5.0 * 5.0;

	private final EnderMan enderman;
	private final UUID carrierId;
	private @Nullable Creeper creeper;
	private @Nullable Player target;
	private Phase phase = Phase.IDLE;
	private long approachDeadline = Long.MIN_VALUE;
	private long nextSearchAt;
	private long nextDeliveryAt;
	private int phaseTicks;
	private int repathCooldown;
	private int retreatDelayTicks;
	private @Nullable Vec3 retreatThreatPosition;
	private @Nullable DeliverySide deliverySide;
	private boolean payloadReleased;
	private boolean retreatCompleted;
	private boolean finished;

	public EndermanCreeperDeliveryGoal(final EnderMan enderman) {
		this.enderman = enderman;
		this.carrierId = enderman.getUUID();
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		if (!enabled() || !this.enderman.isAlive() || this.enderman.getType() != EntityType.ENDERMAN
			|| this.enderman.getCarriedBlock() != null || !(this.enderman.level() instanceof ServerLevel)) {
			return false;
		}

		Player hostilePlayer = hostilePlayer(this.enderman);
		if (hostilePlayer == null || this.isPlayerStaring(hostilePlayer)) {
			return false;
		}
		Creeper mounted = mountedCreeper(this.enderman);
		if (mounted != null) {
			this.creeper = mounted;
			this.target = hostilePlayer;
			return true;
		}
		if (this.enderman.isVehicle() || this.enderman.distanceToSqr(hostilePlayer) < MINIMUM_USE_DISTANCE_SQUARED) {
			return false;
		}

		long now = this.enderman.level().getGameTime();
		if (now < this.nextDeliveryAt || now < this.nextSearchAt) {
			return false;
		}
		this.nextSearchAt = now + 10L + this.enderman.getRandom().nextInt(11);
		return this.findAndReservePayload(hostilePlayer, now);
	}

	@Override
	public boolean canContinueToUse() {
		if (!this.enderman.isAlive() || this.finished) {
			return false;
		}
		// 载荷已经点燃后，即使目标死亡、切换模式或清除了仇恨，也必须先完成撤离。
		if (this.phase == Phase.RETREATING) {
			return true;
		}
		if (!enabled()) {
			return false;
		}
		Player currentTarget = this.target;
		if (!isValidTarget(currentTarget)) {
			return false;
		}
		// 正面投送抵达后，玩家自然会看见末影人；此时不再让凝视规则中断已提交的放置动作。
		if (this.phase != Phase.ARRIVED
			&& (this.enderman.getTarget() != currentTarget || this.isPlayerStaring(currentTarget))) {
			return false;
		}

		Creeper current = this.creeper;
		if (current == null || !current.isAlive()) {
			return false;
		}
		if (this.phase == Phase.APPROACHING) {
			long now = this.enderman.level().getGameTime();
			return !current.isPassenger()
				&& now <= this.approachDeadline
				&& reservation(current).mobsthinknow$isReservedForCarrier(this.carrierId, now);
		}
		return current.getVehicle() == this.enderman;
	}

	@Override
	public void start() {
		this.phaseTicks = 0;
		this.repathCooldown = 0;
		this.retreatDelayTicks = 0;
		this.retreatThreatPosition = null;
		this.deliverySide = chooseDeliverySide(
			this.enderman.getRandom().nextDouble(),
			ConfigManager.get().endermanCreeperFrontDeliveryChance
		);
		this.payloadReleased = false;
		this.retreatCompleted = false;
		this.finished = false;
		this.enderman.getNavigation().stop();
		if (mountedCreeper(this.enderman) != null) {
			this.phase = Phase.HOLDING;
			this.prepareHeldPayload();
		} else {
			this.phase = Phase.APPROACHING;
		}
	}

	@Override
	public void stop() {
		long now = this.enderman.level().getGameTime();
		Creeper current = this.creeper;
		if (current != null) {
			reservation(current).mobsthinknow$releaseCarrierReservation(this.carrierId);
			if (!this.payloadReleased) {
				if (current.getVehicle() == this.enderman) {
					current.stopRiding();
				}
				if (!current.isIgnited()) {
					current.setSwellDir(-1);
				}
			}
		}
		if (!this.payloadReleased && !this.finished) {
			this.nextDeliveryAt = Math.max(this.nextDeliveryAt, now + 40L);
		}
		this.enderman.getNavigation().stop();
		this.creeper = null;
		this.target = null;
		this.phase = Phase.IDLE;
		this.approachDeadline = Long.MIN_VALUE;
		this.phaseTicks = 0;
		this.repathCooldown = 0;
		this.retreatDelayTicks = 0;
		this.retreatThreatPosition = null;
		this.deliverySide = null;
		this.payloadReleased = false;
		this.retreatCompleted = false;
		this.finished = false;
	}

	@Override
	public void tick() {
		this.phaseTicks++;
		if (this.phase == Phase.RETREATING) {
			Player retreatTarget = this.target;
			if (isValidTarget(retreatTarget)) {
				this.faceTarget(retreatTarget);
			}
			this.tickRetreat();
			return;
		}
		Player currentTarget = this.target;
		if (!isValidTarget(currentTarget)) {
			return;
		}
		this.faceTarget(currentTarget);
		switch (this.phase) {
			case APPROACHING -> this.tickApproach(currentTarget);
			case HOLDING -> this.tickHolding(currentTarget);
			case ARRIVED -> this.tickArrival(currentTarget);
			case RETREATING -> {
			}
			case IDLE -> {
			}
		}
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	public Phase phase() {
		return this.phase;
	}

	public boolean isCarryingPayload() {
		return this.creeper != null && this.creeper.getVehicle() == this.enderman;
	}

	public boolean hasReleasedPayload() {
		return this.payloadReleased;
	}

	public boolean hasCompletedRetreat() {
		return this.retreatCompleted;
	}

	public int retreatDelayTicks() {
		return this.retreatDelayTicks;
	}

	public @Nullable DeliverySide deliverySide() {
		return this.deliverySide;
	}

	private void tickApproach(final Player currentTarget) {
		Creeper current = this.creeper;
		if (current == null) {
			return;
		}
		long now = this.enderman.level().getGameTime();
		if (!reservation(current).mobsthinknow$tryReserveForCarrier(
			this.carrierId,
			now,
			now + RESERVATION_TICKS
		)) {
			this.finished = true;
			return;
		}
		current.setTarget(currentTarget);
		current.setSwellDir(-1);
		if (this.enderman.distanceToSqr(current) <= PICKUP_DISTANCE_SQUARED) {
			this.pickUpPayload(current);
			return;
		}

		if (this.phaseTicks >= 12 && this.phaseTicks % 20 == 0
			&& this.enderman.distanceToSqr(current) > 6.0 * 6.0
			&& this.teleportNear(current.position(), 1.6, 3.0, 6)) {
			this.repathCooldown = 0;
			return;
		}
		if (--this.repathCooldown <= 0 || this.enderman.getNavigation().isDone()) {
			this.repathCooldown = 5;
			this.enderman.getNavigation().moveTo(current, 1.15);
		}
	}

	private void tickHolding(final Player currentTarget) {
		this.prepareHeldPayload();
		int intelligence = EndermanIntelligence.get(this.enderman);
		int holdTicks = Math.max(MINIMUM_HOLD_TICKS, 18 - intelligence);
		if (this.phaseTicks < holdTicks) {
			return;
		}
		if (!this.teleportForDelivery(currentTarget)) {
			this.finished = true;
			return;
		}
		this.phase = Phase.ARRIVED;
		this.phaseTicks = 0;
	}

	private void tickArrival(final Player currentTarget) {
		this.prepareHeldPayload();
		if (this.phaseTicks < ARRIVAL_REVEAL_TICKS) {
			return;
		}
		this.releaseAndIgnite(currentTarget);
		if (!this.payloadReleased) {
			return;
		}
		this.phase = Phase.RETREATING;
		this.phaseTicks = 0;
		this.retreatDelayTicks = MINIMUM_RETREAT_DELAY_TICKS + this.enderman.getRandom().nextInt(
			MAXIMUM_RETREAT_DELAY_TICKS - MINIMUM_RETREAT_DELAY_TICKS + 1
		);
	}

	private void tickRetreat() {
		Vec3 threat = this.retreatThreatPosition;
		if (threat == null) {
			threat = this.target != null ? this.target.position() : this.enderman.position();
		}
		if (horizontalDistanceSquared(this.enderman.position(), threat) >= SAFE_RETREAT_DISTANCE_SQUARED) {
			this.finishRetreat();
			return;
		}
		if (this.phaseTicks < this.retreatDelayTicks) {
			return;
		}
		if ((this.phaseTicks - this.retreatDelayTicks) % RETREAT_RETRY_INTERVAL_TICKS != 0) {
			return;
		}

		int attempts = this.phaseTicks >= RETREAT_ESCALATION_TICKS ? 16 : 8;
		if (this.teleportAwayFrom(threat, attempts)) {
			this.finishRetreat();
			return;
		}

		// 极端封闭地形里先实际跑离载荷，并继续低频重试传送；不会像旧逻辑那样失败一次便宣告完成。
		Vec3 away = horizontalDirection(this.enderman.position().subtract(threat));
		Vec3 fallback = this.enderman.position().add(away.scale(16.0));
		this.enderman.getNavigation().moveTo(fallback.x, fallback.y, fallback.z, 1.35);
		Creeper released = this.creeper;
		if (this.phaseTicks >= ABANDON_FINISHED_PAYLOAD_TICKS && (released == null || !released.isAlive())) {
			this.scheduleDeliveryCooldown();
			this.finished = true;
		}
	}

	private void finishRetreat() {
		this.enderman.getNavigation().stop();
		this.retreatCompleted = true;
		this.scheduleDeliveryCooldown();
		this.finished = true;
	}

	private void scheduleDeliveryCooldown() {
		int intelligence = EndermanIntelligence.get(this.enderman);
		int baseCooldown = ConfigManager.get().endermanCreeperDeliveryCooldownTicks;
		int adjusted = Math.max(40, Mth.floor(baseCooldown * (1.15 - intelligence * 0.03)));
		this.nextDeliveryAt = this.enderman.level().getGameTime()
			+ adjusted
			+ this.enderman.getRandom().nextInt(Math.max(1, adjusted / 4 + 1));
	}

	private boolean findAndReservePayload(final Player hostilePlayer, final long now) {
		MobsThinkNowConfig config = ConfigManager.get();
		int intelligence = EndermanIntelligence.get(this.enderman);
		double intelligenceScale = 0.65 + intelligence * 0.035;
		double radius = config.endermanCreeperSearchRadius * intelligenceScale;
		AABB searchBox = this.enderman.getBoundingBox().inflate(radius, Math.min(radius, 8.0), radius);
		List<Creeper> nearby = this.enderman.level().getEntitiesOfClass(
			Creeper.class,
			searchBox,
			candidate -> candidate.getType() == EntityType.CREEPER && candidate.isAlive()
		);
		SmartEndermanMetrics.carrierSearch();
		Creeper selected = null;
		double bestScore = Double.POSITIVE_INFINITY;
		int checks = 0;
		for (Creeper candidate : nearby) {
			if (checks++ >= MAXIMUM_CANDIDATE_CHECKS) {
				break;
			}
			SmartEndermanMetrics.candidateChecked();
			if (!this.isAvailable(candidate, now)) {
				continue;
			}
			double score = this.enderman.distanceToSqr(candidate) - CreeperIntelligence.get(candidate) * 0.25;
			if (score < bestScore) {
				bestScore = score;
				selected = candidate;
			}
		}
		if (selected == null || !reservation(selected).mobsthinknow$tryReserveForCarrier(
			this.carrierId,
			now,
			now + RESERVATION_TICKS
		)) {
			return false;
		}

		this.creeper = selected;
		this.target = hostilePlayer;
		this.approachDeadline = now + APPROACH_TIMEOUT_TICKS;
		selected.setTarget(hostilePlayer);
		selected.setSwellDir(-1);
		return true;
	}

	private boolean isAvailable(final Creeper candidate, final long now) {
		if (candidate.isPassenger() || candidate.isVehicle() || candidate.isIgnited()
			|| candidate.getSwelling(1.0F) >= 0.20F) {
			return false;
		}
		CreeperTransportAccess access = reservation(candidate);
		return !access.mobsthinknow$isReservedForAnyCarrier(now)
			|| access.mobsthinknow$isReservedForCarrier(this.carrierId, now);
	}

	private void pickUpPayload(final Creeper current) {
		this.enderman.getNavigation().stop();
		current.getNavigation().stop();
		current.setSwellDir(-1);
		if (!current.startRiding(this.enderman, true, true)) {
			this.finished = true;
			return;
		}
		reservation(current).mobsthinknow$releaseCarrierReservation(this.carrierId);
		this.phase = Phase.HOLDING;
		this.phaseTicks = 0;
		this.prepareHeldPayload();
		this.enderman.playSound(SoundEvents.ENDERMAN_TELEPORT, 0.8F, 1.18F);
		if (this.enderman.level() instanceof ServerLevel level) {
			level.sendParticles(
				ParticleTypes.PORTAL,
				this.enderman.getX(),
				this.enderman.getY() + 1.35,
				this.enderman.getZ(),
				24,
				0.45,
				0.75,
				0.45,
				0.12
			);
		}
		SmartEndermanMetrics.payloadPickedUp();
	}

	private void prepareHeldPayload() {
		Creeper current = this.creeper;
		Player currentTarget = this.target;
		if (current == null || currentTarget == null) {
			return;
		}
		current.getNavigation().stop();
		current.setTarget(currentTarget);
		current.setSwellDir(-1);
		current.lookAt(EntityAnchorArgument.Anchor.EYES, currentTarget.getEyePosition());
	}

	private boolean teleportForDelivery(final Player currentTarget) {
		Creeper current = this.creeper;
		if (current == null || current.getVehicle() != this.enderman) {
			return false;
		}
		Vec3 horizontalLook = horizontalDirection(currentTarget.getLookAngle());
		double distance = ConfigManager.get().endermanCreeperDropDistance;
		DeliverySide side = this.deliverySide;
		if (side == null) {
			side = chooseDeliverySide(
				this.enderman.getRandom().nextDouble(),
				ConfigManager.get().endermanCreeperFrontDeliveryChance
			);
			this.deliverySide = side;
		}
		int attempts = 6 + EndermanIntelligence.get(this.enderman);
		Vec3 oldPosition = this.enderman.position();
		for (int attempt = 0; attempt < attempts; attempt++) {
			double maximumSpread = side == DeliverySide.FRONT ? FRONT_SPREAD_RADIANS : REAR_SPREAD_RADIANS;
			double spread = (this.enderman.getRandom().nextDouble() - 0.5) * maximumSpread * 2.0;
			double scale = side == DeliverySide.FRONT
				? 0.88 + this.enderman.getRandom().nextDouble() * 0.14
				: 0.88 + this.enderman.getRandom().nextDouble() * 0.24;
			Vec3 offset = deliveryOffset(horizontalLook, distance, side, spread, scale);
			double x = currentTarget.getX() + offset.x;
			double y = currentTarget.getY() + 2.0 + this.enderman.getRandom().nextInt(4);
			double z = currentTarget.getZ() + offset.z;
			if (!this.enderman.randomTeleport(x, y, z, true)) {
				continue;
			}
			// 原版 randomTeleport 只校验载具自身的窄碰撞箱；胸前载荷会额外向前伸出，必须单独验空。
			this.enderman.positionRider(current);
			if (!this.enderman.level().noCollision(current)
				|| this.enderman.level().containsAnyLiquid(current.getBoundingBox())) {
				this.rollbackDeliveryTeleport(oldPosition, current);
				continue;
			}
			double verticalDifference = Math.abs(this.enderman.getY() - currentTarget.getY());
			Vec3 targetToCarrier = this.enderman.position()
				.subtract(currentTarget.position())
				.multiply(1.0, 0.0, 1.0);
			double horizontalDistanceSquared = targetToCarrier.lengthSqr();
			double alignment = horizontalLook.dot(horizontalDirection(targetToCarrier));
			boolean correctSide = side == DeliverySide.FRONT
				? alignment >= MINIMUM_FRONT_ALIGNMENT && currentTarget.hasLineOfSight(current)
				: alignment <= MAXIMUM_REAR_ALIGNMENT;
			if (correctSide
				&& verticalDifference <= 5.0
				&& horizontalDistanceSquared <= (distance + 2.5) * (distance + 2.5)) {
				this.playTeleportEffects(oldPosition);
				SmartEndermanMetrics.deliveryTeleport();
				return true;
			}
			this.rollbackDeliveryTeleport(oldPosition, current);
		}
		return false;
	}

	private void rollbackDeliveryTeleport(final Vec3 oldPosition, final Creeper current) {
		this.enderman.teleportTo(oldPosition.x, oldPosition.y, oldPosition.z);
		this.enderman.positionRider(current);
	}

	private boolean teleportNear(final Vec3 center, final double minimumRadius, final double maximumRadius, final int attempts) {
		Vec3 oldPosition = this.enderman.position();
		for (int attempt = 0; attempt < attempts; attempt++) {
			double angle = this.enderman.getRandom().nextDouble() * Math.PI * 2.0;
			double radius = Mth.lerp(this.enderman.getRandom().nextDouble(), minimumRadius, maximumRadius);
			double x = center.x + Math.cos(angle) * radius;
			double y = center.y + 2.0 + this.enderman.getRandom().nextInt(4);
			double z = center.z + Math.sin(angle) * radius;
			if (this.enderman.randomTeleport(x, y, z, true)
				&& Math.abs(this.enderman.getY() - center.y) <= 5.0) {
				this.playTeleportEffects(oldPosition);
				return true;
			}
			this.enderman.teleportTo(oldPosition.x, oldPosition.y, oldPosition.z);
		}
		return false;
	}

	private void releaseAndIgnite(final Player currentTarget) {
		Creeper current = this.creeper;
		if (current == null || current.getVehicle() != this.enderman) {
			this.finished = true;
			return;
		}
		Vec3 heldPosition = current.position();
		current.stopRiding();
		Vec3 safeFeet = this.findSafeDropFeet(heldPosition, currentTarget);
		current.snapTo(safeFeet.x, safeFeet.y, safeFeet.z, current.getYRot(), 0.0F);
		current.setTarget(currentTarget);
		current.lookAt(EntityAnchorArgument.Anchor.EYES, currentTarget.getEyePosition());
		current.setSwellDir(1);
		current.ignite();
		this.payloadReleased = true;
		this.retreatThreatPosition = current.position();
		SmartEndermanMetrics.payloadIgnited();
	}

	private Vec3 findSafeDropFeet(final Vec3 preferred, final Player currentTarget) {
		ServerLevel level = (ServerLevel)this.enderman.level();
		Vec3 safe = this.findGroundedDropFeet(level, new Vec3(preferred.x, this.enderman.getY(), preferred.z));
		if (safe != null) {
			return safe;
		}

		// 后备点仍严格保留本轮抽中的正面/后方语义，避免地形回退把 80% 正面投送悄悄改成背刺。
		DeliverySide side = this.deliverySide != null ? this.deliverySide : DeliverySide.FRONT;
		Vec3 horizontalLook = horizontalDirection(currentTarget.getLookAngle());
		double distance = ConfigManager.get().endermanCreeperDropDistance;
		for (double scale : DROP_FALLBACK_SCALES) {
			for (double spread : DROP_FALLBACK_SPREADS) {
				Vec3 offset = deliveryOffset(horizontalLook, distance, side, spread, scale);
				Vec3 sample = currentTarget.position().add(offset).add(0.0, 3.0, 0.0);
				safe = this.findGroundedDropFeet(level, sample);
				if (safe != null) {
					return safe;
				}
			}
		}
		return this.enderman.position();
	}

	private @Nullable Vec3 findGroundedDropFeet(final ServerLevel level, final Vec3 sample) {
		BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos(sample.x, sample.y, sample.z);
		for (int down = 0; down < 7 && feet.getY() > level.getMinY(); down++) {
			BlockPos below = feet.below();
			if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
				double x = feet.getX() + 0.5;
				double y = feet.getY();
				double z = feet.getZ() + 0.5;
				AABB box = EntityType.CREEPER.getSpawnAABB(x, y, z);
				if (level.getBlockState(feet).getFluidState().isEmpty()
					&& level.getBlockState(feet.above()).getFluidState().isEmpty()
					&& level.noCollision(box)) {
					return new Vec3(x, y, z);
				}
				return null;
			}
			feet.move(Direction.DOWN);
		}
		return null;
	}

	private boolean teleportAwayFrom(final Vec3 threat, final int attempts) {
		Vec3 away = horizontalDirection(this.enderman.position().subtract(threat));
		Vec3 oldPosition = this.enderman.position();
		double[] fan = {0.0, 0.35, -0.35, 0.70, -0.70, 1.05, -1.05, Math.PI};
		for (int attempt = 0; attempt < attempts; attempt++) {
			double jitter = (this.enderman.getRandom().nextDouble() - 0.5) * 0.20;
			Vec3 direction = away.yRot((float)(fan[attempt % fan.length] + jitter));
			double distance = 18.0 + this.enderman.getRandom().nextDouble() * 16.0;
			Vec3 candidate = oldPosition.add(direction.scale(distance));
			if (this.enderman.randomTeleport(
				candidate.x,
				candidate.y + this.enderman.getRandom().nextInt(15) - 5,
				candidate.z,
				true
			)) {
				if (horizontalDistanceSquared(this.enderman.position(), threat) >= SAFE_RETREAT_DISTANCE_SQUARED) {
					this.playTeleportEffects(oldPosition);
					return true;
				}
				this.enderman.teleportTo(oldPosition.x, oldPosition.y, oldPosition.z);
			}
		}
		return false;
	}

	private void faceTarget(final Player currentTarget) {
		this.enderman.getLookControl().setLookAt(currentTarget, 80.0F, 70.0F);
		this.enderman.lookAt(EntityAnchorArgument.Anchor.EYES, currentTarget.getEyePosition());
		Creeper current = this.creeper;
		if (current != null && current.getVehicle() == this.enderman) {
			current.lookAt(EntityAnchorArgument.Anchor.EYES, currentTarget.getEyePosition());
		}
	}

	private void playTeleportEffects(final Vec3 oldPosition) {
		if (!(this.enderman.level() instanceof ServerLevel level)) {
			return;
		}
		level.gameEvent(GameEvent.TELEPORT, oldPosition, GameEvent.Context.of(this.enderman));
		if (!this.enderman.isSilent()) {
			level.playSound(
				null,
				oldPosition.x,
				oldPosition.y,
				oldPosition.z,
				SoundEvents.ENDERMAN_TELEPORT,
				this.enderman.getSoundSource(),
				1.0F,
				1.0F
			);
			this.enderman.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
		}
	}

	private boolean isPlayerStaring(final Player player) {
		return LivingEntity.PLAYER_NOT_WEARING_DISGUISE_ITEM.test(player)
			&& this.enderman.isLookingAtMe(player, 0.025, true, false, this.enderman.getEyeY());
	}

	private static @Nullable Player hostilePlayer(final EnderMan enderman) {
		return enderman.getTarget() instanceof Player player && isValidTarget(player) ? player : null;
	}

	private static boolean isValidTarget(final @Nullable Player player) {
		return player != null && player.isAlive() && !player.isCreative() && !player.isSpectator();
	}

	private static @Nullable Creeper mountedCreeper(final EnderMan enderman) {
		return enderman.getFirstPassenger() instanceof Creeper creeper && creeper.getType() == EntityType.CREEPER
			? creeper
			: null;
	}

	public static boolean isCarryingCreeper(final EnderMan enderman) {
		return mountedCreeper(enderman) != null;
	}

	private static CreeperTransportAccess reservation(final Creeper creeper) {
		return (CreeperTransportAccess)creeper;
	}

	static DeliverySide chooseDeliverySide(final double roll, final double frontChance) {
		double chance = Double.isFinite(frontChance) ? Mth.clamp(frontChance, 0.0, 1.0) : 0.0;
		return Double.isFinite(roll) && roll >= 0.0 && roll < chance
			? DeliverySide.FRONT
			: DeliverySide.REAR;
	}

	static Vec3 deliveryOffset(
		final Vec3 horizontalLook,
		final double distance,
		final DeliverySide side,
		final double spreadRadians,
		final double distanceScale
	) {
		Vec3 direction = horizontalDirection(horizontalLook);
		if (side == DeliverySide.REAR) {
			direction = direction.scale(-1.0);
		}
		return direction.yRot((float)spreadRadians).scale(distance * distanceScale);
	}

	private static Vec3 horizontalDirection(final Vec3 vector) {
		Vec3 horizontal = vector.multiply(1.0, 0.0, 1.0);
		return horizontal.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : horizontal.normalize();
	}

	private static double horizontalDistanceSquared(final Vec3 first, final Vec3 second) {
		return first.subtract(second).multiply(1.0, 0.0, 1.0).lengthSqr();
	}

	private static boolean enabled() {
		MobsThinkNowConfig config = ConfigManager.get();
		return config.enabled && config.endermanAiEnabled && config.endermanCreeperDelivery;
	}

	public enum DeliverySide {
		FRONT,
		REAR
	}

	public enum Phase {
		IDLE,
		APPROACHING,
		HOLDING,
		ARRIVED,
		RETREATING
	}
}
