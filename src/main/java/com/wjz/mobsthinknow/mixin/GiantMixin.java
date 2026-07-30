package com.wjz.mobsthinknow.mixin;

import com.wjz.mobsthinknow.ai.giant.GiantIntelligence;
import com.wjz.mobsthinknow.ai.giant.GiantIntelligenceAccess;
import com.wjz.mobsthinknow.ai.giant.GiantIntelligenceName;
import com.wjz.mobsthinknow.ai.giant.GiantCombatGoals;
import com.wjz.mobsthinknow.ai.giant.GiantPassengerLayout;
import com.wjz.mobsthinknow.ai.giant.GiantPayloadThrowGoal;
import com.wjz.mobsthinknow.ai.giant.GiantZombieProfile;
import com.wjz.mobsthinknow.ai.giant.SmartGiantMetrics;
import com.wjz.mobsthinknow.ai.zombie.squad.SquadMemberHeartbeat;
import com.wjz.mobsthinknow.ai.zombie.squad.SquadPreparationGoal;
import com.wjz.mobsthinknow.ai.zombie.squad.SquadTheatrics;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.core.BlockPos;
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
public abstract class GiantMixin extends Monster implements GiantIntelligenceAccess {
	@Unique
	private static final String mobsthinknow$INTELLIGENCE_TAG = "MobsThinkNowIntelligence";
	@Unique
	private int mobsthinknow$giantIntelligence;

	protected GiantMixin(final EntityType<? extends Monster> type, final Level level) {
		super(type, level);
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
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		int saved = input.getIntOr(mobsthinknow$INTELLIGENCE_TAG, 0);
		this.mobsthinknow$giantIntelligence = saved == 0 ? 0 : GiantIntelligence.clamp(saved);
		Giant giant = (Giant)(Object)this;
		GiantZombieProfile.applyAttributes(giant, ConfigManager.get());
		SquadTheatrics.stripLeftoverRoleTag(giant);
		GiantIntelligenceName.apply(giant, this.mobsthinknow$getGiantIntelligence());
	}

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		MobsThinkNowConfig config = ConfigManager.get();
		SquadMemberHeartbeat.tick(level, (Giant)(Object)this, config.giantZombieAiEnabled);
	}

	@Override
	protected boolean canAddPassenger(final Entity passenger) {
		Giant giant = (Giant)(Object)this;
		if (GiantPassengerLayout.isHeadRider(passenger)) {
			return GiantPassengerLayout.hasFreeHeadSeat(giant);
		}
		if (GiantPassengerLayout.isPayload(passenger)) {
			return GiantPassengerLayout.hasFreeHand(giant);
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
}
