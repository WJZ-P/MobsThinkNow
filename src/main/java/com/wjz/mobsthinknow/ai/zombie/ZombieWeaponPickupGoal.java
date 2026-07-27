package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.ai.zombie.squad.WeaponClass;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * 让普通僵尸主动寻找并装备地面近战武器。
 *
 * <p>搜索只在 1～2.5 秒的错峰窗口触发，局部实体查询后最多为四件武器寻路。候选先按攻击伤害、
 * 附魔和耐久排序，再比较距离，因此僵尸不会因为一把较近的木剑而错过同一区域里的铁剑。</p>
 *
 * <p>空手或主手为普通杂物时直接换装；已有武器时只接受严格升级。水桶、岩浆桶和已部署流体的
 * 空桶属于战术装备，不会被替换。旧物会真实掉回脚边，拾到的新武器沿用原版拾取装备的必掉落和
 * 持久化语义，不会被静默删除。</p>
 */
public final class ZombieWeaponPickupGoal extends Goal {
	private static final double SEARCH_RADIUS = 12.0;
	private static final double SEARCH_VERTICAL_RADIUS = 4.0;
	private static final double PICKUP_DISTANCE_SQUARED = 2.25;
	private static final double MOVE_SPEED_MODIFIER = 1.20;
	private static final int MINIMUM_SEARCH_DELAY_TICKS = 20;
	private static final int SEARCH_DELAY_VARIANCE_TICKS = 20;
	private static final int SEARCH_TIMEOUT_TICKS = 200;
	private static final int PATH_REFRESH_TICKS = 10;
	private static final int MAXIMUM_PATH_CANDIDATES = 4;

	private final Zombie zombie;
	private Phase phase = Phase.IDLE;
	private @Nullable ItemEntity targetWeapon;
	private @Nullable Path initialPath;
	private long nextSearchAt;
	private long searchDeadline;
	private long nextPathUpdateAt;

	public ZombieWeaponPickupGoal(final Zombie zombie) {
		this.zombie = zombie;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		MobsThinkNowConfig config = ConfigManager.get();
		if (!isEnabled(this.zombie, config)
			|| ZombieFoodEquipment.isActive(this.zombie)
			|| !(this.zombie.level() instanceof ServerLevel level)
			|| !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return false;
		}

		long now = level.getGameTime();
		if (now < this.nextSearchAt) {
			return false;
		}
		this.nextSearchAt = now
			+ MINIMUM_SEARCH_DELAY_TICKS
			+ Math.floorMod(this.zombie.getId(), 7)
			+ this.zombie.getRandom().nextInt(SEARCH_DELAY_VARIANCE_TICKS + 1);

		SearchTarget found = this.findReachableWeapon(level);
		if (found == null) {
			return false;
		}
		this.targetWeapon = found.entity();
		this.initialPath = found.path();
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		MobsThinkNowConfig config = ConfigManager.get();
		return this.phase == Phase.SEEKING
			&& isEnabled(this.zombie, config)
			&& !ZombieFoodEquipment.isActive(this.zombie)
			&& this.zombie.level().getGameTime() < this.searchDeadline
			&& isAvailableWeaponEntity(this.targetWeapon)
			&& canReplaceMainHand(this.zombie.getMainHandItem(), this.targetWeapon.getItem());
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
		ItemEntity weapon = this.targetWeapon;
		if (this.phase != Phase.SEEKING
			|| !isAvailableWeaponEntity(weapon)
			|| !canReplaceMainHand(this.zombie.getMainHandItem(), weapon.getItem())) {
			this.phase = Phase.DONE;
			return;
		}

		this.zombie.setAggressive(false);
		this.zombie.getLookControl().setLookAt(weapon, 30.0F, 30.0F);
		if (this.zombie.distanceToSqr(weapon) <= PICKUP_DISTANCE_SQUARED) {
			this.equipOne(weapon);
			return;
		}

		long now = this.zombie.level().getGameTime();
		if (now >= this.nextPathUpdateAt) {
			this.zombie.getNavigation().moveTo(weapon, MOVE_SPEED_MODIFIER);
			this.nextPathUpdateAt = now + PATH_REFRESH_TICKS;
		}
	}

