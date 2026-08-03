package com.wjz.mobsthinknow.ai.spider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** 蜘蛛载具的有界路径净空、宽度与危险落差检查。 */
public final class SpiderTransportRouteEvaluator {
	private static final int MAXIMUM_SAMPLED_NODES = 16;
	private static final int DELIVERY_ENVELOPE_TOLERANCE = 2;
	private static final double WIDTH_MARGIN = 0.24;
	private static final double HEIGHT_MARGIN = 0.20;
	private static final double DANGEROUS_DROP = 2.5;
	private static final Vec3[] DISMOUNT_OFFSETS = {
		new Vec3(1.5, 0.0, 0.0), new Vec3(-1.5, 0.0, 0.0),
		new Vec3(0.0, 0.0, 1.5), new Vec3(0.0, 0.0, -1.5),
		new Vec3(1.5, 0.0, 1.5), new Vec3(-1.5, 0.0, 1.5),
		new Vec3(1.5, 0.0, -1.5), new Vec3(-1.5, 0.0, -1.5)
	};

	private SpiderTransportRouteEvaluator() {
	}

	public static Assessment assess(
		final Spider spider,
		final Vec3 destination,
		final double passengerHeight
	) {
		// 投送目的地通常就是目标当前脚下；该方块被目标占用是合法的战斗终点。
		// 允许原版寻路在两格投送包络内完成，而不是要求坐骑站进目标碰撞箱中心。
		Path path = spider.getNavigation().createPath(
			BlockPos.containing(destination),
			DELIVERY_ENVELOPE_TOLERANCE
		);
		if (path == null) {
			// WallClimberNavigation 允许“无 Path、记住位置后直接爬行”的降级，因此 null 并不等于
			// 平地不可达。用不安装到实体上的地面导航器生成只读探测 Path；实际移动仍由蜘蛛自己的
			// WallClimberNavigation 执行，不改变其攀墙能力或运行状态。
			GroundPathNavigation probe = new GroundPathNavigation(spider, spider.level());
			path = probe.createPath(BlockPos.containing(destination), DELIVERY_ENVELOPE_TOLERANCE);
		}
		if (path == null || !path.canReach()) {
			// 刚生成、尚未落地或测试夹具设为 NoAI 时，PathNavigation 会暂时拒绝建路。
			// 此时以常数上限的直线几何探测区分“导航未就绪”和“路线确有危险”。
			return assessDirectFallback(spider, destination, passengerHeight);
		}
		return assessPath(spider, path, passengerHeight);
	}

	private static Assessment assessDirectFallback(
		final Spider spider,
		final Vec3 destination,
		final double passengerHeight
	) {
		Vec3 start = spider.position();
		double horizontalDistance = new Vec3(destination.x - start.x, 0.0, destination.z - start.z).length();
		int segments = Math.max(1, Math.min(MAXIMUM_SAMPLED_NODES - 1, (int)Math.ceil(horizontalDistance)));
		double carrierHeight = combinedHeight(spider, passengerHeight);
		double carrierWidth = combinedWidth(spider);
		double previousSurfaceY = Double.NaN;
		for (int index = 0; index <= segments; index++) {
			double progress = index / (double)segments;
			Vec3 expected = new Vec3(
				lerp(progress, start.x, destination.x),
				lerp(progress, start.y, destination.y),
				lerp(progress, start.z, destination.z)
			);
			double surfaceY = findSupportSurfaceY(spider, expected);
			if (!Double.isFinite(surfaceY)
				|| (Double.isFinite(previousSurfaceY) && previousSurfaceY - surfaceY > DANGEROUS_DROP)) {
				return new Assessment(Status.DANGEROUS_DROP, null, index + 1);
			}
			previousSurfaceY = surfaceY;
			Vec3 feet = new Vec3(expected.x, surfaceY, expected.z);
			AABB carrierBox = AABB.ofSize(
				feet.add(0.0, carrierHeight * 0.5, 0.0),
				carrierWidth,
				carrierHeight,
				carrierWidth
			);
			if (spider.level().noBlockCollision(spider, carrierBox)) {
				continue;
			}
			AABB spiderOnlyBox = AABB.ofSize(
				feet.add(0.0, (spider.getBbHeight() + HEIGHT_MARGIN) * 0.5, 0.0),
				carrierWidth,
				spider.getBbHeight() + HEIGHT_MARGIN,
				carrierWidth
			);
			return new Assessment(
				classifyClearance(false, spider.level().noBlockCollision(spider, spiderOnlyBox)),
				null,
				index + 1
			);
		}
		return new Assessment(Status.CLEAR, null, segments + 1);
	}

