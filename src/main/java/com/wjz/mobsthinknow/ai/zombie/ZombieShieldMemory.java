package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.jspecify.annotations.Nullable;

/**
 * 把一次针对举盾僵尸的攻击尝试传给其盾牌战术状态机。
 *
 * <p>信号在 {@code ALLOW_DAMAGE} 阶段记录，因此正面攻击即使随后被盾牌完全格挡、
 * 最终掉血为零，也仍然能触发一次合理的盾后反击。所有入口均位于服务端主线程，
 * 弱键只用于避免已经卸载的实体被瞬时信号表长期引用。</p>
 */
public final class ZombieShieldMemory {
	private static final Map<Zombie, AttackSignal> PENDING_ATTACKS = new WeakHashMap<>();

	private ZombieShieldMemory() {
	}

	public static void recordAttack(
		final Zombie zombie,
		final DamageSource source,
		final MobsThinkNowConfig config
	) {
		if (!config.enabled || !config.zombieAiEnabled || !config.armedSquads) {
			return;
		}
		if (!ZombieArmory.hasShield(zombie)
			|| !zombie.isUsingItem()
			|| zombie.getUsedItemHand() != InteractionHand.OFF_HAND) {
			return;
		}
		if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == zombie) {
			return;
		}

		PENDING_ATTACKS.put(zombie, new AttackSignal(attacker, zombie.level().getGameTime()));
	}

	static @Nullable AttackSignal consume(final Zombie zombie) {
		return PENDING_ATTACKS.remove(zombie);
	}

	public static void discard(final Zombie zombie) {
		PENDING_ATTACKS.remove(zombie);
	}

	public static void clear() {
		PENDING_ATTACKS.clear();
	}

	record AttackSignal(LivingEntity attacker, long gameTime) {
	}
}
