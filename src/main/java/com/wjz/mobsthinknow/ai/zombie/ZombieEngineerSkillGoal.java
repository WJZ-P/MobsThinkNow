package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.ai.zombie.squad.UtilityClass;
import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 正式工程兵的低频随机技能调度器。
 *
 * <p>一次技能结束后随机等待 6～10 秒，再从当前真正可执行的技能中等概率选择：</p>
 * <ul>
 *     <li><b>爆破装药</b>：寻找目标附近有地基、无实体和无友军的可达格，显示 TNT 放置与
 *     打火石点燃动作，生成带工程兵 owner 的原版 {@link PrimedTnt}，随后背向装药撤离；</li>
 *     <li><b>战地维修</b>：接近自己或附近同目标/同小队僵尸，修复损坏最严重的一件装备；</li>
 *     <li><b>临时加固</b>：给八格内至多二十名同目标/同小队僵尸施加五秒抗性提升。</li>
 * </ul>
 *
 * <p>性能上没有逐 tick 范围扫描：冷却到期时才做一次局部查询，候选数量和寻路次数都有硬上限。
 * TNT 同时服从配置、{@code mobGriefing} 与 {@code tntExplodes}，未点燃前被更高优先级 Goal
 * 打断会回收本 Goal 放置的方块，不留下无主装药。</p>
 */
public final class ZombieEngineerSkillGoal extends Goal {
	static final int MINIMUM_SKILL_DELAY_TICKS = 120;
	static final int MAXIMUM_SKILL_DELAY_TICKS = 200;
	private static final int SKILL_DELAY_RANGE = MAXIMUM_SKILL_DELAY_TICKS - MINIMUM_SKILL_DELAY_TICKS;
	private static final double TARGET_MAXIMUM_DISTANCE_SQUARED = 18.0 * 18.0;
	private static final double TNT_INTERACTION_DISTANCE_SQUARED = 3.25 * 3.25;
	private static final double TNT_ALLY_SAFETY_RADIUS = 3.25;
	private static final int TNT_PLACEMENT_WINDUP_TICKS = 8;
	private static final int TNT_IGNITION_WINDUP_TICKS = 12;
	private static final int TNT_FUSE_TICKS = 80;
	private static final int TNT_RETREAT_MAXIMUM_TICKS = 60;
	private static final double TNT_RETREAT_SAFE_DISTANCE_SQUARED = 8.0 * 8.0;
	private static final double TNT_RETREAT_SPEED = 1.35;
	private static final int PATH_REFRESH_TICKS = 10;
	private static final double REPAIR_REACH_SQUARED = 3.0 * 3.0;
	private static final double REPAIR_SEARCH_RADIUS = 8.0;
	private static final int MAXIMUM_REPAIR_PATH_CHECKS = 4;
	private static final int REPAIR_WINDUP_TICKS = 16;
	private static final int FORTIFY_WINDUP_TICKS = 14;
	private static final int FORTIFY_DURATION_TICKS = 100;
	private static final double FORTIFY_RADIUS = 8.0;
	private static final int MAXIMUM_ALLY_CANDIDATES = 20;
	private static final int[][] TNT_OFFSETS = {
		{0, 0},
		{1, 0}, {-1, 0}, {0, 1}, {0, -1},
		{1, 1}, {1, -1}, {-1, 1}, {-1, -1}
	};
	private static final List<EquipmentSlot> REPAIRABLE_SLOTS = List.of(
		EquipmentSlot.MAINHAND,
		EquipmentSlot.OFFHAND,
		EquipmentSlot.HEAD,
		EquipmentSlot.CHEST,
		EquipmentSlot.LEGS,
		EquipmentSlot.FEET
	);

