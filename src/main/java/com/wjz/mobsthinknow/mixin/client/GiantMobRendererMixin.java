package com.wjz.mobsthinknow.mixin.client;

import com.wjz.mobsthinknow.ai.giant.GiantHand;
import com.wjz.mobsthinknow.ai.giant.GiantTacticsState;
import com.wjz.mobsthinknow.client.render.GiantCarrierRenderStateAccess;
import net.minecraft.client.renderer.entity.GiantMobRenderer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.world.entity.monster.Giant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 把客户端已同步的两手状态机和射手登乘阶段投影到本帧模型快照。 */
@Mixin(GiantMobRenderer.class)
public abstract class GiantMobRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void mobsthinknow$extractTacticalAnimation(
		final Giant giant,
		final ZombieRenderState state,
		final float partialTicks,
		final CallbackInfo callbackInfo
	) {
		GiantCarrierRenderStateAccess carrier = (GiantCarrierRenderStateAccess)state;
		for (GiantHand hand : GiantHand.values()) {
			carrier.mobsthinknow$setGiantHandState(
				hand,
				GiantTacticsState.handPhase(giant, hand),
				GiantTacticsState.handPhaseProgress(giant, hand, partialTicks),
				GiantTacticsState.payloadForHand(giant, hand) != null
			);
		}
		carrier.mobsthinknow$setGiantBoardingState(
			GiantTacticsState.boardingPhase(giant),
			GiantTacticsState.boardingProgress(giant, partialTicks)
		);
	}
}
