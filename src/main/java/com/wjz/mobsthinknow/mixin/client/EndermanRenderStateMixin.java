package com.wjz.mobsthinknow.mixin.client;

import com.wjz.mobsthinknow.client.render.EndermanCarrierRenderStateAccess;
import com.wjz.mobsthinknow.ai.enderman.EndermanProfession;
import com.wjz.mobsthinknow.client.render.EndermanProfessionRenderStateAccess;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** 给原版末影人 RenderState 增加瞬时职业与实体载荷状态，不写入世界存档。 */
@Mixin(EndermanRenderState.class)
public abstract class EndermanRenderStateMixin implements
	EndermanCarrierRenderStateAccess,
	EndermanProfessionRenderStateAccess {
	@Unique
	private boolean mobsthinknow$holdingCreeper;
	@Unique
	private EndermanProfession mobsthinknow$endermanProfession = EndermanProfession.NONE;

	@Override
	public void mobsthinknow$setHoldingCreeper(final boolean holdingCreeper) {
		this.mobsthinknow$holdingCreeper = holdingCreeper;
	}

	@Override
	public boolean mobsthinknow$isHoldingCreeper() {
		return this.mobsthinknow$holdingCreeper;
	}

	@Override
	public void mobsthinknow$setEndermanProfession(final EndermanProfession profession) {
		this.mobsthinknow$endermanProfession = profession == null ? EndermanProfession.NONE : profession;
	}

	@Override
	public EndermanProfession mobsthinknow$getEndermanProfession() {
		return this.mobsthinknow$endermanProfession;
	}
}