	private final Zombie zombie;
	private final SkillDecision skillDecision;
	private Phase phase = Phase.IDLE;
	private @Nullable Skill selectedSkill;
	private @Nullable LivingEntity combatTarget;
	private @Nullable TntPlan tntPlan;
	private @Nullable RepairPlan repairPlan;
	private @Nullable Vec3 retreatDestination;
	private InteractionHand visibleHand = InteractionHand.MAIN_HAND;
	private long nextSkillAt;
	private long phaseDeadline;
	private long nextPathRefreshAt;
	private boolean placedTnt;
	private boolean primedTnt;

	public ZombieEngineerSkillGoal(final Zombie zombie) {
		this(
			zombie,
			(candidate, available) -> available.get(candidate.getRandom().nextInt(available.size())),
			skillDelayTicks(zombie.getRandom().nextDouble())
		);
	}

	/** 测试可以固定技能和首次等待；生产构造器始终使用单只僵尸自己的随机源。 */
	ZombieEngineerSkillGoal(
		final Zombie zombie,
		final SkillDecision skillDecision,
		final int initialDelayTicks
	) {
		this.zombie = zombie;
		this.skillDecision = skillDecision;
		this.nextSkillAt = zombie.level().getGameTime() + Math.max(0, initialDelayTicks);
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		MobsThinkNowConfig config = ConfigManager.get();
		if (!isEnabled(this.zombie, config) || !(this.zombie.level() instanceof ServerLevel level)) {
			return false;
		}
		long now = level.getGameTime();
		if (now < this.nextSkillAt) {
			return false;
		}

		LivingEntity target = this.zombie.getTarget();
		if (!isEligibleTarget(target)
			|| this.zombie.distanceToSqr(target) > TARGET_MAXIMUM_DISTANCE_SQUARED) {
			// 没有战斗目标时不白白消费一个完整技能周期；一秒后再看即可。
			this.nextSkillAt = now + 20L;
			return false;
		}

		this.clearPreparedSkill();
		this.combatTarget = target;
		List<Skill> available = new ArrayList<>(3);
		if (config.engineerTntSkill
			&& level.getGameRules().get(GameRules.MOB_GRIEFING)
			&& level.getGameRules().get(GameRules.TNT_EXPLODES)) {
			this.tntPlan = this.findTntPlan(level, target);
			if (this.tntPlan != null) {
				available.add(Skill.DEMOLITION_CHARGE);
			}
		}
		this.repairPlan = this.findRepairPlan(level);
		if (this.repairPlan != null) {
			available.add(Skill.FIELD_REPAIR);
		}
		// 自身永远是合法加固对象，因此只要仍在战斗，技能池就不会为空。
		available.add(Skill.FORTIFY_SQUAD);

		Skill chosen = this.skillDecision.choose(this.zombie, List.copyOf(available));
		if (chosen == null || !available.contains(chosen)) {
			this.nextSkillAt = now + 20L;
			this.clearPreparedSkill();
			return false;
		}
		this.selectedSkill = chosen;
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		MobsThinkNowConfig config = ConfigManager.get();
		if (!isEnabled(this.zombie, config)
			|| !(this.zombie.level() instanceof ServerLevel level)
			|| this.phase == Phase.IDLE
			|| this.phase == Phase.DONE) {
			return false;
		}
		if (this.isTntPhase() && (!config.engineerTntSkill
			|| !level.getGameRules().get(GameRules.MOB_GRIEFING)
			|| !level.getGameRules().get(GameRules.TNT_EXPLODES))) {
			return false;
		}
		if (this.phase == Phase.TNT_RETREAT) {
			return level.getGameTime() < this.phaseDeadline
				&& this.tntPlan != null
				&& this.zombie.position().distanceToSqr(Vec3.atCenterOf(this.tntPlan.position()))
					< TNT_RETREAT_SAFE_DISTANCE_SQUARED;
		}

		LivingEntity target = this.combatTarget;
		if (target == null || !target.isAlive() || this.zombie.getTarget() != target) {
			return false;
		}
		if ((this.phase == Phase.MOVING_TO_REPAIR || this.phase == Phase.REPAIRING)
			&& (this.repairPlan == null || !this.repairPlan.recipient().isAlive())) {
			return false;
		}
		return true;
	}

