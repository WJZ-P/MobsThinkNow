package com.wjz.mobsthinknow.mixin;

import com.wjz.mobsthinknow.ai.zombie.SmartZombieAttackGoal;
import com.wjz.mobsthinknow.ai.zombie.SmartZombieMetrics;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Zombie.class)
public abstract class ZombieMixin extends Monster {
	protected ZombieMixin(final EntityType<? extends Monster> type, final Level level) {
		super(type, level);
	}

	@Inject(method = "addBehaviourGoals", at = @At("TAIL"))
	private void mobsthinknow$replaceZombieAttackGoal(final CallbackInfo callbackInfo) {
		Zombie zombie = (Zombie)(Object)this;
		if (zombie.getType() != EntityType.ZOMBIE) {
			return;
		}

		boolean hasVanillaAttackGoal = this.goalSelector
			.getAvailableGoals()
			.stream()
			.anyMatch(wrapped -> wrapped.getGoal().getClass() == ZombieAttackGoal.class);
		if (!hasVanillaAttackGoal) {
			return;
		}

		this.goalSelector.removeAllGoals(goal -> goal.getClass() == ZombieAttackGoal.class);
		this.goalSelector.addGoal(3, new SmartZombieAttackGoal(zombie, 1.0, false));
		SmartZombieMetrics.goalInstalled();
	}
}
