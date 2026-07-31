package com.wjz.mobsthinknow.mixin;

import com.wjz.mobsthinknow.ai.giant.GiantIntelligence;
import com.wjz.mobsthinknow.ai.giant.GiantIntelligenceAccess;
import com.wjz.mobsthinknow.ai.giant.GiantIntelligenceName;
import com.wjz.mobsthinknow.ai.giant.GiantCombatGoals;
import com.wjz.mobsthinknow.ai.giant.GiantBoardingPhase;
import com.wjz.mobsthinknow.ai.giant.GiantHand;
import com.wjz.mobsthinknow.ai.giant.GiantHandPhase;
import com.wjz.mobsthinknow.ai.giant.GiantPassengerLayout;
import com.wjz.mobsthinknow.ai.giant.GiantPayloadThrowGoal;
import com.wjz.mobsthinknow.ai.giant.GiantTacticsAccess;
import com.wjz.mobsthinknow.ai.giant.GiantTacticsState;
import com.wjz.mobsthinknow.ai.giant.GiantZombieProfile;
import com.wjz.mobsthinknow.ai.giant.SmartGiantMetrics;
import com.wjz.mobsthinknow.ai.zombie.squad.SquadMemberHeartbeat;
import com.wjz.mobsthinknow.ai.zombie.squad.SquadPreparationGoal;
import com.wjz.mobsthinknow.ai.zombie.squad.SquadTheatrics;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 为原版无 Goal 的 Giant 补齐重装战斗、混编小队、头顶射手和双手投送能力。 */
@Mixin(Giant.class)
public abstract class GiantMixin extends Monster implements GiantIntelligenceAccess, GiantTacticsAccess {
	@Unique
	private static final String mobsthinknow$INTELLIGENCE_TAG = "MobsThinkNowIntelligence";
	@Unique
	private static final String mobsthinknow$RIGHT_PAYLOAD_TAG = "MobsThinkNowRightPayload";
	@Unique
	private static final String mobsthinknow$LEFT_PAYLOAD_TAG = "MobsThinkNowLeftPayload";
	@Unique
	private static final EntityDataAccessor<Integer> mobsthinknow$RIGHT_PAYLOAD_ID =
		SynchedEntityData.defineId(Giant.class, EntityDataSerializers.INT);
	@Unique
	private static final EntityDataAccessor<Integer> mobsthinknow$LEFT_PAYLOAD_ID =
		SynchedEntityData.defineId(Giant.class, EntityDataSerializers.INT);
	@Unique
	private static final EntityDataAccessor<Byte> mobsthinknow$RIGHT_HAND_PHASE =
		SynchedEntityData.defineId(Giant.class, EntityDataSerializers.BYTE);
	@Unique
	private static final EntityDataAccessor<Byte> mobsthinknow$LEFT_HAND_PHASE =
		SynchedEntityData.defineId(Giant.class, EntityDataSerializers.BYTE);
	@Unique
	private static final EntityDataAccessor<Integer> mobsthinknow$RIGHT_PHASE_START =
		SynchedEntityData.defineId(Giant.class, EntityDataSerializers.INT);
	@Unique
	private static final EntityDataAccessor<Integer> mobsthinknow$LEFT_PHASE_START =
		SynchedEntityData.defineId(Giant.class, EntityDataSerializers.INT);
	@Unique
	private static final EntityDataAccessor<Integer> mobsthinknow$BOARDING_RIDER_ID =
		SynchedEntityData.defineId(Giant.class, EntityDataSerializers.INT);
	@Unique
	private static final EntityDataAccessor<Byte> mobsthinknow$BOARDING_PHASE =
		SynchedEntityData.defineId(Giant.class, EntityDataSerializers.BYTE);
	@Unique
	private static final EntityDataAccessor<Integer> mobsthinknow$BOARDING_PHASE_START =
		SynchedEntityData.defineId(Giant.class, EntityDataSerializers.INT);
	@Unique
	private int mobsthinknow$giantIntelligence;
	@Unique
	private @Nullable UUID mobsthinknow$rightPayloadUuid;
	@Unique
	private @Nullable UUID mobsthinknow$leftPayloadUuid;
	@Unique
	private @Nullable UUID mobsthinknow$boardingRiderUuid;

