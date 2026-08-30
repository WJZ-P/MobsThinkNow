package com.wjz.mobsthinknow.paper.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PaperSquadCoordinatorTest {
	@Test
	void memberChainPreservesInsertionOrderAcrossRemovalAndReentry() {
		PaperSquadCoordinator.MemberChain chain = new PaperSquadCoordinator.MemberChain();
		PaperSquadCoordinator.MemberRecord first = member(1);
		PaperSquadCoordinator.MemberRecord moving = member(2);
		PaperSquadCoordinator.MemberRecord last = member(3);
		chain.add(first);
		chain.add(moving);
		chain.add(last);
		assertEquals(3, chain.size());
		assertSame(first, chain.first());

		chain.remove(first);
		assertSame(moving, chain.first());
		chain.remove(moving);
		chain.add(moving);
		assertSame(last, chain.first());
		assertEquals(2, chain.size());

		chain.clear();
		assertEquals(0, chain.size());
		assertNull(chain.first());
	}

	private static PaperSquadCoordinator.MemberRecord member(final int stableOrder) {
		return new PaperSquadCoordinator.MemberRecord(UUID.randomUUID(), null, stableOrder);
	}
}