	@Override
	public void start() {
		this.zombie.getNavigation().stop();
		this.zombie.stopUsingItem();
		this.zombie.setAggressive(false);
		long now = this.zombie.level().getGameTime();
		this.nextPathRefreshAt = now + PATH_REFRESH_TICKS;

		if (this.selectedSkill == Skill.DEMOLITION_CHARGE && this.tntPlan != null) {
			this.visibleHand = ZombieEngineerEquipment.begin(this.zombie, new ItemStack(Items.TNT));
			if (this.isWithinTntReach(this.tntPlan.position())) {
				this.beginTntPlacement(now);
			} else {
				this.phase = Phase.MOVING_TO_TNT_SITE;
				this.beginTntNavigation();
			}
			return;
		}
		if (this.selectedSkill == Skill.FIELD_REPAIR && this.repairPlan != null) {
			this.visibleHand = ZombieEngineerEquipment.begin(this.zombie, new ItemStack(Items.IRON_INGOT));
			if (this.zombie.distanceToSqr(this.repairPlan.recipient()) <= REPAIR_REACH_SQUARED) {
				this.beginRepair(now);
			} else {
				this.phase = Phase.MOVING_TO_REPAIR;
				this.beginRepairNavigation();
			}
			return;
		}
		if (this.selectedSkill == Skill.FORTIFY_SQUAD) {
			this.visibleHand = ZombieEngineerEquipment.begin(this.zombie, new ItemStack(Items.IRON_INGOT));
			this.phase = Phase.FORTIFYING;
			this.phaseDeadline = now + FORTIFY_WINDUP_TICKS;
			return;
		}
		this.phase = Phase.DONE;
	}

	@Override
	public void tick() {
		this.zombie.setAggressive(false);
		switch (this.phase) {
			case MOVING_TO_TNT_SITE -> this.tickMovingToTnt();
			case PLACING_TNT -> this.tickPlacingTnt();
			case ARMING_TNT -> this.tickArmingTnt();
			case TNT_RETREAT -> this.tickTntRetreat();
			case MOVING_TO_REPAIR -> this.tickMovingToRepair();
			case REPAIRING -> this.tickRepairing();
			case FORTIFYING -> this.tickFortifying();
			case IDLE, DONE -> {
			}
		}
	}