	static Assessment assessPath(
		final Spider spider,
		final Path path,
		final double passengerHeight
	) {
		int nodeCount = path.getNodeCount();
		if (nodeCount <= 0) {
			return new Assessment(Status.UNREACHABLE, path, 0);
		}
		int step = Math.max(1, (int)Math.ceil(nodeCount / (double)MAXIMUM_SAMPLED_NODES));
		double carrierHeight = combinedHeight(spider, passengerHeight);
		double carrierWidth = combinedWidth(spider);
		int checks = 0;
		Vec3 previous = null;
		for (int index = path.getNextNodeIndex(); index < nodeCount; index += step) {
			Vec3 node = path.getEntityPosAtNode(spider, index);
			checks++;
			if (previous != null && previous.y - node.y > DANGEROUS_DROP) {
				return new Assessment(Status.DANGEROUS_DROP, path, checks);
			}
			previous = node;
			AABB carrierBox = AABB.ofSize(
				node.add(0.0, carrierHeight * 0.5, 0.0),
				carrierWidth,
				carrierHeight,
				carrierWidth
			);
			// 路线净空只验证静态方块。目标和会合中的载荷会移动，若把实体碰撞也算进来，
			// 平地路径的起点/终点反而会被当前乘员或目标自身误判为“窄道”。
			boolean combinedClear = spider.level().noBlockCollision(spider, carrierBox);
			if (combinedClear) {
				continue;
			}
			AABB spiderOnlyBox = AABB.ofSize(
				node.add(0.0, (spider.getBbHeight() + HEIGHT_MARGIN) * 0.5, 0.0),
				carrierWidth,
				spider.getBbHeight() + HEIGHT_MARGIN,
				carrierWidth
			);
			return new Assessment(
				classifyClearance(false, spider.level().noBlockCollision(spider, spiderOnlyBox)),
				path,
				checks
			);
		}
		return new Assessment(Status.CLEAR, path, checks);
	}

	static Status classifyClearance(final boolean combinedClear, final boolean spiderOnlyClear) {
		if (combinedClear) {
			return Status.CLEAR;
		}
		return spiderOnlyClear ? Status.LOW_CEILING : Status.NARROW;
	}

	private static double combinedHeight(final Spider spider, final double passengerHeight) {
		return Math.max(
			spider.getBbHeight() + HEIGHT_MARGIN,
			spider.getBbHeight() + Math.max(0.0, passengerHeight) + HEIGHT_MARGIN
		);
	}

	private static double combinedWidth(final Spider spider) {
		return spider.getBbWidth() + WIDTH_MARGIN;
	}

	private static double findSupportSurfaceY(final Spider spider, final Vec3 expectedFeet) {
		BlockPos feet = BlockPos.containing(expectedFeet.add(0.0, 0.1, 0.0));
		for (int depth = 1; depth <= 4; depth++) {
			BlockPos support = feet.below(depth);
			if (spider.level().getBlockState(support).isFaceSturdy(spider.level(), support, Direction.UP)) {
				return support.getY() + 1.0;
			}
		}
		return Double.NaN;
	}

	private static double lerp(final double progress, final double start, final double end) {
		return start + (end - start) * progress;
	}

	/** 返回载荷可以站立且不会卡入方块的邻接落点；找不到时保持骑乘，避免把实体塞墙里。 */
	public static @Nullable Vec3 findSafeDismount(final Spider spider, final Mob passenger) {
		for (Vec3 offset : DISMOUNT_OFFSETS) {
			Vec3 candidate = spider.position().add(offset);
			BlockPos feet = BlockPos.containing(candidate);
			BlockPos support = feet.below();
			if (!spider.level().getBlockState(support).isFaceSturdy(spider.level(), support, Direction.UP)) {
				continue;
			}
			AABB body = AABB.ofSize(
				Vec3.atBottomCenterOf(feet).add(0.0, passenger.getBbHeight() * 0.5, 0.0),
				passenger.getBbWidth(),
				passenger.getBbHeight(),
				passenger.getBbWidth()
			);
			if (spider.level().noCollision(passenger, body)) {
				return Vec3.atBottomCenterOf(feet);
			}
		}
		return null;
	}

	public enum Status {
		CLEAR,
		UNREACHABLE,
		LOW_CEILING,
		NARROW,
		DANGEROUS_DROP;

		public boolean usable() {
			return this == CLEAR;
		}
	}

	public record Assessment(Status status, @Nullable Path path, int sampledNodes) {
		public boolean usable() {
			return this.status.usable();
		}
	}
}
