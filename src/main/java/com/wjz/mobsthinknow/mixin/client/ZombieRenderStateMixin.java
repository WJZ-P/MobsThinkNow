package com.wjz.mobsthinknow.mixin.client;

import com.wjz.mobsthinknow.ai.giant.GiantBoardingPhase;
import com.wjz.mobsthinknow.ai.giant.GiantHand;
import com.wjz.mobsthinknow.ai.giant.GiantHandPhase;
import com.wjz.mobsthinknow.ai.giant.GiantMeleeAction;
import com.wjz.mobsthinknow.client.render.GiantCarrierRenderStateAccess;
import com.wjz.mobsthinknow.ai.zombie.ZombieBodyAction;
import com.wjz.mobsthinknow.ai.zombie.ZombieProfession;
import com.wjz.mobsthinknow.client.render.ZombieBodyActionRenderStateAccess;
import com.wjz.mobsthinknow.client.render.ZombieProfessionRenderStateAccess;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** 给共用的 ZombieRenderState 增加职业皮肤，以及巨人专用双手、登乘和全身近战动作快照。 */
@Mixin(ZombieRenderState.class)
public abstract class ZombieRenderStateMixin implements
	GiantCarrierRenderStateAccess,
	ZombieProfessionRenderStateAccess,
	ZombieBodyActionRenderStateAccess {
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
	@Unique
	private GiantMeleeAction mobsthinknow$meleeAction = GiantMeleeAction.NONE;
	@Unique
	private float mobsthinknow$meleeProgress;
	@Unique
	private ZombieProfession mobsthinknow$zombieProfession = ZombieProfession.VANILLA;
	@Unique
	private ZombieBodyAction mobsthinknow$bodyAction = ZombieBodyAction.NONE;
	@Unique
	private float mobsthinknow$bodyActionElapsedTicks;
	@Unique
	private ZombieBodyAction mobsthinknow$previousBodyAction = ZombieBodyAction.NONE;
	@Unique
	private float mobsthinknow$previousBodyActionElapsedTicks;
	@Unique
	private float mobsthinknow$bodyActionTransitionElapsedTicks;

	@Override
	public void mobsthinknow$setZombieProfession(final ZombieProfession profession) {
		this.mobsthinknow$zombieProfession = profession == null ? ZombieProfession.VANILLA : profession;
	}

	@Override
	public ZombieProfession mobsthinknow$getZombieProfession() {
		return this.mobsthinknow$zombieProfession;
	}

	@Override
	public void mobsthinknow$setBodyActionState(
		final ZombieBodyAction action,
		final float elapsedTicks,
		final ZombieBodyAction previousAction,
		final float previousElapsedTicks,
		final float transitionElapsedTicks
	) {
		this.mobsthinknow$bodyAction = action == null ? ZombieBodyAction.NONE : action;
		this.mobsthinknow$bodyActionElapsedTicks = Math.max(0.0F, elapsedTicks);
		this.mobsthinknow$previousBodyAction = previousAction == null ? ZombieBodyAction.NONE : previousAction;
		this.mobsthinknow$previousBodyActionElapsedTicks = Math.max(0.0F, previousElapsedTicks);
		this.mobsthinknow$bodyActionTransitionElapsedTicks = Math.max(0.0F, transitionElapsedTicks);
	}

	@Override
	public ZombieBodyAction mobsthinknow$getBodyAction() {
		return this.mobsthinknow$bodyAction;
	}

	@Override
	public float mobsthinknow$getBodyActionElapsedTicks() {
		return this.mobsthinknow$bodyActionElapsedTicks;
	}

	@Override
	public ZombieBodyAction mobsthinknow$getPreviousBodyAction() {
		return this.mobsthinknow$previousBodyAction;
	}

	@Override
	public float mobsthinknow$getPreviousBodyActionElapsedTicks() {
		return this.mobsthinknow$previousBodyActionElapsedTicks;
	}

	@Override
	public float mobsthinknow$getBodyActionTransitionElapsedTicks() {
		return this.mobsthinknow$bodyActionTransitionElapsedTicks;
	}

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

	@Override
	public void mobsthinknow$setGiantMeleeState(final GiantMeleeAction action, final float progress) {
		this.mobsthinknow$meleeAction = action;
		this.mobsthinknow$meleeProgress = progress;
	}

	@Override
	public GiantMeleeAction mobsthinknow$getGiantMeleeAction() {
		return this.mobsthinknow$meleeAction;
	}

	@Override
	public float mobsthinknow$getGiantMeleeProgress() {
		return this.mobsthinknow$meleeProgress;
	}
}
