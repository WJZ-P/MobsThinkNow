package com.wjz.mobsthinknow.mixin.client;

import com.wjz.mobsthinknow.ai.giant.GiantBoardingPhase;
import com.wjz.mobsthinknow.ai.giant.GiantHand;
import com.wjz.mobsthinknow.ai.giant.GiantHandPhase;
import com.wjz.mobsthinknow.client.render.GiantCarrierRenderStateAccess;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** 给共用的 ZombieRenderState 增加巨人专用的双手阶段和登乘动作快照。 */
@Mixin(ZombieRenderState.class)
public abstract class ZombieRenderStateMixin implements GiantCarrierRenderStateAccess {
	@Unique
	private GiantHandPhase mobsthinknow$rightPhase = GiantHandPhase.EMPTY;
	@Unique
	private GiantHandPhase mobsthinknow$leftPhase = GiantHandPhase.EMPTY;
	@Unique
	private float mobsthinknow$rightProgress;
	@Unique
	private float mobsthinknow$leftProgress;
	@Unique
	private boolean mobsthinknow$rightLoaded;
	@Unique
	private boolean mobsthinknow$leftLoaded;
	@Unique
	private GiantBoardingPhase mobsthinknow$boardingPhase = GiantBoardingPhase.NONE;
	@Unique
	private float mobsthinknow$boardingProgress;

	@Override
	public void mobsthinknow$setGiantHandState(
		final GiantHand hand,
		final GiantHandPhase phase,
		final float progress,
		final boolean loaded
	) {
		if (hand == GiantHand.RIGHT) {
			this.mobsthinknow$rightPhase = phase;
			this.mobsthinknow$rightProgress = progress;
			this.mobsthinknow$rightLoaded = loaded;
		} else {
			this.mobsthinknow$leftPhase = phase;
			this.mobsthinknow$leftProgress = progress;
			this.mobsthinknow$leftLoaded = loaded;
		}
	}

	@Override
	public GiantHandPhase mobsthinknow$getGiantHandPhase(final GiantHand hand) {
		return hand == GiantHand.RIGHT ? this.mobsthinknow$rightPhase : this.mobsthinknow$leftPhase;
	}

	@Override
	public float mobsthinknow$getGiantHandProgress(final GiantHand hand) {
		return hand == GiantHand.RIGHT ? this.mobsthinknow$rightProgress : this.mobsthinknow$leftProgress;
	}

	@Override
	public boolean mobsthinknow$isGiantHandLoaded(final GiantHand hand) {
		return hand == GiantHand.RIGHT ? this.mobsthinknow$rightLoaded : this.mobsthinknow$leftLoaded;
	}

	@Override
	public void mobsthinknow$setGiantBoardingState(final GiantBoardingPhase phase, final float progress) {
		this.mobsthinknow$boardingPhase = phase;
		this.mobsthinknow$boardingProgress = progress;
	}

	@Override
	public GiantBoardingPhase mobsthinknow$getGiantBoardingPhase() {
		return this.mobsthinknow$boardingPhase;
	}

	@Override
	public float mobsthinknow$getGiantBoardingProgress() {
		return this.mobsthinknow$boardingProgress;
	}
}
