package com.wjz.mobsthinknow.ai.zombie;

import net.minecraft.world.entity.monster.zombie.Zombie;

/** 服务端动作仲裁与客户端采样快照的共同入口。 */
public final class ZombieBodyLanguage {
	private ZombieBodyLanguage() {
	}

	/**
	 * 播放会自行结束的动作；低优先级动作不会打断正在逃生或怒吼的僵尸。
	 *
	 * @return 动作是否真正发布；表现层据此只统计玩家实际能看到的动作
	 */
	public static boolean play(final Zombie zombie, final ZombieBodyAction action) {
		if (!action.isTransient()) {
			throw new IllegalArgumentException("Expected a transient zombie body action: " + action);
		}
		Snapshot current = snapshot(zombie, 0.0F);
		if (current.action().priority() > action.priority()) {
			return false;
		}
		set(zombie, action);
		return true;
	}

	/** 启动由 Goal 生命周期控制的持续动作，例如撤退冲刺。 */
	public static void startPersistent(final Zombie zombie, final ZombieBodyAction action) {
		if (action == ZombieBodyAction.NONE || action.isTransient()) {
			throw new IllegalArgumentException("Expected a persistent zombie body action: " + action);
		}
		Snapshot current = snapshot(zombie, 0.0F);
		if (current.action() != action && current.action().priority() > action.priority()) {
			return;
		}
		ZombieBodyActionAccess access = access(zombie);
		if (access.mobsthinknow$getBodyAction() != action) {
			set(zombie, action);
		}
	}

	/** 只结束调用方自己拥有的持续动作，避免误删同时发生的更高优先级表现。 */
	public static void stopPersistent(final Zombie zombie, final ZombieBodyAction expected) {
		stop(zombie, expected);
	}

	/**
	 * 只清理由调用方启动的指定动作。瞬时前摇也需要在目标丢失、受击或动作条件失效时提前收回，
	 * 因此这里不限制 {@code expected} 必须是持续动作。
	 */
	public static void stop(final Zombie zombie, final ZombieBodyAction expected) {
		ZombieBodyActionAccess access = access(zombie);
		if (access.mobsthinknow$getBodyAction() == expected) {
			access.mobsthinknow$setBodyAction(ZombieBodyAction.NONE, zombie.level().getGameTime());
		}
	}

	/** 根据同步开始 tick 计算客户端可直接消费的动作时间；过期动作在本地自然回落为 NONE。 */
	public static Snapshot snapshot(final Zombie zombie, final float partialTick) {
		ZombieBodyActionAccess access = access(zombie);
		ZombieBodyAction action = access.mobsthinknow$getBodyAction();
		float elapsedTicks = Math.max(
			0.0F,
			zombie.level().getGameTime() + partialTick - access.mobsthinknow$getBodyActionStartedAt()
		);
		return action.isActiveAt(elapsedTicks)
			? new Snapshot(action, elapsedTicks)
			: Snapshot.NONE;
	}

	private static void set(final Zombie zombie, final ZombieBodyAction action) {
		access(zombie).mobsthinknow$setBodyAction(action, zombie.level().getGameTime());
	}

	private static ZombieBodyActionAccess access(final Zombie zombie) {
		return (ZombieBodyActionAccess)zombie;
	}

	public record Snapshot(ZombieBodyAction action, float elapsedTicks) {
		public static final Snapshot NONE = new Snapshot(ZombieBodyAction.NONE, 0.0F);
	}
}
