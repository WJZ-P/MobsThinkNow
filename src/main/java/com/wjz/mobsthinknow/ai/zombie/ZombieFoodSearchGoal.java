package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * 中高智力僵尸的低血觅食 Goal。
 *
 * <p>优先级位于撤退和战斗之间：受到重击时撤退仍能立即打断进食；没有可达食物时
 * {@link #canUse()} 直接返回 false，原来的追击、小队与武器行为完全照旧。</p>
 *
 * <p>性能上先以 2～4 秒随机间隔和智力概率做门控，再查 12 格局部实体索引；每次最多为
 * 4 个最近候选计算路径，不会每 tick 扫描，也不会遍历世界中的全部掉落物。</p>
 */
public final class ZombieFoodSearchGoal extends Goal {
	private static final double FOOD_HEALTH_FRACTION = 0.50;
	private static final double SEARCH_RADIUS = 12.0;
	private static final double SEARCH_VERTICAL_RADIUS = 4.0;
	private static final double PICKUP_DISTANCE_SQUARED = 2.25;
	private static final double MOVE_SPEED_MODIFIER = 1.15;
	private static final int MINIMUM_SEARCH_DELAY_TICKS = 40;
	private static final int SEARCH_DELAY_VARIANCE_TICKS = 40;
	private static final int SEARCH_TIMEOUT_TICKS = 200;
	private static final int PATH_REFRESH_TICKS = 10;
	private static final int MAXIMUM_PATH_CANDIDATES = 4;

	private final Zombie zombie;
	private final SearchDecision searchDecision;
	private Phase phase = Phase.IDLE;
	private @Nullable ItemEntity targetFood;
	private @Nullable Path initialPath;
	private ItemStack mealTemplate = ItemStack.EMPTY;
	private InteractionHand mealHand = InteractionHand.MAIN_HAND;
	private int nutrition;
	private long nextSearchAt;
	private long searchDeadline;
	private long nextPathUpdateAt;
	private long eatingCompletesAt;

	public ZombieFoodSearchGoal(final Zombie zombie) {
		this(
			zombie,
			(candidate, intelligence, minimumIntelligence) -> candidate.getRandom().nextFloat()
				< searchChance(intelligence, minimumIntelligence)
		);
	}

	/** 测试可注入确定性决策；生产构造器始终使用单只僵尸自己的随机源。 */
	ZombieFoodSearchGoal(final Zombie zombie, final SearchDecision searchDecision) {
		this.zombie = zombie;
		this.searchDecision = searchDecision;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		MobsThinkNowConfig config = ConfigManager.get();
		if (!canSearchNow(this.zombie, config) || !(this.zombie.level() instanceof ServerLevel level)) {
			return false;
		}
		if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return false;
		}

		long now = level.getGameTime();
		if (now < this.nextSearchAt) {
			return false;
		}
		this.nextSearchAt = now
			+ MINIMUM_SEARCH_DELAY_TICKS
			+ this.zombie.getRandom().nextInt(SEARCH_DELAY_VARIANCE_TICKS + 1);

		int intelligence = ZombieIntelligence.get(this.zombie);
		if (intelligence < config.foodMinimumIntelligence
			|| !this.searchDecision.shouldSearch(this.zombie, intelligence, config.foodMinimumIntelligence)) {
			return false;
		}

		SearchTarget found = this.findReachableFood(level);
		if (found == null) {
			return false;
		}
		this.targetFood = found.entity();
		this.initialPath = found.path();
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		MobsThinkNowConfig config = ConfigManager.get();
		if (!isEnabled(config) || !this.zombie.isAlive()) {
			return false;
		}
		return switch (this.phase) {
			case SEEKING -> isBelowFoodThreshold(this.zombie.getHealth(), this.zombie.getMaxHealth())
				&& this.zombie.level().getGameTime() < this.searchDeadline
				&& isAvailableFoodEntity(this.targetFood);
			case EATING -> ZombieFoodEquipment.isActive(this.zombie);
			case IDLE, DONE -> false;
		};
	}

	@Override
	public void start() {
		long now = this.zombie.level().getGameTime();
		this.phase = Phase.SEEKING;
		this.searchDeadline = now + SEARCH_TIMEOUT_TICKS;
		this.nextPathUpdateAt = now + PATH_REFRESH_TICKS;
		this.zombie.stopUsingItem();
		this.zombie.setAggressive(false);
		if (this.initialPath != null) {
			this.zombie.getNavigation().moveTo(this.initialPath, MOVE_SPEED_MODIFIER);
		}
	}

	@Override
	public void tick() {
		switch (this.phase) {
			case SEEKING -> this.tickSeeking();
			case EATING -> this.tickEating();
			case IDLE, DONE -> {
			}
		}
	}

	@Override
	public void stop() {
		this.zombie.getNavigation().stop();
		if (ZombieFoodEquipment.isActive(this.zombie)) {
			// 撤退、配置热重载、死亡等中断会把尚未吃完的一份食物放回脚边并恢复装备。
			ZombieFoodEquipment.restore(this.zombie, true);
		}
		this.phase = Phase.IDLE;
		this.targetFood = null;
		this.initialPath = null;
		this.mealTemplate = ItemStack.EMPTY;
		this.mealHand = InteractionHand.MAIN_HAND;
		this.nutrition = 0;
		this.searchDeadline = 0L;
		this.eatingCompletesAt = 0L;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	private void tickSeeking() {
		ItemEntity food = this.targetFood;
		if (!isAvailableFoodEntity(food)) {
			this.phase = Phase.DONE;
			return;
		}
		this.zombie.getLookControl().setLookAt(food, 30.0F, 30.0F);
		if (this.zombie.distanceToSqr(food) <= PICKUP_DISTANCE_SQUARED) {
			this.beginEating(food);
			return;
		}

		long now = this.zombie.level().getGameTime();
		if (now >= this.nextPathUpdateAt) {
			this.zombie.getNavigation().moveTo(food, MOVE_SPEED_MODIFIER);
			this.nextPathUpdateAt = now + PATH_REFRESH_TICKS;
		}
	}

	private void beginEating(final ItemEntity foodEntity) {
		ItemStack groundStack = foodEntity.getItem();
		FoodProperties food = groundStack.get(DataComponents.FOOD);
		Consumable consumable = groundStack.get(DataComponents.CONSUMABLE);
		if (food == null || food.nutrition() <= 0 || consumable == null || consumable.consumeTicks() <= 0) {
			this.phase = Phase.DONE;
			return;
		}

		// 先发送原版拾取动画包，再从地面堆叠中只拆一份；其余食物仍留在原 ItemEntity 中。
		this.zombie.onItemPickup(foodEntity);
		this.zombie.take(foodEntity, 1);
		ItemStack serving = groundStack.split(1);
		if (groundStack.isEmpty()) {
			foodEntity.discard();
		}

		InteractionHand hand = preferredFoodHand(!this.zombie.getMainHandItem().isEmpty());
		ZombieFoodEquipment.begin(this.zombie, hand, serving);
		this.zombie.getNavigation().stop();
		this.zombie.setAggressive(false);
		this.mealTemplate = serving.copy();
		this.mealHand = hand;
		this.nutrition = food.nutrition();
		this.eatingCompletesAt = this.zombie.level().getGameTime() + serving.getUseDuration(this.zombie);
		this.phase = Phase.EATING;
		this.zombie.startUsingItem(hand);
		if (!this.zombie.isUsingItem()) {
			// 自定义食物若声明了异常的零时长，干净中止并把该份食物放回地面。
			this.phase = Phase.DONE;
		}
	}

	private void tickEating() {
		this.zombie.getNavigation().stop();
		this.zombie.setAggressive(false);
		if (this.zombie.isUsingItem()) {
			return;
		}

		ItemStack current = this.zombie.getItemInHand(this.mealHand);
		boolean elapsed = this.zombie.level().getGameTime() >= this.eatingCompletesAt;
		boolean servingWasConsumed = current.isEmpty()
			|| !ItemStack.isSameItemSameComponents(current, this.mealTemplate);
		if (elapsed && servingWasConsumed) {
			// MC 生命值以 1 点=半颗心计；面包 nutrition=5，因此立即恢复 5 点生命值。
			this.zombie.heal(this.nutrition);
		}
		ZombieFoodEquipment.restore(this.zombie, true);
		this.phase = Phase.DONE;
	}

	private @Nullable SearchTarget findReachableFood(final ServerLevel level) {
		AABB searchBox = this.zombie.getBoundingBox().inflate(SEARCH_RADIUS, SEARCH_VERTICAL_RADIUS, SEARCH_RADIUS);
		List<ItemEntity> foods = level.getEntitiesOfClass(
			ItemEntity.class,
			searchBox,
			entity -> isAvailableFoodEntity(entity)
				&& this.zombie.distanceToSqr(entity) <= SEARCH_RADIUS * SEARCH_RADIUS
		);
		foods.sort(
			Comparator.<ItemEntity>comparingInt(entity -> foodPriority(entity.getItem())).reversed()
				.thenComparing(Comparator.comparingInt(
					(ItemEntity entity) -> nutritionOf(entity.getItem())
				).reversed())
				.thenComparingDouble(entity -> this.zombie.distanceToSqr(entity))
				.thenComparingInt(ItemEntity::getId)
		);

		int pathChecks = 0;
		for (ItemEntity food : foods) {
			if (this.zombie.distanceToSqr(food) <= PICKUP_DISTANCE_SQUARED) {
				return new SearchTarget(food, null);
			}
			if (pathChecks++ >= MAXIMUM_PATH_CANDIDATES) {
				break;
			}
			Path path = this.zombie.getNavigation().createPath(food, 1);
			if (path != null && path.canReach()) {
				return new SearchTarget(food, path);
			}
		}
		return null;
	}

	/** 原版 looting 不再把食物当武器装备；所有食物拾取统一走上面的单份消费事务。 */
	public static boolean managesFood(final Zombie zombie, final ItemStack stack, final MobsThinkNowConfig config) {
		return zombie.getType() == EntityType.ZOMBIE && isEnabled(config) && isFood(stack);
	}

	public static boolean isFood(final ItemStack stack) {
		FoodProperties food = stack.get(DataComponents.FOOD);
		Consumable consumable = stack.get(DataComponents.CONSUMABLE);
		return food != null && food.nutrition() > 0 && consumable != null && consumable.consumeTicks() > 0;
	}

	/**
	 * 搜索优先级严格高于距离：附魔金苹果 > 金苹果 > 其余食物。普通层再按营养值排序，
	 * 因此腐肉仍是完全合法的兜底食物，只会在附近没有更高价值补给时被选择。
	 */
	static int foodPriority(final ItemStack stack) {
		if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
			return 3;
		}
		if (stack.is(Items.GOLDEN_APPLE)) {
			return 2;
		}
		return isFood(stack) ? 1 : 0;
	}

	private static int nutritionOf(final ItemStack stack) {
		FoodProperties food = stack.get(DataComponents.FOOD);
		return food == null ? 0 : food.nutrition();
	}

	static boolean isBelowFoodThreshold(final float health, final float maximumHealth) {
		return health > 0.0F && maximumHealth > 0.0F && health < maximumHealth * FOOD_HEALTH_FRACTION;
	}

	static double searchChance(final int intelligence, final int minimumIntelligence) {
		if (intelligence < minimumIntelligence) {
			return 0.0;
		}
		// 默认 IQ 6 为 25%，每高一级增加 10%；IQ 10 每个搜索机会有 65% 概率主动觅食。
		return Math.min(0.75, 0.25 + (intelligence - minimumIntelligence) * 0.10);
	}

	static InteractionHand preferredFoodHand(final boolean mainHandOccupied) {
		// 主手空着就直接使用；主手有武器/工具/其他物品时优先副手，副手原物会被安全暂存。
		return mainHandOccupied ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
	}

	private static boolean canSearchNow(final Zombie zombie, final MobsThinkNowConfig config) {
		return zombie.getType() == EntityType.ZOMBIE
			&& isEnabled(config)
			&& zombie.isAlive()
			&& isBelowFoodThreshold(zombie.getHealth(), zombie.getMaxHealth());
	}

	private static boolean isEnabled(final MobsThinkNowConfig config) {
		return config.enabled && config.zombieAiEnabled && config.foodScavenging;
	}

	private static boolean isAvailableFoodEntity(final @Nullable ItemEntity entity) {
		return entity != null
			&& !entity.isRemoved()
			&& !entity.hasPickUpDelay()
			&& isFood(entity.getItem());
	}

	@FunctionalInterface
	interface SearchDecision {
		boolean shouldSearch(Zombie zombie, int intelligence, int minimumIntelligence);
	}

	private enum Phase {
		IDLE,
		SEEKING,
		EATING,
		DONE
	}

	private record SearchTarget(ItemEntity entity, @Nullable Path path) {
	}
}