	@Override
	public void stop() {
		this.cleanupUnprimedTnt();
		ZombieEngineerEquipment.restore(this.zombie, false);
		this.zombie.getNavigation().stop();
		this.zombie.setAggressive(false);
		this.nextSkillAt = this.zombie.level().getGameTime()
			+ skillDelayTicks(this.zombie.getRandom().nextDouble());
		this.phase = Phase.IDLE;
		this.selectedSkill = null;
		this.combatTarget = null;
		this.tntPlan = null;
		this.repairPlan = null;
		this.retreatDestination = null;
		this.phaseDeadline = 0L;
		this.nextPathRefreshAt = 0L;
		this.placedTnt = false;
		this.primedTnt = false;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	private void tickMovingToTnt() {
		TntPlan plan = this.tntPlan;
		if (plan == null || !(this.zombie.level() instanceof ServerLevel level)
			|| !this.isValidTntSite(level, plan.position())) {
			this.phase = Phase.DONE;
			return;
		}
		this.lookAt(plan.position());
		if (this.isWithinTntReach(plan.position())) {
			this.zombie.getNavigation().stop();
			this.beginTntPlacement(level.getGameTime());
			return;
		}
		if (level.getGameTime() >= this.nextPathRefreshAt) {
			this.beginTntNavigation();
			this.nextPathRefreshAt = level.getGameTime() + PATH_REFRESH_TICKS;
		}
	}

	private void beginTntPlacement(final long now) {
		this.phase = Phase.PLACING_TNT;
		this.phaseDeadline = now + TNT_PLACEMENT_WINDUP_TICKS;
	}

	private void tickPlacingTnt() {
		TntPlan plan = this.tntPlan;
		if (plan == null || !(this.zombie.level() instanceof ServerLevel level)) {
			this.phase = Phase.DONE;
			return;
		}
		this.zombie.getNavigation().stop();
		this.lookAt(plan.position());
		if (level.getGameTime() < this.phaseDeadline) {
			return;
		}
		if (!this.isWithinTntReach(plan.position()) || !this.placeTnt(level, plan.position())) {
			this.phase = Phase.DONE;
			return;
		}
		this.visibleHand = ZombieEngineerEquipment.show(this.zombie, new ItemStack(Items.FLINT_AND_STEEL));
		this.phase = Phase.ARMING_TNT;
		this.phaseDeadline = level.getGameTime() + TNT_IGNITION_WINDUP_TICKS;
	}

	private void tickArmingTnt() {
		TntPlan plan = this.tntPlan;
		if (plan == null || !(this.zombie.level() instanceof ServerLevel level)) {
			this.phase = Phase.DONE;
			return;
		}
		this.zombie.getNavigation().stop();
		this.lookAt(plan.position());
		if (level.getGameTime() < this.phaseDeadline) {
			return;
		}
		if (!level.getBlockState(plan.position()).is(Blocks.TNT)) {
			this.phase = Phase.DONE;
			return;
		}
		this.zombie.swing(this.visibleHand);
		level.playSound(
			null,
			plan.position(),
			SoundEvents.FLINTANDSTEEL_USE,
			SoundSource.BLOCKS,
			1.0F,
			0.9F + this.zombie.getRandom().nextFloat() * 0.2F
		);
		if (!this.primeTnt(level, plan.position())) {
			this.phase = Phase.DONE;
			return;
		}
		ZombieEngineerEquipment.restore(this.zombie, false);
		this.phase = Phase.TNT_RETREAT;
		this.phaseDeadline = level.getGameTime() + TNT_RETREAT_MAXIMUM_TICKS;
		this.retreatDestination = null;
		this.nextPathRefreshAt = level.getGameTime();
		SmartZombieMetrics.engineerTntCharge();
	}

	private void tickTntRetreat() {
		TntPlan plan = this.tntPlan;
		if (plan == null) {
			this.phase = Phase.DONE;
			return;
		}
		Vec3 charge = Vec3.atCenterOf(plan.position());
		if (this.zombie.position().distanceToSqr(charge) >= TNT_RETREAT_SAFE_DISTANCE_SQUARED) {
			this.phase = Phase.DONE;
			return;
		}
		long now = this.zombie.level().getGameTime();
		boolean reached = this.retreatDestination == null
			|| this.zombie.position().distanceToSqr(this.retreatDestination) <= 2.25;
		if (now < this.nextPathRefreshAt && !reached && !this.zombie.getNavigation().isDone()) {
			return;
		}
		Vec3 destination = LandRandomPos.getPosAway(this.zombie, 8.0, 11.0, 4, charge);
		if (destination == null) {
			Vec3 away = horizontalUnit(this.zombie.position().subtract(charge));
			destination = this.zombie.position().add(away.scale(8.0));
		}
		boolean moving = this.zombie.getNavigation().moveTo(
			destination.x,
			destination.y,
			destination.z,
			TNT_RETREAT_SPEED
		);
		this.retreatDestination = moving ? destination : null;
		this.nextPathRefreshAt = now + (moving ? 8L : 2L);
		if (moving) {
			this.zombie.getLookControl().setLookAt(destination.add(0.0, 1.0, 0.0));
		}
	}

	private void tickMovingToRepair() {
		RepairPlan plan = this.repairPlan;
		if (plan == null || !isRepairable(plan.recipient().getItemBySlot(plan.slot()))
			|| !this.isEngineerAlly(plan.recipient())) {
			this.phase = Phase.DONE;
			return;
		}
		this.zombie.getLookControl().setLookAt(plan.recipient(), 30.0F, 30.0F);
		if (this.zombie.distanceToSqr(plan.recipient()) <= REPAIR_REACH_SQUARED) {
			this.zombie.getNavigation().stop();
			this.beginRepair(this.zombie.level().getGameTime());
			return;
		}
		long now = this.zombie.level().getGameTime();
		if (now >= this.nextPathRefreshAt) {
			this.beginRepairNavigation();
			this.nextPathRefreshAt = now + PATH_REFRESH_TICKS;
		}
	}

	private void beginRepair(final long now) {
		this.phase = Phase.REPAIRING;
		this.phaseDeadline = now + REPAIR_WINDUP_TICKS;
	}

	private void tickRepairing() {
		RepairPlan plan = this.repairPlan;
		if (plan == null || !(this.zombie.level() instanceof ServerLevel level)) {
			this.phase = Phase.DONE;
			return;
		}
		this.zombie.getNavigation().stop();
		this.zombie.getLookControl().setLookAt(plan.recipient(), 30.0F, 30.0F);
		if (this.zombie.distanceToSqr(plan.recipient()) > REPAIR_REACH_SQUARED
			|| !this.isEngineerAlly(plan.recipient())) {
			this.phase = Phase.DONE;
			return;
		}
		if (level.getGameTime() < this.phaseDeadline) {
			return;
		}

		ItemStack equipment = plan.recipient().getItemBySlot(plan.slot());
		if (!isRepairable(equipment)) {
			this.phase = Phase.DONE;
			return;
		}
		int repairAmount = repairAmount(equipment.getMaxDamage());
		equipment.setDamageValue(Math.max(0, equipment.getDamageValue() - repairAmount));
		this.zombie.swing(this.visibleHand);
		level.playSound(
			null,
			plan.recipient(),
			SoundEvents.ANVIL_USE,
			SoundSource.HOSTILE,
			0.65F,
			1.25F
		);
		level.sendParticles(
			ParticleTypes.ELECTRIC_SPARK,
			plan.recipient().getX(),
			plan.recipient().getY() + plan.recipient().getBbHeight() * 0.6,
			plan.recipient().getZ(),
			8,
			0.35,
			0.45,
			0.35,
			0.04
		);
		SmartZombieMetrics.engineerRepair();
		this.phase = Phase.DONE;
	}

	private void tickFortifying() {
		if (!(this.zombie.level() instanceof ServerLevel level)) {
			this.phase = Phase.DONE;
			return;
		}
		this.zombie.getNavigation().stop();
		LivingEntity target = this.combatTarget;
		if (target != null) {
			this.zombie.getLookControl().setLookAt(target, 30.0F, 30.0F);
		}
		if (level.getGameTime() < this.phaseDeadline) {
			return;
		}

		List<Zombie> recipients = this.nearbyEngineerAllies(level, FORTIFY_RADIUS);
		for (Zombie recipient : recipients) {
			recipient.addEffect(
				new MobEffectInstance(MobEffects.RESISTANCE, FORTIFY_DURATION_TICKS, 0, false, true, true),
				this.zombie
			);
			level.sendParticles(
				ParticleTypes.HAPPY_VILLAGER,
				recipient.getX(),
				recipient.getY() + recipient.getBbHeight() * 0.65,
				recipient.getZ(),
				5,
				0.30,
				0.35,
				0.30,
				0.02
			);
		}
		this.zombie.swing(this.visibleHand);
		level.playSound(
			null,
			this.zombie,
			SoundEvents.BEACON_ACTIVATE,
			SoundSource.HOSTILE,
			0.65F,
			1.35F
		);
		SmartZombieMetrics.engineerFortification();
		this.phase = Phase.DONE;
	}

	private @Nullable TntPlan findTntPlan(final ServerLevel level, final LivingEntity target) {
		BlockPos center = target.blockPosition();
		int start = this.zombie.getRandom().nextInt(TNT_OFFSETS.length);
		for (int index = 0; index < TNT_OFFSETS.length; index++) {
			int[] offset = TNT_OFFSETS[(start + index) % TNT_OFFSETS.length];
			BlockPos candidate = center.offset(offset[0], 0, offset[1]);
			if (!this.isValidTntSite(level, candidate)) {
				continue;
			}
			if (this.isWithinTntReach(candidate)) {
				return new TntPlan(candidate.immutable(), null);
			}
			Path path = this.zombie.getNavigation().createPath(candidate, 1);
			if (path != null && path.canReach()) {
				return new TntPlan(candidate.immutable(), path);
			}
		}
		return null;
	}

	private boolean isValidTntSite(final ServerLevel level, final BlockPos pos) {
		if (!Level.isInSpawnableBounds(pos)
			|| !isChunkLoaded(level, pos)
			|| !level.getWorldBorder().isWithinBounds(new AABB(pos))) {
			return false;
		}
		BlockState current = level.getBlockState(pos);
		BlockPos support = pos.below();
		if (!current.canBeReplaced()
			|| !current.getFluidState().isEmpty()
			|| level.getBlockEntity(pos) != null
			|| !level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)) {
			return false;
		}
		if (!level.getEntitiesOfClass(
			LivingEntity.class,
			new AABB(pos),
			LivingEntity::isAlive
		).isEmpty()) {
			return false;
		}
		// 放置时没有友军处于爆心附近才允许使用；自身随后执行独立撤离阶段。
		return level.getEntitiesOfClass(
			Zombie.class,
			new AABB(pos).inflate(TNT_ALLY_SAFETY_RADIUS, 1.5, TNT_ALLY_SAFETY_RADIUS),
			candidate -> candidate != this.zombie && candidate.isAlive()
		).isEmpty();
	}

