package com.wjz.mobsthinknow.mixin;

import com.wjz.mobsthinknow.ai.nether.NetherProfession;
import com.wjz.mobsthinknow.ai.nether.NetherProfessionAccess;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.monster.Slime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 岩浆怪继承史莱姆的实体数据布局；普通史莱姆保留 NONE，不参与职业逻辑。 */
@Mixin(Slime.class)
public abstract class SlimeProfessionMixin implements NetherProfessionAccess {
	@Unique
	private static final EntityDataAccessor<Byte> mobsthinknow$NETHER_PROFESSION =
		SynchedEntityData.defineId(Slime.class, EntityDataSerializers.BYTE);

	@Inject(method = "defineSynchedData", at = @At("TAIL"))
	private void mobsthinknow$defineNetherProfession(
		final SynchedEntityData.Builder builder,
		final CallbackInfo callbackInfo
	) {
		builder.define(mobsthinknow$NETHER_PROFESSION, NetherProfession.NONE.id());
	}

	@Override
	public NetherProfession mobsthinknow$getNetherProfession() {
		return NetherProfession.fromId(
			((Slime)(Object)this).getEntityData().get(mobsthinknow$NETHER_PROFESSION)
		);
	}

	@Override
	public void mobsthinknow$setNetherProfession(final NetherProfession profession) {
		((Slime)(Object)this).getEntityData().set(
			mobsthinknow$NETHER_PROFESSION,
			(profession == null ? NetherProfession.NONE : profession).id()
		);
	}
}
