package com.wjz.mobsthinknow.mixin.client;

import com.wjz.mobsthinknow.client.render.EndermanCarrierRenderStateAccess;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** 给原版末影人 RenderState 增加一位瞬时实体载荷状态，不写入世界存档。 */
@Mixin(EndermanRenderState.class)
public abstract class EndermanRenderStateMixin implements EndermanCarrierRenderStateAccess {
	@Unique
	private boolean mobsthinknow$holdingCreeper;

	@Override
	public void mobsthinknow$setHoldingCreeper(final boolean holdingCreeper) {
		this.mobsthinknow$holdingCreeper = holdingCreeper;
	}

	@Override
	public boolean mobsthinknow$isHoldingCreeper() {
		return this.mobsthinknow$holdingCreeper;
	}
}