	@Override
	public void stop() {
		this.zombie.getNavigation().stop();
		this.zombie.setAggressive(false);
		this.phase = Phase.IDLE;
		this.targetWeapon = null;
		this.initialPath = null;
		this.searchDeadline = 0L;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	private void equipOne(final ItemEntity weaponEntity) {
		if (!(this.zombie.level() instanceof ServerLevel level)) {
			this.phase = Phase.DONE;
			return;
		}
		ItemStack groundStack = weaponEntity.getItem();
		ItemStack current = this.zombie.getMainHandItem().copy();
		if (!canReplaceMainHand(current, groundStack)) {
			this.phase = Phase.DONE;
			return;
		}

		// 与原版 Mob.pickUpItem 相同：先广播拾取动画，再只从地面堆叠中拆出一件。
		this.zombie.onItemPickup(weaponEntity);
		this.zombie.take(weaponEntity, 1);
		ItemStack equipped = groundStack.split(1);
		if (groundStack.isEmpty()) {
			weaponEntity.discard();
		}

		this.zombie.getNavigation().stop();
		this.zombie.stopUsingItem();
		this.zombie.setItemSlot(EquipmentSlot.MAINHAND, equipped);
		this.zombie.setGuaranteedDrop(EquipmentSlot.MAINHAND);
		this.zombie.setPersistenceRequired();
		if (!current.isEmpty()) {
			this.zombie.spawnAtLocation(level, current);
		}
		this.phase = Phase.DONE;
	}

	private @Nullable SearchTarget findReachableWeapon(final ServerLevel level) {
		ItemStack current = this.zombie.getMainHandItem();
		AABB searchBox = this.zombie.getBoundingBox().inflate(SEARCH_RADIUS, SEARCH_VERTICAL_RADIUS, SEARCH_RADIUS);
		List<ItemEntity> weapons = level.getEntitiesOfClass(
			ItemEntity.class,
			searchBox,
			entity -> isAvailableWeaponEntity(entity)
				&& this.zombie.distanceToSqr(entity) <= SEARCH_RADIUS * SEARCH_RADIUS
				&& canReplaceMainHand(current, entity.getItem())
		);
		weapons.sort((first, second) -> {
			int quality = compareWeaponQuality(second.getItem(), first.getItem());
			if (quality != 0) {
				return quality;
			}
			int distance = Double.compare(this.zombie.distanceToSqr(first), this.zombie.distanceToSqr(second));
			return distance != 0 ? distance : Integer.compare(first.getId(), second.getId());
		});

		int pathChecks = 0;
		for (ItemEntity weapon : weapons) {
			if (this.zombie.distanceToSqr(weapon) <= PICKUP_DISTANCE_SQUARED) {
				return new SearchTarget(weapon, null);
			}
			if (pathChecks++ >= MAXIMUM_PATH_CANDIDATES) {
				break;
			}
			Path path = this.zombie.getNavigation().createPath(weapon, 1);
			if (path != null && path.canReach()) {
				return new SearchTarget(weapon, path);
			}
		}
		return null;
	}

	/** 阻止原版随机 looting 抢先处理武器；选择、比较和换装统一由本 Goal 完成。 */
	public static boolean managesWeapon(final Zombie zombie, final ItemStack stack, final MobsThinkNowConfig config) {
		return zombie.getType() == EntityType.ZOMBIE
			&& config.enabled
			&& config.zombieAiEnabled
			&& isMeleeWeapon(stack);
	}

	static boolean canReplaceMainHand(final ItemStack current, final ItemStack candidate) {
		if (!isMeleeWeapon(candidate) || isProtectedUtility(current)) {
			return false;
		}
		if (current.isEmpty() || !isMeleeWeapon(current)) {
			return true;
		}
		return compareWeaponQuality(candidate, current) > 0;
	}

	static boolean isMeleeWeapon(final ItemStack stack) {
		return !stack.isEmpty()
			&& (ZombieArmory.weaponClassOf(stack) != WeaponClass.NONE
				|| stack.is(ItemTags.MELEE_WEAPON_ENCHANTABLE));
	}

	/** 正数表示 first 更好；顺序与原版 Mob.compareWeapons 对齐。 */
	static int compareWeaponQuality(final ItemStack first, final ItemStack second) {
		int damage = Double.compare(attackDamageOf(first), attackDamageOf(second));
		if (damage != 0) {
			return damage;
		}
		ItemEnchantments firstEnchantments = first.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		ItemEnchantments secondEnchantments = second.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		int enchantments = Integer.compare(firstEnchantments.entrySet().size(), secondEnchantments.entrySet().size());
		if (enchantments != 0) {
			return enchantments;
		}
		return Integer.compare(second.getDamageValue(), first.getDamageValue());
	}

	private static double attackDamageOf(final ItemStack stack) {
		ItemAttributeModifiers modifiers = stack.getOrDefault(
			DataComponents.ATTRIBUTE_MODIFIERS,
			ItemAttributeModifiers.EMPTY
		);
		return modifiers.compute(Attributes.ATTACK_DAMAGE, 0.0, EquipmentSlot.MAINHAND);
	}

	private static boolean isProtectedUtility(final ItemStack stack) {
		return stack.is(Items.WATER_BUCKET) || stack.is(Items.LAVA_BUCKET) || stack.is(Items.BUCKET);
	}

	private static boolean isEnabled(final Zombie zombie, final MobsThinkNowConfig config) {
		return zombie.getType() == EntityType.ZOMBIE
			&& zombie.isAlive()
			&& config.enabled
			&& config.zombieAiEnabled;
	}

	private static boolean isAvailableWeaponEntity(final @Nullable ItemEntity entity) {
		return entity != null
			&& !entity.isRemoved()
			&& !entity.hasPickUpDelay()
			&& isMeleeWeapon(entity.getItem());
	}

	private enum Phase {
		IDLE,
		SEEKING,
		DONE
	}

	private record SearchTarget(ItemEntity entity, @Nullable Path path) {
	}
}
