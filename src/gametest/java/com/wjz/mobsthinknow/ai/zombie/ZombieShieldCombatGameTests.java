package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ZombieShieldCombatGameTests implements CustomTestMethodInvoker {
	@GameTest(maxTicks = 40)
	public void fullyBlockedHitDoesNotRestartHurtAnimation(final GameTestHelper helper) {
		ShieldFixture fixture = this.createFixture(helper);
		// 目标位于僵尸东侧；-90 度让头部正对攻击来源，满足原版盾牌的正面格挡夹角。
		fixture.zombie().setYRot(-90.0F);
		fixture.zombie().setYHeadRot(-90.0F);
		AtomicBoolean resolved = new AtomicBoolean();

		helper.onEachTick(() -> {
			fixture.zombie().clearFire();
			fixture.combat().tick(fixture.target(), fixture.config(), true);
			if (resolved.get() || !fixture.zombie().isBlocking()) {
				return;
			}
			resolved.set(true);

			float healthBefore = fixture.zombie().getHealth();
			fixture.zombie().hurtDuration = 0;
			fixture.zombie().hurtTime = 0;
			boolean hurt = fixture.zombie().hurtServer(
				helper.getLevel(),
				fixture.zombie().damageSources().mobAttack(fixture.target()),
				4.0F
			);

			helper.assertTrue(!hurt, "A frontal shield hit was not fully blocked.");
			helper.assertTrue(
				fixture.zombie().getHealth() == healthBefore,
				"A fully blocked shield hit still removed health."
			);
			helper.assertTrue(
				fixture.zombie().hurtTime == 0 && fixture.zombie().hurtDuration == 0,
				"A fully blocked hit started the zombie hurt animation."
			);
			helper.assertTrue(
				ZombieShieldMemory.wasHurtSoundSuppressedAt(fixture.zombie(), helper.getLevel().getGameTime()),
				"A fully blocked hit still reached the zombie hurt-sound call."
			);
			helper.assertTrue(
				ZombieShieldDesign.hasZombieHead(fixture.zombie().getOffhandItem()),
				"The shield combatant did not receive the zombie-head shield pattern."
			);

			// 若上一笔真实伤害尚有动画，则新的格挡只是不重播，而不是粗暴截断旧动画。
			fixture.zombie().invulnerableTime = 0;
			fixture.zombie().hurtDuration = 7;
			fixture.zombie().hurtTime = 3;
			boolean secondHurt = fixture.zombie().hurtServer(
				helper.getLevel(),
				fixture.zombie().damageSources().mobAttack(fixture.target()),
				4.0F
			);
			helper.assertTrue(!secondHurt, "The repeated frontal shield hit was not fully blocked.");
			helper.assertTrue(
				fixture.zombie().hurtTime == 3 && fixture.zombie().hurtDuration == 7,
				"A repeated shield block restarted or truncated an existing hurt animation."
			);
			helper.assertTrue(fixture.zombie().isUsingItem(), "The successful block unexpectedly lowered the shield.");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 40)
	public void unblockedHitKeepsVanillaHurtAnimation(final GameTestHelper helper) {
		ShieldFixture fixture = this.createFixture(helper);
		// 90 度让僵尸背对东侧目标：虽然仍在举盾，但这次攻击没有被盾牌挡住。
		fixture.zombie().setYRot(90.0F);
		fixture.zombie().setYHeadRot(90.0F);
		AtomicBoolean resolved = new AtomicBoolean();

		helper.onEachTick(() -> {
			fixture.zombie().clearFire();
			fixture.combat().tick(fixture.target(), fixture.config(), true);
			if (resolved.get() || !fixture.zombie().isBlocking()) {
				return;
			}
			resolved.set(true);

			float healthBefore = fixture.zombie().getHealth();
			fixture.zombie().hurtDuration = 0;
			fixture.zombie().hurtTime = 0;
			boolean hurt = fixture.zombie().hurtServer(
				helper.getLevel(),
				fixture.zombie().damageSources().mobAttack(fixture.target()),
				4.0F
			);

			helper.assertTrue(hurt, "A rear attack was incorrectly treated as a shield block.");
			helper.assertTrue(
				fixture.zombie().getHealth() < healthBefore,
				"An unblocked attack did not remove health."
			);
			helper.assertTrue(
				fixture.zombie().hurtTime > 0 && fixture.zombie().hurtDuration > 0,
				"The shield animation filter swallowed a real hurt animation."
			);
			helper.assertTrue(
				!ZombieShieldMemory.wasHurtSoundSuppressedAt(fixture.zombie(), helper.getLevel().getGameTime()),
				"A real rear hit incorrectly suppressed the zombie hurt sound."
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 70)
	public void passiveTargetEventuallyProvokesOneStrikeThenShieldReturns(final GameTestHelper helper) {
		ShieldFixture fixture = this.createFixture(helper);
		AtomicBoolean sawGuard = new AtomicBoolean();
		AtomicBoolean sawShieldReturn = new AtomicBoolean();
		long[] firstStrikeAt = {Long.MIN_VALUE};

		helper.onEachTick(() -> {
			fixture.zombie().clearFire();
			fixture.combat().tick(fixture.target(), fixture.config(), true);
			if (!sawGuard.get()) {
				helper.assertTrue(fixture.combat().holdsPosition(), "The shield zombie did not stop to guard at melee range.");
				helper.assertTrue(fixture.zombie().isUsingItem(), "The shield was not raised before waiting.");
				sawGuard.set(true);
			}
			if (firstStrikeAt[0] != Long.MIN_VALUE
				&& !sawShieldReturn.get()
				&& !fixture.combat().isStrikeWindow()
				&& fixture.zombie().isUsingItem()) {
				sawShieldReturn.set(true);
			}

			if (fixture.combat().isStrikeWindow()) {
				helper.assertTrue(
					!fixture.zombie().isUsingItem(),
					"The shield stayed raised during the proactive strike window."
				);
				long now = helper.getLevel().getGameTime();
				if (firstStrikeAt[0] != Long.MIN_VALUE) {
					helper.assertTrue(
						sawShieldReturn.get(),
						"The shield zombie never returned to defense after its first strike."
					);
					helper.assertTrue(
						now - firstStrikeAt[0] >= 13L,
						"The shield zombie opened a second iron-sword strike before its weapon cooldown."
					);
					helper.succeed();
					return;
				}

				firstStrikeAt[0] = now;
				fixture.combat().onAttackPerformed(fixture.target());
				helper.assertTrue(!fixture.zombie().isUsingItem(), "The shield was raised during post-strike recovery.");
				helper.assertTrue(
					!fixture.combat().isStrikeWindow() && fixture.combat().blocksAttack(),
					"The zombie did not enter its unshielded post-strike recovery gap."
				);
			}
		});
	}

	@GameTest(maxTicks = 60)
	public void successfulBlockWaitsBeforeUnshieldedCounterattack(final GameTestHelper helper) {
		ShieldFixture fixture = this.createFixture(helper);
		fixture.zombie().setYRot(-90.0F);
		fixture.zombie().setYHeadRot(-90.0F);
		AtomicBoolean blockApplied = new AtomicBoolean();
		AtomicBoolean counterPerformed = new AtomicBoolean();
		long[] blockAt = {Long.MIN_VALUE};
		long[] attackAt = {Long.MIN_VALUE};

		helper.onEachTick(() -> {
			fixture.zombie().clearFire();
			fixture.combat().tick(fixture.target(), fixture.config(), true);
			long now = helper.getLevel().getGameTime();

			if (!blockApplied.get()) {
				if (!fixture.zombie().isBlocking()) {
					return;
				}

				// 短暂打开全局开关，走真实 AFTER_DAMAGE 成功格挡入口；同一调用栈内立即恢复。
				boolean armedSquadsBefore = ConfigManager.get().armedSquads;
				helper.assertTrue(
					ConfigManager.update(config -> config.armedSquads = true),
					"The test could not enable shield-combat block signals."
				);
				try {
					boolean hurt = fixture.zombie().hurtServer(
						helper.getLevel(),
						fixture.zombie().damageSources().mobAttack(fixture.target()),
						4.0F
					);
					helper.assertTrue(!hurt, "The frontal attack was not fully blocked by the shield.");
				} finally {
					helper.assertTrue(
						ConfigManager.update(config -> config.armedSquads = armedSquadsBefore),
						"The test could not restore the armed-squad configuration."
					);
				}

				blockAt[0] = now;
				blockApplied.set(true);
				// 同 tick 消费成功格挡信号；状态机应只排期，绝不能立刻放盾出手。
				fixture.combat().tick(fixture.target(), fixture.config(), true);
				helper.assertTrue(!fixture.combat().isStrikeWindow(), "The shield zombie countered in the block tick.");
				helper.assertTrue(fixture.zombie().isUsingItem(), "The shield dropped before the counter delay elapsed.");
				return;
			}

			if (!counterPerformed.get()) {
				if (!fixture.combat().isStrikeWindow()) {
					helper.assertTrue(fixture.zombie().isUsingItem(), "The shield dropped during the 2-4 tick counter delay.");
					return;
				}

				long delay = now - blockAt[0];
				helper.assertTrue(delay >= 2L && delay <= 4L, "The shield counter delay was outside 2-4 ticks.");
				helper.assertTrue(!fixture.zombie().isUsingItem(), "The shield stayed raised in the attack window.");
				attackAt[0] = now;
				fixture.combat().onAttackPerformed(fixture.target());
				counterPerformed.set(true);
				helper.assertTrue(!fixture.zombie().isUsingItem(), "The shield was re-raised in the attack tick.");
				helper.assertTrue(fixture.combat().blocksAttack(), "Recovery allowed a second attack before re-guarding.");
				return;
			}

			if (!fixture.zombie().isUsingItem()) {
				helper.assertTrue(
					now - attackAt[0] <= 4L,
					"The zombie stayed unshielded beyond the post-attack recovery range."
				);
				return;
			}

			long recovery = now - attackAt[0];
			helper.assertTrue(recovery >= 2L && recovery <= 4L, "The post-attack shield gap was outside 2-4 ticks.");
			helper.assertTrue(fixture.combat().holdsPosition(), "The zombie did not return to its guard phase.");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void smartGuardConvertsOneSuccessfulBlockIntoOneTimedShieldBash(final GameTestHelper helper) {
		ShieldFixture fixture = this.createFixture(helper);
		fixture.config().shieldBashes = true;
		fixture.config().shieldBashMinimumIntelligence = 7;
		fixture.config().shieldBashChance = 1.0;
		fixture.config().shieldBashDamage = 2.0;
		fixture.config().shieldBashKnockback = 1.25;
		ZombieIntelligence.set(fixture.zombie(), 10);
		fixture.target().setInvulnerable(false);
		fixture.target().getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);
		fixture.target().getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.0);
		fixture.target().setHealth(40.0F);
		fixture.zombie().setYRot(-90.0F);
		fixture.zombie().setYHeadRot(-90.0F);

		boolean[] blockApplied = {false};
		boolean[] sawBash = {false};
		long[] bashStartedAt = {Long.MIN_VALUE};
		int[] damageFrames = {0};
		float healthBefore = fixture.target().getHealth();
		long bashesBefore = SmartZombieMetrics.snapshot().shieldBashes();
		long bashHitsBefore = SmartZombieMetrics.snapshot().shieldBashHits();
		double awayX = fixture.target().getX() - fixture.zombie().getX();
		double awayZ = fixture.target().getZ() - fixture.zombie().getZ();
		boolean[] sawAwayImpulse = {false};

		helper.onEachTick(() -> {
			fixture.zombie().clearFire();
			fixture.combat().tick(fixture.target(), fixture.config(), true);
			long now = helper.getLevel().getGameTime();

			if (!blockApplied[0]) {
				if (!fixture.zombie().isBlocking()) {
					return;
				}
				boolean armedSquadsBefore = ConfigManager.get().armedSquads;
				helper.assertTrue(
					ConfigManager.update(config -> config.armedSquads = true),
					"The bash test could not enable real shield-block signals."
				);
				try {
					boolean hurt = fixture.zombie().hurtServer(
						helper.getLevel(),
						fixture.zombie().damageSources().mobAttack(fixture.target()),
						4.0F
					);
					helper.assertTrue(!hurt, "The setup strike was not blocked by the guard shield.");
				} finally {
					helper.assertTrue(
						ConfigManager.update(config -> config.armedSquads = armedSquadsBefore),
						"The bash test could not restore armed-squad configuration."
					);
				}
				blockApplied[0] = true;
				fixture.combat().tick(fixture.target(), fixture.config(), true);
				return;
			}

			ZombieBodyActionAccess action = (ZombieBodyActionAccess)fixture.zombie();
			if (action.mobsthinknow$getBodyAction() == ZombieBodyAction.SHIELD_BASH) {
				sawBash[0] = true;
				if (bashStartedAt[0] == Long.MIN_VALUE) {
					bashStartedAt[0] = action.mobsthinknow$getBodyActionStartedAt();
					helper.assertTrue(!fixture.zombie().isUsingItem(), "The shield stayed raised during bash windup.");
					helper.assertTrue(
						SmartZombieMetrics.snapshot().shieldBashes() > bashesBefore,
						"The started shield bash was absent from /mtn status diagnostics."
					);
				}
			}

			if (fixture.target().getHealth() < healthBefore && damageFrames[0] == 0) {
				damageFrames[0]++;
				helper.assertTrue(sawBash[0], "The target took counter damage before a shield-bash action began.");
				helper.assertTrue(
					now - bashStartedAt[0] == 5L,
					"Shield-bash damage did not land on its dedicated fifth-tick impact frame."
				);
				helper.assertTrue(
					Math.abs((healthBefore - fixture.target().getHealth()) - 2.0F) < 0.01F,
					"The configured shield-bash damage was not applied exactly once."
				);
				double awayImpulse = fixture.target().getDeltaMovement().x * awayX
					+ fixture.target().getDeltaMovement().z * awayZ;
				helper.assertTrue(
					awayImpulse > 0.01,
					"Shield-bash impact did not write an impulse away from the zombie: "
						+ fixture.target().getDeltaMovement()
				);
				sawAwayImpulse[0] = true;
				helper.assertTrue(
					SmartZombieMetrics.snapshot().shieldBashHits() > bashHitsBefore,
					"The shield-bash impact was absent from /mtn status diagnostics."
				);
			}

			if (damageFrames[0] == 1
				&& action.mobsthinknow$getBodyAction() == ZombieBodyAction.NONE
				&& fixture.zombie().isUsingItem()) {
				helper.assertTrue(
					fixture.target().getHealth() == healthBefore - 2.0F,
					"The single bash state applied damage more than once."
				);
				helper.assertTrue(
					sawAwayImpulse[0],
					"The shield-bash hit never produced a verified away-facing velocity impulse."
				);
				helper.succeed();
			}
		});
	}

	private ShieldFixture createFixture(final GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 0, 2);
		Villager target = helper.spawn(EntityType.VILLAGER, 3, 0, 2);
		zombie.setNoAi(true);
		target.setNoAi(true);
		target.setInvulnerable(true);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		zombie.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));

		MobsThinkNowConfig config = new MobsThinkNowConfig();
		config.armedSquads = true;
		// 既有测试只验证延迟武器反击；盾击分支由独立测试以 100% 概率覆盖。
		config.shieldBashes = false;
		return new ShieldFixture(zombie, target, new ZombieShieldCombat(zombie), config);
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}

	private record ShieldFixture(
		Zombie zombie,
		Villager target,
		ZombieShieldCombat combat,
		MobsThinkNowConfig config
	) {
	}
}
