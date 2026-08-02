package com.wjz.mobsthinknow.mixin;

import com.wjz.mobsthinknow.ai.nether.GhastArtilleryPolicy;
import com.wjz.mobsthinknow.ai.nether.NetherProfession;
import com.wjz.mobsthinknow.ai.nether.NetherProfessionAccess;
import com.wjz.mobsthinknow.ai.nether.SmartNetherMetrics;
import com.wjz.mobsthinknow.config.ConfigManager;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 将恶魂原版仅四格的垂直索敌带扩展到可见的十六格炮击带。 */
@Mixin(Ghast.class)
public abstract class GhastMixin extends Mob implements NetherProfessionAccess {
	@Unique
	private static final EntityDataAccessor<Byte> mobsthinknow$NETHER_PROFESSION =
		SynchedEntityData.defineId(Ghast.class, EntityDataSerializers.BYTE);

	protected GhastMixin(final EntityType<? extends Mob> type, final Level level) {
		super(type, level);
	}

	@Inject(method = "defineSynchedData", at = @At("TAIL"))
	private void mobsthinknow$defineNetherProfession(
		final SynchedEntityData.Builder builder,
		final CallbackInfo callbackInfo
	) {
		builder.define(mobsthinknow$NETHER_PROFESSION, NetherProfession.NONE.id());
	}

	@Inject(method = "registerGoals", at = @At("TAIL"))
	private void mobsthinknow$installExpandedArtilleryTargeting(final CallbackInfo callbackInfo) {
		Ghast ghast = (Ghast)(Object)this;
		this.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(
			ghast,
			Player.class,
			10,
			true,
			false,
			(target, level) -> {
				var config = ConfigManager.get();
				return GhastArtilleryPolicy.enabled(config)
					&& GhastArtilleryPolicy.withinVerticalBand(ghast.getY(), target.getY());
			}
		));
		SmartNetherMetrics.controllerInstalled();
	}

	@Override
	public NetherProfession mobsthinknow$getNetherProfession() {
		return NetherProfession.fromId(this.entityData.get(mobsthinknow$NETHER_PROFESSION));
	}

	@Override
	public void mobsthinknow$setNetherProfession(final NetherProfession profession) {
		this.entityData.set(
			mobsthinknow$NETHER_PROFESSION,
			(profession == null ? NetherProfession.NONE : profession).id()
		);
	}
}
