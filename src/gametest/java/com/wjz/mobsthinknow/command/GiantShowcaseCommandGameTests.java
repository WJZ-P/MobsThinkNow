package com.wjz.mobsthinknow.command;

import com.wjz.mobsthinknow.ai.giant.GiantIntelligence;
import com.wjz.mobsthinknow.ai.giant.GiantPassengerLayout;
import java.lang.reflect.Method;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** 从真实 Brigadier 入口验证巨人预设、批量参数与两套复数快捷入口。 */
public final class GiantShowcaseCommandGameTests implements CustomTestMethodInvoker {
	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void giantSiegeLiteralSupportsBatchCountAndAllThreeAttachments(final GameTestHelper helper) {
		BlockPos sourceBlock = helper.absolutePos(new BlockPos(32, 1, 8));
		helper.getLevel().getServer().getCommands().performPrefixedCommand(
			source(helper, sourceBlock),
			"mtn spawn giant_siege 2"
		);

		List<Giant> giants = giantsNear(helper, sourceBlock);
		helper.assertTrue(giants.size() == 2, "Giant siege batch did not create exactly two roots.");
		for (Giant giant : giants) {
			helper.assertTrue(GiantIntelligence.get(giant) == 10, "A showcase Giant lost its IQ-10 profile.");
			helper.assertTrue(GiantPassengerLayout.headRider(giant) instanceof Skeleton, "Giant lost its head rider.");
			helper.assertTrue(GiantPassengerLayout.payloads(giant).size() == 2, "Giant lost one of its two hand payloads.");
			helper.assertTrue(
				GiantPassengerLayout.payloads(giant).stream().anyMatch(payload -> payload.entity() instanceof Creeper)
					&& GiantPassengerLayout.payloads(giant).stream().anyMatch(payload -> payload.entity() instanceof Zombie),
				"Giant hands did not contain one creeper and one zombie."
			);
		}
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void spawnGiantsShortcutCreatesCompletePlatform(final GameTestHelper helper) {
		assertShortcut(helper, "mtn spawngiants");
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void nestedSpawnAllGiantsCreatesCompletePlatform(final GameTestHelper helper) {
		assertShortcut(helper, "mtn spawnall giants");
	}

	private static void assertShortcut(final GameTestHelper helper, final String command) {
		BlockPos sourceBlock = helper.absolutePos(new BlockPos(32, 1, 8));
		helper.getLevel().getServer().getCommands().performPrefixedCommand(source(helper, sourceBlock), command);
		List<Giant> giants = giantsNear(helper, sourceBlock);
		helper.assertTrue(giants.size() == 1, command + " did not create exactly one Giant root.");
		helper.assertTrue(giants.getFirst().getPassengers().size() == 3, command + " lost a rider or hand payload.");
		helper.succeed();
	}

	private static List<Giant> giantsNear(final GameTestHelper helper, final BlockPos sourceBlock) {
		return helper.getLevel().getEntitiesOfClass(
			Giant.class,
			new AABB(sourceBlock).inflate(16.0, 16.0, 16.0),
			giant -> giant.getType() == EntityType.GIANT && giant.isAlive()
		);
	}

	private static CommandSourceStack source(final GameTestHelper helper, final BlockPos sourceBlock) {
		return helper.getLevel()
			.getServer()
			.createCommandSourceStack()
			.withLevel(helper.getLevel())
			.withPosition(Vec3.atBottomCenterOf(sourceBlock))
			.withRotation(Vec2.ZERO)
			.withSuppressedOutput();
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}
