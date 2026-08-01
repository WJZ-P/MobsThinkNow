package com.wjz.mobsthinknow.ai.utility;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class OverworldUndeadFamiliesTest {
	@BeforeAll
	static void bootstrapRegistries() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void acceptsEverySupportedZombieVariant() {
		assertTrue(OverworldUndeadFamilies.isZombieFamily(EntityType.ZOMBIE));
		assertTrue(OverworldUndeadFamilies.isZombieFamily(EntityType.HUSK));
		assertTrue(OverworldUndeadFamilies.isZombieFamily(EntityType.DROWNED));
		assertTrue(OverworldUndeadFamilies.isZombieFamily(EntityType.ZOMBIE_VILLAGER));
	}

	@Test
	void acceptsEverySupportedSkeletonVariant() {
		assertTrue(OverworldUndeadFamilies.isSkeletonFamily(EntityType.SKELETON));
		assertTrue(OverworldUndeadFamilies.isSkeletonFamily(EntityType.STRAY));
		assertTrue(OverworldUndeadFamilies.isSkeletonFamily(EntityType.BOGGED));
		assertTrue(OverworldUndeadFamilies.isSkeletonFamily(EntityType.PARCHED));
	}

	@Test
	void excludesNetherAndNonHumanoidUndead() {
		assertFalse(OverworldUndeadFamilies.isZombieFamily(EntityType.ZOMBIFIED_PIGLIN));
		assertFalse(OverworldUndeadFamilies.isZombieFamily(EntityType.CAMEL_HUSK));
		assertFalse(OverworldUndeadFamilies.isZombieFamily(EntityType.ZOMBIE_NAUTILUS));
		assertFalse(OverworldUndeadFamilies.isSkeletonFamily(EntityType.WITHER_SKELETON));
	}

	@Test
	void drownedKeepsItsDedicatedAmphibiousCombatStack() {
		assertTrue(OverworldUndeadFamilies.usesGroundZombieTactics(EntityType.ZOMBIE));
		assertTrue(OverworldUndeadFamilies.usesGroundZombieTactics(EntityType.HUSK));
		assertTrue(OverworldUndeadFamilies.usesGroundZombieTactics(EntityType.ZOMBIE_VILLAGER));
		assertFalse(OverworldUndeadFamilies.usesGroundZombieTactics(EntityType.DROWNED));
	}
}
