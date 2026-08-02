package com.wjz.mobsthinknow.ai.zombie.squad;

/** 协调器在重编职位时提取的一次性阵容快照，不持有 Minecraft 实体引用。 */
public record SquadComposition(
	int meleeMembers,
	int rangedMembers,
	int creepers,
	int spiders,
	int shieldFrontliners,
	int supportMembers
) {
	public SquadComposition {
		if (meleeMembers < 0
			|| rangedMembers < 0
			|| creepers < 0
			|| spiders < 0
			|| shieldFrontliners < 0
			|| supportMembers < 0) {
			throw new IllegalArgumentException("Squad composition counts must be non-negative.");
		}
	}

	public boolean hasFourCoreSpecies() {
		return this.meleeMembers > 0
			&& this.rangedMembers > 0
			&& this.creepers > 0
			&& this.spiders > 0;
	}
}
