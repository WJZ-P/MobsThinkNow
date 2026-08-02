package com.wjz.mobsthinknow.mixin;

import com.wjz.mobsthinknow.ai.nether.NetherProfessionProfile;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在 Mob 的公共生命周期边界统一处理下界职业。
 *
 * <p>这里只做常数次类型分派；职业同步槽仍只安装在对应实体家族，普通生物不会增加跟踪数据。</p>
 */
@Mixin(Mob.class)
public abstract class NetherProfessionLifecycleMixin {
	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void mobsthinknow$assignNetherProfession(
		final ServerLevelAccessor level,
		final DifficultyInstance difficulty,
		final EntitySpawnReason spawnReason,
		final @Nullable SpawnGroupData groupData,
		final CallbackInfoReturnable<SpawnGroupData> callbackInfo
	) {
		Mob mob = (Mob)(Object)this;
		if (NetherProfessionProfile.supports(mob)
			&& !NetherProfessionProfile.requiresLateSpawnAssignment(mob)) {
			NetherProfessionProfile.assignOnSpawn(mob, difficulty, level.getRandom());
		}
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void mobsthinknow$saveNetherProfession(
		final ValueOutput output,
		final CallbackInfo callbackInfo
	) {
		Mob mob = (Mob)(Object)this;
		if (NetherProfessionProfile.supports(mob)) {
			NetherProfessionProfile.save(mob, output);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void mobsthinknow$loadNetherProfession(
		final ValueInput input,
		final CallbackInfo callbackInfo
	) {
		Mob mob = (Mob)(Object)this;
		if (NetherProfessionProfile.supports(mob)) {
			NetherProfessionProfile.load(mob, input);
		}
	}
}
