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
 * 把一次已经确认成功的盾牌格挡传给其盾牌战术状态机。
 *
 * <p>信号在 {@code AFTER_DAMAGE} 阶段记录，只有原版盾牌参与结算且最终伤害为零时才成立；
 * 背刺、破盾和部分穿透都不会误触发盾后反击。所有入口均位于服务端主线程，弱键只用于
 * 避免已经卸载的实体被瞬时信号表长期引用。</p>
 */
public final class ZombieShieldMemory {
	private static final Map<Zombie, BlockSignal> PENDING_BLOCKS = new WeakHashMap<>();
	private static final Map<Zombie, HurtAnimationSnapshot> PENDING_HURT_ANIMATIONS = new WeakHashMap<>();
	private static final Map<Zombie, DamageSource> PENDING_HURT_SOUND_SUPPRESSIONS = new WeakHashMap<>();
	private static final Map<Zombie, Long> LAST_SUPPRESSED_HURT_SOUND_AT = new WeakHashMap<>();

	private ZombieShieldMemory() {
	}

	/**
	 * 在原版开始结算伤害前保存已有的受击动画。
	 *
	 * <p>26.1.2 的 {@code LivingEntity.hurtServer} 即使把伤害完全格挡为零，也会先把
	 * {@code hurtTime/hurtDuration} 重置为十 tick。这里只给已经真正进入格挡状态的持盾僵尸留快照；
	 * 背刺、破盾以及绕过盾牌的伤害会在结算后自然丢弃快照。</p>
	 */
	public static void beginDamageAnimation(final Zombie zombie) {
		if (!ZombieArmory.hasShield(zombie) || zombie.getItemBlockingWith() == null) {
			PENDING_HURT_ANIMATIONS.remove(zombie);
			return;
		}

		PENDING_HURT_ANIMATIONS.put(
			zombie,
			new HurtAnimationSnapshot(zombie.hurtTime, zombie.hurtDuration)
		);
	}

	/**
	 * 只有盾牌确实参与格挡且结算伤害为零时才恢复旧计时。
	 *
	 * <p>恢复旧值而不是简单清零，可以避免一次新的格挡提前截断上一笔真实伤害尚未播放完的动画。
	 * 盾牌格挡声、盾反击退、物品耐久和仇恨信号都已经由原版伤害链完成，不受这里影响。</p>
	 */
	public static void finishDamageAnimation(
		final Zombie zombie,
		final float damage,
		final boolean blocked
	) {
		HurtAnimationSnapshot snapshot = PENDING_HURT_ANIMATIONS.remove(zombie);
		if (snapshot == null || !blocked || damage > 0.0F) {
			return;
		}

		zombie.hurtTime = snapshot.hurtTime();
		zombie.hurtDuration = snapshot.hurtDuration();
	}

	/**
	 * 记录 {@code LivingEntity.applyItemBlocking} 的真实结算结果，供同一伤害调用栈里的声音分支读取。
	 *
	 * <p>原版 26.1.2 在完全格挡后仍会无条件调用 {@code playHurtSound}。这里必须使用
	 * “被挡数值等于本次输入伤害”作为判据，而不是仅检查正在举盾；这样背刺、穿透和部分格挡
	 * 仍然保留真实受伤音效。每次格挡计算都会先覆盖旧信号，避免伤害冷却提前返回时留下脏状态。</p>
	 */
	public static void recordItemBlockingResolution(
		final Zombie zombie,
		final DamageSource source,
		final float incomingDamage,
		final float blockedDamage
	) {
		PENDING_HURT_SOUND_SUPPRESSIONS.remove(zombie);
		if (incomingDamage <= 0.0F || blockedDamage < incomingDamage) {
			return;
		}
		if (!ZombieArmory.hasShield(zombie) || zombie.getItemBlockingWith() == null) {
			return;
		}

		PENDING_HURT_SOUND_SUPPRESSIONS.put(zombie, source);
	}

	/** 仅供原版 hurtServer 的声音调用点查询；盾牌自己的格挡声不会经过这里。 */
	public static boolean shouldSuppressHurtSound(final Zombie zombie, final DamageSource source) {
		if (PENDING_HURT_SOUND_SUPPRESSIONS.get(zombie) != source) {
			return false;
		}
		LAST_SUPPRESSED_HURT_SOUND_AT.put(zombie, zombie.level().getGameTime());
		return true;
	}

	/** 每条伤害调用栈结束时清掉瞬时声音判据，包括中途被伤害冷却拦下的路径。 */
	public static void finishDamageSoundResolution(final Zombie zombie) {
		PENDING_HURT_SOUND_SUPPRESSIONS.remove(zombie);
	}

	static boolean wasHurtSoundSuppressedAt(final Zombie zombie, final long gameTime) {
		return LAST_SUPPRESSED_HURT_SOUND_AT.getOrDefault(zombie, Long.MIN_VALUE) == gameTime;
	}

	/** 在伤害后事件中把一次零实伤格挡登记为可消费的单次反击信号。 */
	public static void recordSuccessfulBlock(
		final Zombie zombie,
		final DamageSource source,
		final float damage,
		final boolean blocked,
		final MobsThinkNowConfig config
	) {
		if (!blocked || damage > 0.0F
			|| !config.enabled || !config.zombieAiEnabled || !config.armedSquads) {
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

		PENDING_BLOCKS.put(zombie, new BlockSignal(attacker, zombie.level().getGameTime()));
	}

	static @Nullable BlockSignal consume(final Zombie zombie) {
		return PENDING_BLOCKS.remove(zombie);
	}

	public static void discard(final Zombie zombie) {
		PENDING_BLOCKS.remove(zombie);
		PENDING_HURT_ANIMATIONS.remove(zombie);
		PENDING_HURT_SOUND_SUPPRESSIONS.remove(zombie);
		LAST_SUPPRESSED_HURT_SOUND_AT.remove(zombie);
	}

	public static void clear() {
		PENDING_BLOCKS.clear();
		PENDING_HURT_ANIMATIONS.clear();
		PENDING_HURT_SOUND_SUPPRESSIONS.clear();
		LAST_SUPPRESSED_HURT_SOUND_AT.clear();
	}

	record BlockSignal(LivingEntity attacker, long gameTime) {
	}

	private record HurtAnimationSnapshot(int hurtTime, int hurtDuration) {
	}
}
