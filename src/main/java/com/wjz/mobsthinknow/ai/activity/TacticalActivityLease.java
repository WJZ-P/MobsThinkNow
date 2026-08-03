package com.wjz.mobsthinknow.ai.activity;

import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * 服务端主线程使用的轻量战术活动租约。
 *
 * <p>每个 Goal 持有一个稳定 {@link Handle}。{@code canUse} 只询问能否取得租约，真正的
 * {@code start} 才提交；{@code tick} 每拍续租，{@code stop} 精确释放自己的租约。高优先级活动
 * 可立即抢占，旧 Goal 会在下一次 {@code canContinueToUse} 发现所有权丢失后退出。弱键不会延长
 * 实体生命周期，也不会把临时状态写入存档。</p>
 */
public final class TacticalActivityLease {
	private static final int LEASE_TICKS = 3;
	private static final AtomicLong NEXT_OWNER_ID = new AtomicLong(1L);
	private static final WeakHashMap<Mob, Lease> ACTIVE = new WeakHashMap<>();

	private TacticalActivityLease() {
	}

	public static Handle handle(final TacticalActivity activity) {
		return new Handle(NEXT_OWNER_ID.getAndIncrement(), activity);
	}

	public static @Nullable Snapshot snapshot(final Mob mob, final long now) {
		synchronized (ACTIVE) {
			Lease current = liveLease(mob, now);
			return current == null ? null : new Snapshot(current.activity, current.expiresAt);
		}
	}

	public static int activeLeaseCount(final long now) {
		synchronized (ACTIVE) {
			ACTIVE.entrySet().removeIf(entry -> entry.getValue().expiresAt < now);
			return ACTIVE.size();
		}
	}

	private static Lease liveLease(final Mob mob, final long now) {
		Lease current = ACTIVE.get(mob);
		if (current != null && current.expiresAt < now) {
			ACTIVE.remove(mob);
			return null;
		}
		return current;
	}

	public static final class Handle {
		private final long ownerId;
		private final TacticalActivity activity;

		private Handle(final long ownerId, final TacticalActivity activity) {
			this.ownerId = ownerId;
			this.activity = activity;
		}

		/** 只做无副作用资格判断，避免未被 GoalSelector 选中的 Goal 提前占锁。 */
		public boolean canAcquire(final Mob mob, final long now) {
			synchronized (ACTIVE) {
				Lease current = liveLease(mob, now);
				return current == null
					|| current.ownerId == this.ownerId
					|| this.activity.priority() > current.activity.priority();
			}
		}

		/** 真正提交租约；同一拍被更高优先级活动抢占时返回 false。 */
		public boolean acquire(final Mob mob, final long now) {
			synchronized (ACTIVE) {
				Lease current = liveLease(mob, now);
				if (current != null
					&& current.ownerId != this.ownerId
					&& this.activity.priority() <= current.activity.priority()) {
					return false;
				}
				ACTIVE.put(mob, new Lease(this.ownerId, this.activity, now + LEASE_TICKS));
				return true;
			}
		}

		/**
		 * 续租同时验证所有权。被高优先级活动抢占后不会把旧租约“续回来”。
		 */
		public boolean renew(final Mob mob, final long now) {
			synchronized (ACTIVE) {
				Lease current = liveLease(mob, now);
				if (current == null || current.ownerId != this.ownerId) {
					return false;
				}
				ACTIVE.put(mob, new Lease(this.ownerId, this.activity, now + LEASE_TICKS));
				return true;
			}
		}

		public boolean owns(final Mob mob, final long now) {
			synchronized (ACTIVE) {
				Lease current = liveLease(mob, now);
				return current != null && current.ownerId == this.ownerId;
			}
		}

		public void release(final Mob mob) {
			synchronized (ACTIVE) {
				Lease current = ACTIVE.get(mob);
				if (current != null && current.ownerId == this.ownerId) {
					ACTIVE.remove(mob);
				}
			}
		}

		public TacticalActivity activity() {
			return this.activity;
		}
	}

	public record Snapshot(TacticalActivity activity, long expiresAt) {
	}

	private record Lease(long ownerId, TacticalActivity activity, long expiresAt) {
	}
}
