package com.wjz.mobsthinknow.mixin;

import com.wjz.mobsthinknow.ai.enderman.EndermanProfession;
import com.wjz.mobsthinknow.ai.enderman.EndermanProfessionProfile;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 在公共出生尾部为普通末影人冻结职业；不改变原版仇恨和生成数量。 */
@Mixin(Mob.class)
public abstract class EndermanProfessionLifecycleMixin {
	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void mobsthinknow$assignEndermanProfession(
		final ServerLevelAccessor level,
		final DifficultyInstance difficulty,
		final EntitySpawnReason spawnReason,
		final @Nullable SpawnGroupData groupData,
		final CallbackInfoReturnable<SpawnGroupData> callbackInfo
	) {
		Mob mob = (Mob)(Object)this;
		if (mob instanceof EnderMan enderman
			&& EndermanProfessionProfile.get(enderman) == EndermanProfession.NONE) {
			EndermanProfessionProfile.assignOnSpawn(
				enderman,
				difficulty,
				Level.END.equals(level.getLevel().dimension()),
				level.getRandom()
			);
		}
	}
}