	protected GiantMixin(final EntityType<? extends Monster> type, final Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(mobsthinknow$RIGHT_PAYLOAD_ID, 0);
		builder.define(mobsthinknow$LEFT_PAYLOAD_ID, 0);
		builder.define(mobsthinknow$RIGHT_HAND_PHASE, (byte)GiantHandPhase.EMPTY.ordinal());
		builder.define(mobsthinknow$LEFT_HAND_PHASE, (byte)GiantHandPhase.EMPTY.ordinal());
		builder.define(mobsthinknow$RIGHT_PHASE_START, 0);
		builder.define(mobsthinknow$LEFT_PHASE_START, 0);
		builder.define(mobsthinknow$BOARDING_RIDER_ID, 0);
		builder.define(mobsthinknow$BOARDING_PHASE, (byte)GiantBoardingPhase.NONE.ordinal());
		builder.define(mobsthinknow$BOARDING_PHASE_START, 0);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void mobsthinknow$initializeGiant(
		final EntityType<? extends Giant> type,
		final Level level,
		final CallbackInfo callbackInfo
	) {
		if (!(level instanceof ServerLevel)) {
			return;
		}
		Giant giant = (Giant)(Object)this;
		MobsThinkNowConfig config = ConfigManager.get();
		GiantZombieProfile.applyAttributes(giant, config);
		giant.setHealth(giant.getMaxHealth());
		GiantIntelligenceName.apply(giant, this.mobsthinknow$getGiantIntelligence());

		this.goalSelector.addGoal(0, new FloatGoal(giant));
		this.goalSelector.addGoal(1, new GiantPayloadThrowGoal(giant));
		this.goalSelector.addGoal(2, new SquadPreparationGoal(giant, 0.86));
		this.goalSelector.addGoal(3, new GiantCombatGoals.Melee(giant, 0.92, true));
		this.goalSelector.addGoal(7, new GiantCombatGoals.Stroll(giant, 0.70));
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(giant, Player.class, 16.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(giant));

		this.targetSelector.addGoal(1, new GiantCombatGoals.HurtBy(giant));
		this.targetSelector.addGoal(2, new GiantCombatGoals.Nearest<>(giant, Player.class, true));
		this.targetSelector.addGoal(3, new GiantCombatGoals.Nearest<>(giant, AbstractVillager.class, false));
		this.targetSelector.addGoal(3, new GiantCombatGoals.Nearest<>(giant, IronGolem.class, true));
		SmartGiantMetrics.goalInstalled();
	}

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt(mobsthinknow$INTELLIGENCE_TAG, this.mobsthinknow$getGiantIntelligence());
		if (this.mobsthinknow$rightPayloadUuid != null) {
			output.putString(mobsthinknow$RIGHT_PAYLOAD_TAG, this.mobsthinknow$rightPayloadUuid.toString());
		}
		if (this.mobsthinknow$leftPayloadUuid != null) {
			output.putString(mobsthinknow$LEFT_PAYLOAD_TAG, this.mobsthinknow$leftPayloadUuid.toString());
		}
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		int saved = input.getIntOr(mobsthinknow$INTELLIGENCE_TAG, 0);
		this.mobsthinknow$giantIntelligence = saved == 0 ? 0 : GiantIntelligence.clamp(saved);
		this.mobsthinknow$rightPayloadUuid = mobsthinknow$readUuid(input, mobsthinknow$RIGHT_PAYLOAD_TAG);
		this.mobsthinknow$leftPayloadUuid = mobsthinknow$readUuid(input, mobsthinknow$LEFT_PAYLOAD_TAG);
		// 运行时实体 ID 和动画阶段不能跨世界加载；乘客完成加载后 reconcile 会恢复 HOLDING。
		this.entityData.set(mobsthinknow$RIGHT_PAYLOAD_ID, 0);
		this.entityData.set(mobsthinknow$LEFT_PAYLOAD_ID, 0);
		this.entityData.set(mobsthinknow$RIGHT_HAND_PHASE, (byte)GiantHandPhase.EMPTY.ordinal());
		this.entityData.set(mobsthinknow$LEFT_HAND_PHASE, (byte)GiantHandPhase.EMPTY.ordinal());
		this.mobsthinknow$boardingRiderUuid = null;
		this.entityData.set(mobsthinknow$BOARDING_RIDER_ID, 0);
		this.entityData.set(mobsthinknow$BOARDING_PHASE, (byte)GiantBoardingPhase.NONE.ordinal());
		Giant giant = (Giant)(Object)this;
		GiantZombieProfile.applyAttributes(giant, ConfigManager.get());
		SquadTheatrics.stripLeftoverRoleTag(giant);
		GiantIntelligenceName.apply(giant, this.mobsthinknow$getGiantIntelligence());
	}

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		GiantTacticsState.reconcile((Giant)(Object)this);
		MobsThinkNowConfig config = ConfigManager.get();
		SquadMemberHeartbeat.tick(level, (Giant)(Object)this, config.giantZombieAiEnabled);
	}

	@Override
	protected boolean canAddPassenger(final Entity passenger) {
		Giant giant = (Giant)(Object)this;
		if (GiantPassengerLayout.isHeadRider(passenger)) {
			return GiantPassengerLayout.canAcceptHeadRider(giant, passenger);
		}
		if (GiantPassengerLayout.isPayload(passenger)) {
			return GiantTacticsState.handFor(giant, passenger) != null
				|| GiantPassengerLayout.hasFreeHand(giant);
		}
		return super.canAddPassenger(passenger);
	}

	@Override
	public Vec3 getPassengerRidingPosition(final Entity passenger) {
		Giant giant = (Giant)(Object)this;
		return GiantPassengerLayout.isManagedPassenger(passenger)
			? GiantPassengerLayout.ridingPosition(giant, passenger)
			: super.getPassengerRidingPosition(passenger);
	}

	/** 头顶射手和双手载荷都不是驾驶者，巨人的 MOVE/LOOK 始终由自己的 Goal 控制。 */
	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		Entity first = this.getFirstPassenger();
		return first != null && GiantPassengerLayout.isManagedPassenger(first)
			? null
			: super.getControllingPassenger();
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ZOMBIE_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(final DamageSource source) {
		return SoundEvents.ZOMBIE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ZOMBIE_DEATH;
	}

	@Override
	protected void playStepSound(final BlockPos pos, final BlockState blockState) {
		this.playSound(SoundEvents.ZOMBIE_STEP, 0.55F, 0.62F);
	}

	@Override
	public int mobsthinknow$getGiantIntelligence() {
		if (this.mobsthinknow$giantIntelligence == 0) {
			this.mobsthinknow$giantIntelligence = GiantIntelligence.roll(this.level().getDifficulty(), this.random);
		}
		return this.mobsthinknow$giantIntelligence;
	}

	@Override
	public void mobsthinknow$setGiantIntelligence(final int intelligence) {
		this.mobsthinknow$giantIntelligence = GiantIntelligence.clamp(intelligence);
	}

	@Override
	public @Nullable UUID mobsthinknow$getPayloadUuid(final GiantHand hand) {
		return hand == GiantHand.RIGHT ? this.mobsthinknow$rightPayloadUuid : this.mobsthinknow$leftPayloadUuid;
	}

	@Override
	public void mobsthinknow$setPayloadUuid(final GiantHand hand, final @Nullable UUID uuid) {
		if (hand == GiantHand.RIGHT) {
			this.mobsthinknow$rightPayloadUuid = uuid;
		} else {
			this.mobsthinknow$leftPayloadUuid = uuid;
		}
	}

	@Override
	public int mobsthinknow$getPayloadEntityId(final GiantHand hand) {
		return this.entityData.get(hand == GiantHand.RIGHT
			? mobsthinknow$RIGHT_PAYLOAD_ID
			: mobsthinknow$LEFT_PAYLOAD_ID);
	}

	@Override
	public void mobsthinknow$setPayloadEntityId(final GiantHand hand, final int entityId) {
		this.entityData.set(hand == GiantHand.RIGHT
			? mobsthinknow$RIGHT_PAYLOAD_ID
			: mobsthinknow$LEFT_PAYLOAD_ID, entityId);
	}

	@Override
	public GiantHandPhase mobsthinknow$getHandPhase(final GiantHand hand) {
		byte id = this.entityData.get(hand == GiantHand.RIGHT
			? mobsthinknow$RIGHT_HAND_PHASE
			: mobsthinknow$LEFT_HAND_PHASE);
		return GiantHandPhase.fromId(Byte.toUnsignedInt(id));
	}

	@Override
	public void mobsthinknow$setHandPhase(final GiantHand hand, final GiantHandPhase phase) {
		this.entityData.set(hand == GiantHand.RIGHT
			? mobsthinknow$RIGHT_HAND_PHASE
			: mobsthinknow$LEFT_HAND_PHASE, (byte)phase.ordinal());
	}

	@Override
	public int mobsthinknow$getHandPhaseStartTick(final GiantHand hand) {
		return this.entityData.get(hand == GiantHand.RIGHT
			? mobsthinknow$RIGHT_PHASE_START
			: mobsthinknow$LEFT_PHASE_START);
	}

	@Override
	public void mobsthinknow$setHandPhaseStartTick(final GiantHand hand, final int tick) {
		this.entityData.set(hand == GiantHand.RIGHT
			? mobsthinknow$RIGHT_PHASE_START
			: mobsthinknow$LEFT_PHASE_START, tick);
	}

	@Override
	public @Nullable UUID mobsthinknow$getBoardingRiderUuid() {
		return this.mobsthinknow$boardingRiderUuid;
	}

	@Override
	public void mobsthinknow$setBoardingRiderUuid(final @Nullable UUID uuid) {
		this.mobsthinknow$boardingRiderUuid = uuid;
	}

	@Override
	public int mobsthinknow$getBoardingRiderEntityId() {
		return this.entityData.get(mobsthinknow$BOARDING_RIDER_ID);
	}

	@Override
	public void mobsthinknow$setBoardingRiderEntityId(final int entityId) {
		this.entityData.set(mobsthinknow$BOARDING_RIDER_ID, entityId);
	}

	@Override
	public GiantBoardingPhase mobsthinknow$getBoardingPhase() {
		return GiantBoardingPhase.fromId(Byte.toUnsignedInt(this.entityData.get(mobsthinknow$BOARDING_PHASE)));
	}

	@Override
	public void mobsthinknow$setBoardingPhase(final GiantBoardingPhase phase) {
		this.entityData.set(mobsthinknow$BOARDING_PHASE, (byte)phase.ordinal());
	}

	@Override
	public int mobsthinknow$getBoardingPhaseStartTick() {
		return this.entityData.get(mobsthinknow$BOARDING_PHASE_START);
	}

	@Override
	public void mobsthinknow$setBoardingPhaseStartTick(final int tick) {
		this.entityData.set(mobsthinknow$BOARDING_PHASE_START, tick);
	}

	@Unique
	private static @Nullable UUID mobsthinknow$readUuid(final ValueInput input, final String key) {
		String raw = input.getStringOr(key, "");
		if (raw.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(raw);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}
}
