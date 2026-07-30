package com.wjz.mobsthinknow.mixin.client;

import com.wjz.mobsthinknow.client.render.GiantCarrierRenderStateAccess;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** 给共用的 ZombieRenderState 增加两个仅由巨人渲染器写入的手部载荷位。 */
@Mixin(ZombieRenderState.class)
public abstract class ZombieRenderStateMixin implements GiantCarrierRenderStateAccess {
	@Unique
	private boolean mobsthinknow$giantRightHandLoaded;
	@Unique
	private boolean mobsthinknow$giantLeftHandLoaded;

	@Override
	public void mobsthinknow$setGiantHeldPayloads(final boolean rightHand, final boolean leftHand) {
		this.mobsthinknow$giantRightHandLoaded = rightHand;
		this.mobsthinknow$giantLeftHandLoaded = leftHand;
	}

	@Override
	public boolean mobsthinknow$isGiantRightHandLoaded() {
		return this.mobsthinknow$giantRightHandLoaded;
	}

	@Override
	public boolean mobsthinknow$isGiantLeftHandLoaded() {
		return this.mobsthinknow$giantLeftHandLoaded;
	}
}
