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
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ZombieShieldCombatGameTests implements CustomTestMethodInvoker {
	@GameTest(maxTicks = 70)
	public void passiveTargetEventuallyProvokesOneStrikeThenShieldReturns(final GameTestHelper helper) {
		ShieldFixture fixture = this.createFixture(helper);
		AtomicBoolean sawGuard = new AtomicBoolean();
		long[] firstStrikeAt = {Long.MIN_VALUE};

		helper.onEachTick(() -> {
			fixture.zombie().clearFire();
			fixture.combat().tick(fixture.target(), fixture.config(), true);
			if (!sawGuard.get()) {
				helper.assertTrue(fixture.combat().holdsPosition(), "The shield zombie did not stop to guard at melee range.");
				helper.assertTrue(fixture.zombie().isUsingItem(), "The shield was not raised before waiting.");
				sawGuard.set(true);
			}

			if (fixture.combat().isStrikeWindow()) {
				helper.assertTrue(
					!fixture.zombie().isUsingItem(),
					"The shield stayed raised during the proactive strike window."
				);
				long now = helper.getLevel().getGameTime();
				if (firstStrikeAt[0] != Long.MIN_VALUE) {
					helper.assertTrue(
						now - firstStrikeAt[0] >= 13L,
						"The shield zombie opened a second iron-sword strike before its weapon cooldown."
					);
					helper.succeed();
					return;
				}

				firstStrikeAt[0] = now;
				fixture.combat().onAttackPerformed(fixture.target());
				helper.assertTrue(fixture.zombie().isUsingItem(), "The shield was not restored after the single strike.");
				helper.assertTrue(fixture.combat().holdsPosition(), "The zombie did not return to its guard phase.");
			}
		});
	}

	@GameTest
	public void incomingAttackOpensImmediateCounterWindow(final GameTestHelper helper) {
		ShieldFixture fixture = this.createFixture(helper);
		fixture.combat().tick(fixture.target(), fixture.config(), true);
		helper.assertTrue(fixture.combat().holdsPosition(), "The shield zombie did not enter its guard phase.");

		// 短暂打开全局开关，走真实 ALLOW_DAMAGE 注册入口；finally 在同一服务端调用栈内恢复，
		// 不让并行运行的其他 GameTest 继承这项设置。
		boolean armedSquadsBefore = ConfigManager.get().armedSquads;
		helper.assertTrue(
			ConfigManager.update(config -> config.armedSquads = true),
			"The test could not enable shield-combat damage signals."
		);
		try {
			boolean hurt = fixture.zombie().hurtServer(
				helper.getLevel(),
				fixture.zombie().damageSources().mobAttack(fixture.target()),
				1.0F
			);
			helper.assertTrue(hurt, "The incoming attack did not enter the server damage pipeline.");
		} finally {
			helper.assertTrue(
				ConfigManager.update(config -> config.armedSquads = armedSquadsBefore),
				"The test could not restore the armed-squad configuration."
			);
		}
		fixture.combat().tick(fixture.target(), fixture.config(), true);
		helper.assertTrue(fixture.combat().isStrikeWindow(), "The incoming attack did not trigger a counter window.");
		helper.assertTrue(!fixture.zombie().isUsingItem(), "The zombie did not lower its shield to counterattack.");

		fixture.combat().onAttackPerformed(fixture.target());
		helper.assertTrue(fixture.zombie().isUsingItem(), "The zombie did not re-raise its shield after countering.");
		helper.succeed();
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
