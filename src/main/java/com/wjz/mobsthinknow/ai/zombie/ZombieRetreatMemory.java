package com.wjz.mobsthinknow.ai.zombie;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.jspecify.annotations.Nullable;

/**
 * 把一次服务端伤害结算转换为撤退 Goal 可消费的短期事件。
 *
 * <p>Fabric 的 {@code AFTER_DAMAGE.damageTaken} 仍是护甲和附魔减伤前的数值，不能直接代表
 * 僵尸真正掉了多少血。因此在 {@code ALLOW_DAMAGE} 记录生命值，在 {@code AFTER_DAMAGE}
 * 用前后差得到最终实伤；盾牌、护甲、附魔、吸收生命和无敌帧都会自然反映在结果中。</p>
 *
 * <p>同一只僵尸在 GoalSelector 下次评估前可能连续挨打。这里同时保留“最近攻击者”和
 * “造成最大单次实伤的攻击者”：低血撤退远离最近威胁，重伤撤退则远离重击来源，且不会被
 * 随后的一次轻击覆盖。所有入口都只在服务器主线程调用，因此不需要锁。</p>
 */
public final class ZombieRetreatMemory {
	private static final Map<Zombie, Float> HEALTH_BEFORE_DAMAGE = new WeakHashMap<>();
	private static final Map<Zombie, AttackSnapshot> PENDING_ATTACKS = new WeakHashMap<>();

	private ZombieRetreatMemory() {
	}

	/** 在原版开始结算盾牌和减伤前记录生命值。 */
	public static void beginDamage(final Zombie zombie) {
		HEALTH_BEFORE_DAMAGE.put(zombie, zombie.getHealth());
	}

	/**
	 * 在非致命伤害结算结束后记录实际生命损失。环境伤害没有可远离的生物攻击者，因此不入队。
	 */
	public static void finishDamage(final Zombie zombie, final DamageSource source) {
		Float healthBefore = HEALTH_BEFORE_DAMAGE.remove(zombie);
		if (healthBefore == null
			|| !(source.getEntity() instanceof LivingEntity attacker)
			|| attacker == zombie) {
			return;
		}

		float healthDamage = actualHealthDamage(healthBefore, zombie.getHealth());
		PENDING_ATTACKS.compute(
			zombie,
			(ignored, pending) -> pending == null
				? new AttackSnapshot(attacker, attacker, healthDamage)
				: pending.withAttack(attacker, healthDamage)
		);
	}

	/** 每个新伤害批次只允许撤退 Goal 消费一次。 */
	static @Nullable AttackSnapshot consume(final Zombie zombie) {
		return PENDING_ATTACKS.remove(zombie);
	}

	/** 实体死亡或功能关闭时丢弃尚未消费的瞬时状态。 */
	public static void discard(final Zombie zombie) {
		HEALTH_BEFORE_DAMAGE.remove(zombie);
		PENDING_ATTACKS.remove(zombie);
	}

	/** 同一 JVM 切换存档时清空引用，避免旧世界状态进入新服务器。 */
	public static void clear() {
		HEALTH_BEFORE_DAMAGE.clear();
		PENDING_ATTACKS.clear();
	}

	static float actualHealthDamage(final float healthBefore, final float healthAfter) {
		return Math.max(0.0F, healthBefore - healthAfter);
	}

	record AttackSnapshot(
		LivingEntity latestAttacker,
		LivingEntity largestDamageAttacker,
		float largestHealthDamage
	) {
		private AttackSnapshot withAttack(final LivingEntity attacker, final float healthDamage) {
			if (healthDamage >= this.largestHealthDamage) {
				return new AttackSnapshot(attacker, attacker, healthDamage);
			}
			return new AttackSnapshot(attacker, this.largestDamageAttacker, this.largestHealthDamage);
		}
	}
}
