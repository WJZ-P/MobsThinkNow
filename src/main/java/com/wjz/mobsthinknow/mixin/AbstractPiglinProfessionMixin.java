package com.wjz.mobsthinknow.mixin;

import com.wjz.mobsthinknow.ai.nether.NetherProfession;
import com.wjz.mobsthinknow.ai.nether.NetherProfessionAccess;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 猪灵与猪灵蛮兵共用一个职业同步槽，避免在两个子类重复占用数据编号。 */
@Mixin(AbstractPiglin.class)
public abstract class AbstractPiglinProfessionMixin implements NetherProfessionAccess {
	@Unique
	private static final EntityDataAccessor<Byte> mobsthinknow$NETHER_PROFESSION =
		SynchedEntityData.defineId(AbstractPiglin.class, EntityDataSerializers.BYTE);

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
			((AbstractPiglin)(Object)this).getEntityData().get(mobsthinknow$NETHER_PROFESSION)
		);
	}

	@Override
	public void mobsthinknow$setNetherProfession(final NetherProfession profession) {
		((AbstractPiglin)(Object)this).getEntityData().set(
			mobsthinknow$NETHER_PROFESSION,
			(profession == null ? NetherProfession.NONE : profession).id()
		);
	}
}