	private boolean placeTnt(final ServerLevel level, final BlockPos pos) {
		if (!this.isValidTntSite(level, pos)) {
			return false;
		}
		BlockState tnt = Blocks.TNT.defaultBlockState();
		if (!level.setBlock(pos, tnt, Block.UPDATE_ALL)) {
			return false;
		}
		this.placedTnt = true;
		SoundType sound = tnt.getSoundType();
		level.playSound(
			null,
			pos,
			sound.getPlaceSound(),
			SoundSource.BLOCKS,
			(sound.getVolume() + 1.0F) / 2.0F,
			sound.getPitch() * 0.8F
		);
		level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(this.zombie, tnt));
		this.zombie.swing(this.visibleHand);
		return true;
	}

	private boolean primeTnt(final ServerLevel level, final BlockPos pos) {
		PrimedTnt primed = new PrimedTnt(
			level,
			pos.getX() + 0.5,
			pos.getY(),
			pos.getZ() + 0.5,
			this.zombie
		);
		primed.setFuse(TNT_FUSE_TICKS);
		if (!level.addFreshEntity(primed)) {
			return false;
		}
		if (!level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)) {
			primed.discard();
			return false;
		}
		this.placedTnt = false;
		this.primedTnt = true;
		level.playSound(null, primed, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
		level.gameEvent(this.zombie, GameEvent.PRIME_FUSE, pos);
		return true;
	}

	private void cleanupUnprimedTnt() {
		if (!this.placedTnt || this.primedTnt || this.tntPlan == null
			|| !(this.zombie.level() instanceof ServerLevel level)) {
			return;
		}
		BlockPos pos = this.tntPlan.position();
		if (level.getBlockState(pos).is(Blocks.TNT)) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		this.placedTnt = false;
	}

	private @Nullable RepairPlan findRepairPlan(final ServerLevel level) {
		List<Zombie> candidates = this.nearbyEngineerAllies(level, REPAIR_SEARCH_RADIUS);
		List<RepairCandidate> damaged = new ArrayList<>();
		for (Zombie candidate : candidates) {
			EquipmentSlot bestSlot = null;
			double bestDamageFraction = 0.0;
			for (EquipmentSlot slot : REPAIRABLE_SLOTS) {
				ItemStack stack = candidate.getItemBySlot(slot);
				if (!isRepairable(stack)) {
					continue;
				}
				double fraction = (double)stack.getDamageValue() / stack.getMaxDamage();
				if (fraction > bestDamageFraction) {
					bestSlot = slot;
					bestDamageFraction = fraction;
				}
			}
			if (bestSlot != null) {
				damaged.add(new RepairCandidate(
					candidate,
					bestSlot,
					bestDamageFraction,
					this.zombie.distanceToSqr(candidate)
				));
			}
		}
		damaged.sort(
			Comparator.comparingDouble(RepairCandidate::damageFraction).reversed()
				.thenComparingDouble(RepairCandidate::distanceSquared)
				.thenComparingInt(candidate -> candidate.recipient().getId())
		);

		int pathChecks = 0;
		for (RepairCandidate candidate : damaged) {
			Path path = null;
			if (candidate.recipient() != this.zombie && candidate.distanceSquared() > REPAIR_REACH_SQUARED) {
				if (pathChecks >= MAXIMUM_REPAIR_PATH_CHECKS) {
					break;
				}
				pathChecks++;
				path = this.zombie.getNavigation().createPath(candidate.recipient(), 1);
				if (path == null || !path.canReach()) {
					continue;
				}
			}
			return new RepairPlan(candidate.recipient(), candidate.slot(), path);
		}
		return null;
	}

	private List<Zombie> nearbyEngineerAllies(final ServerLevel level, final double radius) {
		List<Zombie> allies = level.getEntitiesOfClass(
			Zombie.class,
			this.zombie.getBoundingBox().inflate(radius, radius * 0.5, radius),
			candidate -> candidate.isAlive() && this.isEngineerAlly(candidate)
		);
		if (!allies.contains(this.zombie)) {
			allies.add(this.zombie);
		}
		allies.sort(
			Comparator.comparingDouble((Zombie candidate) -> this.zombie.distanceToSqr(candidate))
				.thenComparingInt(Zombie::getId)
		);
		if (allies.size() > MAXIMUM_ALLY_CANDIDATES) {
			return new ArrayList<>(allies.subList(0, MAXIMUM_ALLY_CANDIDATES));
		}
		return allies;
	}

	private boolean isEngineerAlly(final Zombie candidate) {
		if (candidate == this.zombie) {
			return true;
		}
		if (ZombieSquadCoordinator.areSquadmates(this.zombie, candidate)) {
			return true;
		}
		LivingEntity target = this.combatTarget;
		return target != null && candidate.getTarget() == target;
	}

	private void beginTntNavigation() {
		TntPlan plan = this.tntPlan;
		if (plan == null) {
			this.phase = Phase.DONE;
			return;
		}
		boolean moving = plan.initialPath() != null
			? this.zombie.getNavigation().moveTo(plan.initialPath(), 1.12)
			: this.zombie.getNavigation().moveTo(
				plan.position().getX() + 0.5,
				plan.position().getY(),
				plan.position().getZ() + 0.5,
				1.12
			);
		if (!moving && this.zombie.getNavigation().isDone()) {
			this.phase = Phase.DONE;
		}
	}

	private void beginRepairNavigation() {
		RepairPlan plan = this.repairPlan;
		if (plan == null) {
			this.phase = Phase.DONE;
			return;
		}
		boolean moving = plan.initialPath() != null
			? this.zombie.getNavigation().moveTo(plan.initialPath(), 1.10)
			: this.zombie.getNavigation().moveTo(plan.recipient(), 1.10);
		if (!moving && this.zombie.getNavigation().isDone()) {
			this.phase = Phase.DONE;
		}
	}

	private void lookAt(final BlockPos pos) {
		this.zombie.getLookControl().setLookAt(Vec3.atCenterOf(pos));
	}

	private boolean isWithinTntReach(final BlockPos pos) {
		return this.zombie.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= TNT_INTERACTION_DISTANCE_SQUARED;
	}

	private boolean isTntPhase() {
		return this.phase == Phase.MOVING_TO_TNT_SITE
			|| this.phase == Phase.PLACING_TNT
			|| this.phase == Phase.ARMING_TNT
			|| this.phase == Phase.TNT_RETREAT;
	}

	private void clearPreparedSkill() {
		this.selectedSkill = null;
		this.combatTarget = null;
		this.tntPlan = null;
		this.repairPlan = null;
	}

	static int skillDelayTicks(final double roll) {
		double bounded = Double.isFinite(roll) ? Math.max(0.0, Math.min(1.0, roll)) : 0.0;
		int offset = Math.min(SKILL_DELAY_RANGE, (int)Math.floor(bounded * (SKILL_DELAY_RANGE + 1)));
		return MINIMUM_SKILL_DELAY_TICKS + offset;
	}

	static int repairAmount(final int maximumDurability) {
		return Math.max(1, maximumDurability / 4);
	}

	static boolean isVisualTool(final ItemStack stack) {
		return stack.is(Items.TNT) || stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.IRON_INGOT);
	}

	private static boolean isEnabled(final Zombie zombie, final MobsThinkNowConfig config) {
		return config.enabled
			&& config.zombieAiEnabled
			&& config.engineerSkills
			&& zombie.getType() == EntityType.ZOMBIE
			&& zombie.isAlive()
			&& !zombie.isBaby()
			&& ZombieEngineerProfile.isEngineer(zombie)
			&& ZombieSpecialEquipment.utilityClassOf(zombie) == UtilityClass.NONE
			&& !ZombieAirAssault.isAirAssaultLoadout(zombie);
	}

	private static boolean isEligibleTarget(final @Nullable LivingEntity target) {
		return target != null
			&& target.isAlive()
			&& !(target instanceof Zombie)
			&& (!(target instanceof Player player) || (!player.isCreative() && !player.isSpectator()));
	}

	private static boolean isRepairable(final ItemStack stack) {
		return !stack.isEmpty() && stack.isDamageableItem() && stack.getDamageValue() > 0;
	}

	private static boolean isChunkLoaded(final ServerLevel level, final BlockPos pos) {
		return level.getChunkSource().hasChunk(
			SectionPos.blockToSectionCoord(pos.getX()),
			SectionPos.blockToSectionCoord(pos.getZ())
		);
	}

	private static Vec3 horizontalUnit(final Vec3 vector) {
		Vec3 horizontal = new Vec3(vector.x, 0.0, vector.z);
		return horizontal.horizontalDistanceSqr() < 1.0E-6
			? new Vec3(0.0, 0.0, 1.0)
			: horizontal.normalize();
	}

	enum Skill {
		DEMOLITION_CHARGE,
		FIELD_REPAIR,
		FORTIFY_SQUAD
	}

	private enum Phase {
		IDLE,
		MOVING_TO_TNT_SITE,
		PLACING_TNT,
		ARMING_TNT,
		TNT_RETREAT,
		MOVING_TO_REPAIR,
		REPAIRING,
		FORTIFYING,
		DONE
	}

	@FunctionalInterface
	interface SkillDecision {
		@Nullable Skill choose(Zombie zombie, List<Skill> available);
	}

	private record TntPlan(BlockPos position, @Nullable Path initialPath) {
	}

	private record RepairPlan(Zombie recipient, EquipmentSlot slot, @Nullable Path initialPath) {
	}

	private record RepairCandidate(
		Zombie recipient,
		EquipmentSlot slot,
		double damageFraction,
		double distanceSquared
	) {
	}
}
