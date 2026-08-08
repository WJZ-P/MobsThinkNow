package com.wjz.mobsthinknow.paper.ai;

import com.wjz.mobsthinknow.shared.math.Vec3d;
import java.util.EnumSet;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Spider;

/** 只检查预测点附近固定五层，拒绝液体、火焰、仙人掌和无落脚面的跳扑落点。 */
public final class PaperLandingSafety {
	private static final Set<Material> HAZARDS = EnumSet.of(
		Material.LAVA,
		Material.FIRE,
		Material.SOUL_FIRE,
		Material.CAMPFIRE,
		Material.SOUL_CAMPFIRE,
		Material.CACTUS,
		Material.MAGMA_BLOCK,
		Material.POWDER_SNOW,
		Material.SWEET_BERRY_BUSH
	);

	private PaperLandingSafety() {
	}

	public static Location findSafeLanding(final Spider spider, final Vec3d predicted) {
		World world = spider.getWorld();
		int baseY = (int)Math.floor(predicted.y());
		for (int offset : new int[]{1, 0, -1, 2, -2}) {
			int feetY = baseY + offset;
			Location candidate = new Location(world, predicted.x(), feetY, predicted.z());
			if (!world.getWorldBorder().isInside(candidate)) {
				continue;
			}
			Block feet = world.getBlockAt(candidate);
			Block head = world.getBlockAt(feet.getX(), feetY + 1, feet.getZ());
			Block floor = world.getBlockAt(feet.getX(), feetY - 1, feet.getZ());
			if (isOpen(feet) && isOpen(head) && floor.getType().isSolid() && !HAZARDS.contains(floor.getType())) {
				return new Location(world, predicted.x(), feetY, predicted.z());
			}
		}
		return null;
	}

	private static boolean isOpen(final Block block) {
		return block.isPassable() && !block.isLiquid() && !HAZARDS.contains(block.getType());
	}
}
