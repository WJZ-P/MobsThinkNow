package com.wjz.mobsthinknow.ai.skeleton;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class SkeletonBowIntervalsTest {
	@BeforeAll
	static void bootstrapRegistries() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void standardArchersKeepTwentyFortyCadence() {
		assertEquals(20, SkeletonBowIntervals.vanillaInterval(EntityType.SKELETON, Difficulty.HARD));
		assertEquals(40, SkeletonBowIntervals.vanillaInterval(EntityType.SKELETON, Difficulty.NORMAL));
		assertEquals(20, SkeletonBowIntervals.vanillaInterval(EntityType.STRAY, Difficulty.HARD));
		assertEquals(40, SkeletonBowIntervals.vanillaInterval(EntityType.STRAY, Difficulty.EASY));
	}

	@Test
	void statusArrowVariantsKeepTheirSlowerCadence() {
		assertEquals(50, SkeletonBowIntervals.vanillaInterval(EntityType.BOGGED, Difficulty.HARD));
		assertEquals(70, SkeletonBowIntervals.vanillaInterval(EntityType.BOGGED, Difficulty.NORMAL));
		assertEquals(50, SkeletonBowIntervals.vanillaInterval(EntityType.PARCHED, Difficulty.HARD));
		assertEquals(70, SkeletonBowIntervals.vanillaInterval(EntityType.PARCHED, Difficulty.EASY));
	}
}
