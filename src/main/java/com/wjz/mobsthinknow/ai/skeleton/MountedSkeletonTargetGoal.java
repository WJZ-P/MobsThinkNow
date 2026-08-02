package com.wjz.mobsthinknow.ai.skeleton;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.jspecify.annotations.Nullable;

/**
 * 以 TARGET 最高优先级把受管理坐骑的当前目标镜像给骷髅射手。
 *
 * <p>该 Goal 每 tick 都先于弓/弩 Goal 运行。下马后只清理它最后写入的共享目标，随后把索敌权
 * 交还给骷髅自己的原版目标选择器。</p>
 */
public final class MountedSkeletonTargetGoal extends Goal {
	private final AbstractSkeleton skeleton;
	private @Nullable LivingEntity sharedTarget;

	public MountedSkeletonTargetGoal(final AbstractSkeleton skeleton) {
		this.skeleton = skeleton;
		this.setFlags(EnumSet.of(Flag.TARGET));
	}

	@Override
	public boolean canUse() {
		this.sharedTarget = MountedSkeletonCombat.sharedTarget(this.skeleton);
		return this.sharedTarget != null;
	}

	@Override
	public boolean canContinueToUse() {
		return MountedSkeletonCombat.sharedTarget(this.skeleton) != null;
	}

	@Override
	public void start() {
		this.synchronizeTarget();
	}

	@Override
	public void tick() {
		this.synchronizeTarget();
	}

	@Override
	public void stop() {
		if (this.skeleton.getTarget() == this.sharedTarget) {
			this.skeleton.setTarget(null);
		}
		this.sharedTarget = null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	private void synchronizeTarget() {
		LivingEntity current = MountedSkeletonCombat.sharedTarget(this.skeleton);
		if (current == null) {
			return;
		}
		this.sharedTarget = current;
		if (this.skeleton.getTarget() != current) {
			this.skeleton.setTarget(current);
		}
	}
}
