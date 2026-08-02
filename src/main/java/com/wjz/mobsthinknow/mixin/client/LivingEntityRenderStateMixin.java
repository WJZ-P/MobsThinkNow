package com.wjz.mobsthinknow.mixin.client;

import com.wjz.mobsthinknow.ai.nether.NetherProfession;
import com.wjz.mobsthinknow.client.render.NetherProfessionRenderStateAccess;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** 所有下界模型共享的单字节职业渲染快照；非下界实体始终保持 NONE。 */
@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements NetherProfessionRenderStateAccess {
	@Unique
	private NetherProfession mobsthinknow$netherProfession = NetherProfession.NONE;

	@Override
	public void mobsthinknow$setNetherProfession(final NetherProfession profession) {
		this.mobsthinknow$netherProfession = profession == null ? NetherProfession.NONE : profession;
	}

	@Override
	public NetherProfession mobsthinknow$getNetherProfession() {
		return this.mobsthinknow$netherProfession;
	}
}
