package com.wjz.mobsthinknow.paper.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wjz.mobsthinknow.paper.command.PaperTestSpawner;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

final class PaperIntelligenceServiceTest {
	@Test
	void ownershipRequiresTheExactGeneratedComponentAndIntelligence() {
		Component generated = PaperIntelligenceService.syntheticName("entity.minecraft.zombie", 7);
		assertTrue(PaperIntelligenceService.matchesSyntheticName(generated, "entity.minecraft.zombie", 7));
		assertFalse(PaperIntelligenceService.matchesSyntheticName(generated, "entity.minecraft.zombie", 8));
		assertFalse(PaperIntelligenceService.matchesSyntheticName(generated, "entity.minecraft.husk", 7));
		assertFalse(PaperIntelligenceService.matchesSyntheticName(Component.text("Dinnerbone"), "entity.minecraft.zombie", 7));
	}

	@Test
	void generatedNameClampsOutOfRangeIntelligence() {
		assertTrue(PaperIntelligenceService.matchesSyntheticName(
			PaperIntelligenceService.syntheticName("entity.minecraft.zombie", 99),
			"entity.minecraft.zombie",
			10
		));
	}

	@Test
	void supportedTypesUseAnExplicitFamilyBoundary() {
		assertTrue(PaperIntelligenceService.isSupportedType(EntityType.ZOMBIE));
		assertTrue(PaperIntelligenceService.isSupportedType(EntityType.DROWNED));
		assertTrue(PaperIntelligenceService.isSupportedType(EntityType.PARCHED));
		assertTrue(PaperIntelligenceService.isSupportedType(EntityType.WITHER_SKELETON));
		assertTrue(PaperIntelligenceService.isSupportedType(EntityType.CREEPER));
		assertTrue(PaperIntelligenceService.isSupportedType(EntityType.SPIDER));
		assertFalse(PaperIntelligenceService.isSupportedType(EntityType.ZOMBIFIED_PIGLIN));
		assertFalse(PaperIntelligenceService.isSupportedType(EntityType.CAVE_SPIDER));
		assertFalse(PaperIntelligenceService.isSupportedType(EntityType.ZOMBIE_NAUTILUS));
		assertTrue(PaperTestSpawner.supportedTypes().contains(EntityType.PARCHED));
		assertTrue(PaperTestSpawner.supportedTypes().stream().allMatch(PaperIntelligenceService::isSupportedType));
	}
}
