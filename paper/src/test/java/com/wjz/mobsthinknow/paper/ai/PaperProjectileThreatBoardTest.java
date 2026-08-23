package com.wjz.mobsthinknow.paper.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class PaperProjectileThreatBoardTest {
	@Test
	void packedCellsKeepSignedAxesDistinctAcrossThePlayableWorld() {
		int[] horizontal = {-2_500_001, -1, 0, 1, 2_500_001};
		int[] vertical = {-171, -1, 0, 1, 171};
		Set<Long> packed = new HashSet<>();
		for (int x : horizontal) {
			for (int y : vertical) {
				for (int z : horizontal) {
					packed.add(PaperProjectileThreatBoard.packedCell(x, y, z));
				}
			}
		}

		assertEquals(horizontal.length * vertical.length * horizontal.length, packed.size());
		assertNotEquals(
			PaperProjectileThreatBoard.packedCell(-1, 0, 0),
			PaperProjectileThreatBoard.packedCell(0, 0, 0)
		);
	}
}
