package com.wjz.mobsthinknow.mixin;

import com.wjz.mobsthinknow.ai.zombie.ReactiveRetreatGoal;
import com.wjz.mobsthinknow.ai.zombie.SmartZombieAttackGoal;
import com.wjz.mobsthinknow.ai.zombie.SmartZombieGapJumpGoal;
import com.wjz.mobsthinknow.ai.zombie.SmartZombieGroundNavigation;
import com.wjz.mobsthinknow.ai.zombie.SmartZombieMetrics;
import com.wjz.mobsthinknow.ai.zombie.SquadHurtByTargetGoal;
import com.wjz.mobsthinknow.ai.zombie.SmartZombieSpearUseGoal;
import com.wjz.mobsthinknow.ai.zombie.ZombieAirAssault;
import com.wjz.mobsthinknow.ai.zombie.ZombieAirAssaultStatusAccess;
import com.wjz.mobsthinknow.ai.zombie.ZombieArmory;
import com.wjz.mobsthinknow.ai.zombie.ZombieBodyAction;
import com.wjz.mobsthinknow.ai.zombie.ZombieBodyActionAccess;
import com.wjz.mobsthinknow.ai.zombie.ZombieBuilderInventory;
import com.wjz.mobsthinknow.ai.zombie.ZombieBuilderInventoryAccess;
import com.wjz.mobsthinknow.ai.zombie.ZombieEngineerAccess;
import com.wjz.mobsthinknow.ai.zombie.ZombieEngineerEquipment;
import com.wjz.mobsthinknow.ai.zombie.ZombieEngineerProfile;
import com.wjz.mobsthinknow.ai.zombie.ZombieEngineerSkillGoal;
import com.wjz.mobsthinknow.ai.zombie.ZombieFoodEquipment;
import com.wjz.mobsthinknow.ai.zombie.ZombieFoodSearchGoal;
import com.wjz.mobsthinknow.ai.zombie.ZombieFireSurvivalGoal;
import com.wjz.mobsthinknow.ai.zombie.ZombieFluidCarrierAccess;
import com.wjz.mobsthinknow.ai.zombie.ZombieFluidCarrierState;
import com.wjz.mobsthinknow.ai.zombie.ZombieFluidTacticsGoal;
import com.wjz.mobsthinknow.ai.zombie.ZombieFlightAccess;
import com.wjz.mobsthinknow.ai.zombie.ZombieIndividualTraits;
import com.wjz.mobsthinknow.ai.zombie.ZombieIntelligence;
import com.wjz.mobsthinknow.ai.zombie.ZombieIntelligenceAccess;
import com.wjz.mobsthinknow.ai.zombie.ZombieIntelligenceName;
import com.wjz.mobsthinknow.ai.zombie.ZombieProfession;
import com.wjz.mobsthinknow.ai.zombie.ZombieProfessionAccess;
import com.wjz.mobsthinknow.ai.zombie.ZombieProfessionProfile;
import com.wjz.mobsthinknow.ai.zombie.ZombieSpecialEquipment;
import com.wjz.mobsthinknow.ai.zombie.ZombieSpearAirAssaultGoal;
import com.wjz.mobsthinknow.ai.zombie.ZombieTerrainTacticsGoal;
import com.wjz.mobsthinknow.ai.zombie.ZombieVoiceAccess;
import com.wjz.mobsthinknow.ai.zombie.ZombieVoiceProfile;
import com.wjz.mobsthinknow.ai.zombie.ZombieWeaponPickupGoal;
import com.wjz.mobsthinknow.ai.zombie.squad.SquadTheatrics;
import com.wjz.mobsthinknow.ai.giant.GiantZombieProfile;
import com.wjz.mobsthinknow.ai.giant.GiantZombieSpawnAccess;
import com.wjz.mobsthinknow.ai.utility.OverworldUndeadFamilies;
import com.wjz.mobsthinknow.ai.nether.NetherProfession;
import com.wjz.mobsthinknow.ai.nether.NetherProfessionAccess;
import com.wjz.mobsthinknow.ai.nether.NetherProfessionProfile;
import com.wjz.mobsthinknow.ai.zombie.squad.SquadMemberHeartbeat;
import com.wjz.mobsthinknow.ai.zombie.squad.SquadCreeperEvadeGoal;
import com.wjz.mobsthinknow.ai.zombie.squad.SquadCasualtyResponseGoal;
import com.wjz.mobsthinknow.ai.zombie.squad.SquadFiringLaneClearGoal;
import com.wjz.mobsthinknow.ai.zombie.squad.SquadPreparationGoal;
import com.wjz.mobsthinknow.config.ConfigManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.SpearUseGoal;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Zombie.class)
public abstract class ZombieMixin extends Monster implements
	ZombieIntelligenceAccess,
	ZombieBuilderInventoryAccess,
	ZombieEngineerAccess,
	ZombieVoiceAccess,
	ZombieFluidCarrierAccess,
	ZombieFlightAccess,
	ZombieAirAssaultStatusAccess,
	ZombieProfessionAccess,
	ZombieBodyActionAccess,
	GiantZombieSpawnAccess,
	NetherProfessionAccess {
	@Unique
	private static final String mobsthinknow$INTELLIGENCE_TAG = "MobsThinkNowIntelligence";
	@Unique
	private static final EntityDataAccessor<Byte> mobsthinknow$PROFESSION_ID =
		SynchedEntityData.defineId(Zombie.class, EntityDataSerializers.BYTE);
	@Unique
	private static final EntityDataAccessor<Byte> mobsthinknow$BODY_ACTION_ID =
		SynchedEntityData.defineId(Zombie.class, EntityDataSerializers.BYTE);
	@Unique
	private static final EntityDataAccessor<Long> mobsthinknow$BODY_ACTION_STARTED_AT =
		SynchedEntityData.defineId(Zombie.class, EntityDataSerializers.LONG);
	@Unique
	private static final EntityDataAccessor<Byte> mobsthinknow$PREVIOUS_BODY_ACTION_ID =
		SynchedEntityData.defineId(Zombie.class, EntityDataSerializers.BYTE);
	@Unique
	private static final EntityDataAccessor<Integer> mobsthinknow$PREVIOUS_BODY_ACTION_ELAPSED =
		SynchedEntityData.defineId(Zombie.class, EntityDataSerializers.INT);
	@Unique
	private static final EntityDataAccessor<Long> mobsthinknow$BODY_ACTION_TRANSITION_STARTED_AT =
		SynchedEntityData.defineId(Zombie.class, EntityDataSerializers.LONG);
	@Unique
	private static final EntityDataAccessor<Byte> mobsthinknow$NETHER_PROFESSION_ID =
		SynchedEntityData.defineId(Zombie.class, EntityDataSerializers.BYTE);

	/** 0 只表示“尚未生成”，对外可见的合法智力值始终是 1～10。 */
	@Unique
	private int mobsthinknow$intelligence;
	/** 独立于双手装备的持久化建筑材料槽；普通生成始终为空。 */
	@Unique
	private ItemStack mobsthinknow$buildingBlocks = ItemStack.EMPTY;
	/** 工程兵是独立于智力门槛的稀有持久职业；只有它会运行周期技能调度器。 */
	@Unique
	private boolean mobsthinknow$engineer;
	/** 每只僵尸的固定声线中心，0 表示旧存档尚未生成。 */
	@Unique
	private float mobsthinknow$voiceFactor;
	/** 水/岩浆从满桶离手到回收完成的持久化事务。 */
	@Unique
	private ZombieFluidCarrierState mobsthinknow$fluidCarrierState = ZombieFluidCarrierState.NONE;
	/** 仅用于运行诊断和测试，不持久化，读档后由 Goal 在首个 tick 重建。 */
	@Unique
	private ZombieSpearAirAssaultGoal.Phase mobsthinknow$airAssaultPhase = ZombieSpearAirAssaultGoal.Phase.IDLE;
	@Unique
	private int mobsthinknow$rocketsLaunched;
	@Unique
	private int mobsthinknow$divesStarted;
	/** finalizeSpawn 判定、实体真正加入世界后只消费一次的巨人替换标记。 */
	@Unique
	private boolean mobsthinknow$giantReplacement;

	protected ZombieMixin(final EntityType<? extends Monster> type, final Level level) {
		super(type, level);
	}

	@Inject(method = "defineSynchedData", at = @At("TAIL"))
	private void mobsthinknow$defineProfessionData(
		final SynchedEntityData.Builder builder,
		final CallbackInfo callbackInfo
	) {
		builder.define(mobsthinknow$PROFESSION_ID, ZombieProfession.VANILLA.id());
		builder.define(mobsthinknow$BODY_ACTION_ID, ZombieBodyAction.NONE.id());
		builder.define(mobsthinknow$BODY_ACTION_STARTED_AT, 0L);
		builder.define(mobsthinknow$PREVIOUS_BODY_ACTION_ID, ZombieBodyAction.NONE.id());
		builder.define(mobsthinknow$PREVIOUS_BODY_ACTION_ELAPSED, 0);
		builder.define(mobsthinknow$BODY_ACTION_TRANSITION_STARTED_AT, 0L);
		// 只有僵尸猪灵会使用该槽；其余僵尸保持 NONE，但共享父类只增加一个同步字节。
		builder.define(mobsthinknow$NETHER_PROFESSION_ID, NetherProfession.NONE.id());
	}

	/** 只替换僵尸的节点分类器，导航、A* 与 MoveControl 仍沿用原版实现。 */
	@Override
	protected PathNavigation createNavigation(final Level level) {
		Zombie zombie = (Zombie)(Object)this;
		return OverworldUndeadFamilies.usesGroundZombieTactics(zombie.getType())
			? new SmartZombieGroundNavigation(zombie, level)
			: new GroundPathNavigation(zombie, level);
	}

	/** 所有人形僵尸共享友伤过滤；溺尸只额外接受集结和部署命令，不替换两栖战斗 Goal。 */
	@Inject(method = "registerGoals", at = @At("TAIL"))
	private void mobsthinknow$installFamilySquadGoals(final CallbackInfo callbackInfo) {
		Zombie zombie = (Zombie)(Object)this;
		if (!OverworldUndeadFamilies.isZombieFamily(zombie)) {
			return;
		}
		boolean alreadyInstalled = this.targetSelector.getAvailableGoals().stream()
			.anyMatch(wrapped -> wrapped.getGoal() instanceof SquadHurtByTargetGoal);
		if (!alreadyInstalled) {
			this.targetSelector.removeAllGoals(goal -> goal.getClass() == HurtByTargetGoal.class);
			this.targetSelector.addGoal(1, new SquadHurtByTargetGoal(zombie));
		}
		this.goalSelector.addGoal(0, new SquadCreeperEvadeGoal(zombie));
		this.goalSelector.addGoal(1, new SquadCasualtyResponseGoal(zombie, 1.38, 1.16));
		this.goalSelector.addGoal(2, new SquadFiringLaneClearGoal(zombie, 1.15));
		if (zombie.getType() == EntityType.DROWNED) {
			this.goalSelector.addGoal(0, new SquadPreparationGoal(zombie, 1.0));
		}
	}

	@Inject(method = "addBehaviourGoals", at = @At("TAIL"))
	private void mobsthinknow$replaceZombieAttackGoal(final CallbackInfo callbackInfo) {
		Zombie zombie = (Zombie)(Object)this;
		if (!OverworldUndeadFamilies.usesGroundZombieTactics(zombie.getType())) {
			return;
		}

		boolean hasVanillaAttackGoal = this.goalSelector
			.getAvailableGoals()
			.stream()
			.anyMatch(wrapped -> wrapped.getGoal().getClass() == ZombieAttackGoal.class);
		if (!hasVanillaAttackGoal) {
			return;
		}

		this.goalSelector.removeAllGoals(goal -> goal.getClass() == ZombieAttackGoal.class);
		this.goalSelector.removeAllGoals(goal -> goal.getClass() == SpearUseGoal.class);
		// 真实着火时生存高于战斗和空袭；仅日晒时一旦受击，下一拍就把控制权交回战斗系统。
		this.goalSelector.addGoal(0, new ZombieFireSurvivalGoal(zombie, true));
		// 空袭 Goal 是持矛套装有弹药时唯一的 MOVE/LOOK 决策者；弹尽后先落地，再让原版地面长矛逻辑接手。
		this.goalSelector.addGoal(0, new ZombieSpearAirAssaultGoal(zombie));
		// 独立的高优先级撤退 Goal 不依赖近战追击能否启动；MOVE/LOOK 冲突会自然暂停攻击与小队机动。
		this.goalSelector.addGoal(1, new ReactiveRetreatGoal(zombie));
		// 非着火的日晒生存保持 priority 1；它不会压住 priority 0 的持矛空袭。
		this.goalSelector.addGoal(1, new ZombieFireSurvivalGoal(zombie, false));
		// 对目标方向存在严格的一格宽安全落点时，以真实跳跃越沟；失败后仍回到普通寻路。
		this.goalSelector.addGoal(2, new SmartZombieGapJumpGoal(zombie));
		// 工程兵仅在 6～10 秒技能窗口到期且存在合法技能时接管；着火和受击撤退仍可立即打断。
		this.goalSelector.addGoal(2, new ZombieEngineerSkillGoal(zombie));
		// 地面武器是永久战力升级；工程技能未到期时，它优先于流体、觅食、采集与普通战斗。
		this.goalSelector.addGoal(2, new ZombieWeaponPickupGoal(zombie));
		// 特殊桶兵优先承担支援/骚扰；已放出的源方块即使热关配置也会先完成回收事务。
		this.goalSelector.addGoal(2, new ZombieFluidTacticsGoal(zombie));
		// 低血觅食位于撤退与攻击之间：有可达食物才接管移动，受击撤退仍可立即抢占。
		this.goalSelector.addGoal(2, new ZombieFoodSearchGoal(zombie));
		// 地形战术与觅食同级但后注册：低血且附近有食物时先保命，其余时间才采集和搭建立柱。
		this.goalSelector.addGoal(2, new ZombieTerrainTacticsGoal(zombie));
		this.goalSelector.addGoal(2, new SmartZombieSpearUseGoal(zombie, 1.0, 1.0, 10.0F, 2.0F));
		this.goalSelector.addGoal(3, new SmartZombieAttackGoal(zombie, 1.0, false));

		// 换成小队感知版的仇恨反击：队友误伤不转移仇恨，其余语义与原版一致。
		boolean hasVanillaHurtByGoal = this.targetSelector
			.getAvailableGoals()
			.stream()
			.anyMatch(wrapped -> wrapped.getGoal().getClass() == HurtByTargetGoal.class);
		if (hasVanillaHurtByGoal) {
			this.targetSelector.removeAllGoals(goal -> goal.getClass() == HurtByTargetGoal.class);
			this.targetSelector.addGoal(1, new SquadHurtByTargetGoal(zombie));
		}
		SmartZombieMetrics.goalInstalled();
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void mobsthinknow$saveIntelligence(final ValueOutput output, final CallbackInfo callbackInfo) {
		Zombie zombie = (Zombie)(Object)this;
		if (!OverworldUndeadFamilies.isZombieFamily(zombie)) {
			return;
		}
		// 智力是个体特征，必须持久化；小队归属和命令刻意不保存，避免重载世界后引用失效实体。
		output.putInt(mobsthinknow$INTELLIGENCE_TAG, this.mobsthinknow$getIntelligence());
		ZombieBuilderInventory.save((Zombie)(Object)this, output);
		ZombieEngineerProfile.save((Zombie)(Object)this, output);
		ZombieEngineerEquipment.saveTemporaryEquipment((Zombie)(Object)this, output);
		ZombieVoiceProfile.save((Zombie)(Object)this, output);
		ZombieSpecialEquipment.save((Zombie)(Object)this, output);
		ZombieProfessionProfile.save((Zombie)(Object)this, output);
		// 自动保存若恰好发生在进食换手的 1～2 秒内，额外保存真正的武器/盾牌供读档恢复。
		ZombieFoodEquipment.saveTemporaryEquipment((Zombie)(Object)this, output);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void mobsthinknow$loadIntelligence(final ValueInput input, final CallbackInfo callbackInfo) {
		Zombie candidate = (Zombie)(Object)this;
		if (!OverworldUndeadFamilies.isZombieFamily(candidate)) {
			return;
		}
		int saved = input.getIntOr(mobsthinknow$INTELLIGENCE_TAG, 0);
		this.mobsthinknow$intelligence = saved == 0 ? 0 : ZombieIntelligence.clamp(saved);
		// 实体基础数据（含 CustomName）先于本回调加载；崩溃残留的职业名牌在这里剥掉。
		Zombie zombie = (Zombie)(Object)this;
		SquadTheatrics.stripLeftoverRoleTag(zombie);
		ZombieFoodEquipment.restoreSavedEquipment(zombie, input);
		ZombieEngineerEquipment.restoreSavedEquipment(zombie, input);
		ZombieBuilderInventory.load(zombie, input);
		ZombieEngineerProfile.load(zombie, input);
		ZombieVoiceProfile.load(zombie, input);
		ZombieSpecialEquipment.load(zombie, input);
		ZombieProfessionProfile.load(zombie, input);
		if (OverworldUndeadFamilies.isZombieFamily(zombie)) {
			ZombieIntelligenceName.apply(zombie, this.mobsthinknow$getIntelligence());
		}
	}

	@Inject(method = "convertToZombieType", at = @At("HEAD"))
	private void mobsthinknow$stripRoleTagBeforeConversion(
		final ServerLevel level,
		final EntityType<? extends Zombie> zombieType,
		final CallbackInfo callbackInfo
	) {
		// 原版转化会把 CustomName 原样复制给新实体（僵尸→溺尸等）；转化前剥掉职业名牌，
		// 避免溺尸顶着本 Mod 的名牌继续存在。
		Zombie zombie = (Zombie)(Object)this;
		if (!OverworldUndeadFamilies.isZombieFamily(zombie)) {
			return;
		}
		// Conversion copies visible hand equipment into the replacement. Restore a real weapon first;
		// otherwise a zombie converting mid-bite can turn its temporary one-serving food into permanent gear.
		ZombieFoodEquipment.restore(zombie, true);
		ZombieEngineerEquipment.restore(zombie, false);
		SquadTheatrics.stripLeftoverRoleTag(zombie);
		ZombieIntelligenceName.removeSyntheticMarker(zombie);
	}

	@Inject(method = "finalizeSpawn", at = @At("TAIL"))
	private void mobsthinknow$equipSquadWeapon(
		final ServerLevelAccessor level,
		final DifficultyInstance difficulty,
		final EntitySpawnReason spawnReason,
		final SpawnGroupData groupData,
		final CallbackInfoReturnable<SpawnGroupData> callbackInfo
	) {
		Zombie zombie = (Zombie)(Object)this;
		if (zombie.getType() == EntityType.ZOMBIFIED_PIGLIN) {
			// 僵尸猪灵的金剑/金矛直到 Zombie.finalizeSpawn 内才生成，必须在这里做一次晚分配。
			NetherProfessionProfile.assignOnSpawn(zombie, difficulty, level.getRandom());
			return;
		}
		if (OverworldUndeadFamilies.isZombieFamily(zombie)) {
			ZombieIntelligenceName.apply(zombie, this.mobsthinknow$getIntelligence());
			// 提前固化声线；首次环境音与后续小队叫声都会使用同一中心音高。
			ZombieVoiceProfile.factor(zombie);
		}
		// 村民转化等 CONVERSION 路径保留原有装备语义，与原版 populateDefaultEquipmentSlots 的边界一致。
		if (zombie.getType() != EntityType.ZOMBIE || spawnReason == EntitySpawnReason.CONVERSION) {
			return;
		}
		var config = ConfigManager.get();
		ZombieIndividualTraits.applyOnSpawn(zombie, difficulty, this.random, config);
		ZombieSpecialEquipment.maybeEquip(zombie, difficulty, this.random, config);
		ZombieArmory.maybeEquipForSquad(zombie, difficulty, this.random, config);
		ZombieAirAssault.equipForSpawn(zombie, difficulty.getDifficulty(), this.random, config);
		// 最后掷工程兵身份；水/岩浆桶变体会并入工程兵，武装兵和空袭兵仍保持独立。
		ZombieEngineerProfile.maybeAssignOnSpawn(zombie, difficulty, this.random, config);
		// 装备和工程兵身份都已冻结，此时生成一次持久且同步到客户端的职业皮肤编号。
		ZombieProfessionProfile.assignFromLoadout(zombie);
		if (GiantZombieProfile.shouldReplace(
			difficulty.getDifficulty(),
			spawnReason,
			this.random.nextDouble(),
			config
		)) {
			this.mobsthinknow$giantReplacement = true;
		}
	}

	/** 独立心跳保证溺尸的原版两栖 Goal 未运行近战时，也能参与同一轮发现、选举与重选。 */
	@Override
	protected void customServerAiStep(final ServerLevel serverLevel) {
		super.customServerAiStep(serverLevel);
		Zombie zombie = (Zombie)(Object)this;
		if (zombie.getType() == EntityType.DROWNED) {
			SquadMemberHeartbeat.tick(serverLevel, zombie, ConfigManager.get().zombieAiEnabled);
		}
	}

	@Inject(method = "wantsToPickUp", at = @At("HEAD"), cancellable = true)
	private void mobsthinknow$leaveManagedLootForTacticalGoals(
		final ServerLevel level,
		final ItemStack itemStack,
		final CallbackInfoReturnable<Boolean> callbackInfo
	) {
		Zombie zombie = (Zombie)(Object)this;
		var config = ConfigManager.get();
		if (ZombieFoodSearchGoal.managesFood(zombie, itemStack, config)
			|| ZombieWeaponPickupGoal.managesWeapon(zombie, itemStack, config)) {
			callbackInfo.setReturnValue(false);
		}
	}

	@Override
	public int mobsthinknow$getIntelligence() {
		if (this.mobsthinknow$intelligence == 0) {
			this.mobsthinknow$intelligence = this.random.nextInt(ZombieIntelligence.MAXIMUM) + ZombieIntelligence.MINIMUM;
		}
		return this.mobsthinknow$intelligence;
	}

	@Override
	public void mobsthinknow$setIntelligence(final int intelligence) {
		this.mobsthinknow$intelligence = ZombieIntelligence.clamp(intelligence);
	}

	@Override
	public ItemStack mobsthinknow$getBuildingBlocks() {
		return this.mobsthinknow$buildingBlocks;
	}

	@Override
	public void mobsthinknow$setBuildingBlocks(final ItemStack stack) {
		this.mobsthinknow$buildingBlocks = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
	}

	@Override
	public boolean mobsthinknow$isEngineer() {
		return this.mobsthinknow$engineer;
	}

	@Override
	public void mobsthinknow$setEngineer(final boolean engineer) {
		this.mobsthinknow$engineer = engineer;
	}

	@Override
	public float mobsthinknow$getVoiceFactor() {
		return this.mobsthinknow$voiceFactor;
	}

	@Override
	public void mobsthinknow$setVoiceFactor(final float factor) {
		this.mobsthinknow$voiceFactor = factor;
	}

	@Override
	public ZombieFluidCarrierState mobsthinknow$getFluidCarrierState() {
		return this.mobsthinknow$fluidCarrierState;
	}

	@Override
	public void mobsthinknow$setFluidCarrierState(final ZombieFluidCarrierState state) {
		this.mobsthinknow$fluidCarrierState = state == null ? ZombieFluidCarrierState.NONE : state;
	}

	@Override
	public ZombieProfession mobsthinknow$getProfession() {
		return ZombieProfession.fromId(this.entityData.get(mobsthinknow$PROFESSION_ID));
	}

	@Override
	public void mobsthinknow$setProfession(final ZombieProfession profession) {
		this.entityData.set(
			mobsthinknow$PROFESSION_ID,
			(profession == null ? ZombieProfession.VANILLA : profession).id()
		);
	}

	@Override
	public NetherProfession mobsthinknow$getNetherProfession() {
		return NetherProfession.fromId(this.entityData.get(mobsthinknow$NETHER_PROFESSION_ID));
	}

	@Override
	public void mobsthinknow$setNetherProfession(final NetherProfession profession) {
		this.entityData.set(
			mobsthinknow$NETHER_PROFESSION_ID,
			(profession == null ? NetherProfession.NONE : profession).id()
		);
	}

	@Override
	public ZombieBodyAction mobsthinknow$getBodyAction() {
		return ZombieBodyAction.fromId(this.entityData.get(mobsthinknow$BODY_ACTION_ID));
	}

	@Override
	public long mobsthinknow$getBodyActionStartedAt() {
		return this.entityData.get(mobsthinknow$BODY_ACTION_STARTED_AT);
	}

	@Override
	public ZombieBodyAction mobsthinknow$getPreviousBodyAction() {
		return ZombieBodyAction.fromId(this.entityData.get(mobsthinknow$PREVIOUS_BODY_ACTION_ID));
	}

	@Override
	public int mobsthinknow$getPreviousBodyActionElapsedTicks() {
		return this.entityData.get(mobsthinknow$PREVIOUS_BODY_ACTION_ELAPSED);
	}

	@Override
	public long mobsthinknow$getBodyActionTransitionStartedAt() {
		return this.entityData.get(mobsthinknow$BODY_ACTION_TRANSITION_STARTED_AT);
	}

	@Override
	public void mobsthinknow$setBodyAction(final ZombieBodyAction action, final long startedAt) {
		ZombieBodyAction safeAction = action == null ? ZombieBodyAction.NONE : action;
		ZombieBodyAction current = this.mobsthinknow$getBodyAction();
		if (current != safeAction) {
			long currentStartedAt = this.mobsthinknow$getBodyActionStartedAt();
			long elapsed = Math.max(0L, Math.min(200L, startedAt - currentStartedAt));
			this.entityData.set(mobsthinknow$PREVIOUS_BODY_ACTION_ID, current.id());
			this.entityData.set(mobsthinknow$PREVIOUS_BODY_ACTION_ELAPSED, (int)elapsed);
			this.entityData.set(mobsthinknow$BODY_ACTION_TRANSITION_STARTED_AT, startedAt);
		}
		this.entityData.set(mobsthinknow$BODY_ACTION_ID, safeAction.id());
		this.entityData.set(mobsthinknow$BODY_ACTION_STARTED_AT, startedAt);
	}

	@Override
	public void mobsthinknow$startFallFlying() {
		// LivingEntity 已有完整滑翔物理，但公开 startFallFlying 只放在 Player；Mixin 在继承层内补同一位标志。
		this.setPose(Pose.FALL_FLYING);
		this.setSharedFlag(7, true);
	}

	@Override
	public void mobsthinknow$stopFallFlying() {
		// LivingEntity#stopFallFlying 会先把共享位写 true 再写 false；已落地时反复调用会制造一帧
		// 假滑翔状态。只在该位真实为 true 时走原版关闭链路，姿态则独立兜底归正。
		if (this.isFallFlying()) {
			this.stopFallFlying();
		}
		if (this.hasPose(Pose.FALL_FLYING)) {
			this.setPose(Pose.STANDING);
		}
	}

	@Override
	public ZombieSpearAirAssaultGoal.Phase mobsthinknow$getAirAssaultPhase() {
		return this.mobsthinknow$airAssaultPhase;
	}

	@Override
	public void mobsthinknow$setAirAssaultPhase(final ZombieSpearAirAssaultGoal.Phase phase) {
		this.mobsthinknow$airAssaultPhase = phase == null ? ZombieSpearAirAssaultGoal.Phase.IDLE : phase;
	}

	@Override
	public int mobsthinknow$getRocketsLaunched() {
		return this.mobsthinknow$rocketsLaunched;
	}

	@Override
	public void mobsthinknow$recordRocketLaunch() {
		this.mobsthinknow$rocketsLaunched++;
	}

	@Override
	public int mobsthinknow$getDivesStarted() {
		return this.mobsthinknow$divesStarted;
	}

	@Override
	public void mobsthinknow$recordDiveStart() {
		this.mobsthinknow$divesStarted++;
	}

	@Override
	public void mobsthinknow$markGiantReplacement() {
		this.mobsthinknow$giantReplacement = true;
	}

	@Override
	public boolean mobsthinknow$consumeGiantReplacement() {
		boolean marked = this.mobsthinknow$giantReplacement;
		this.mobsthinknow$giantReplacement = false;
		return marked;
	}

	/** 原版每次发声的小抖动保留，再乘固定个体声线，幼年僵尸的高音规则也照常叠加。 */
	@Override
	public float getVoicePitch() {
		Zombie zombie = (Zombie)(Object)this;
		return super.getVoicePitch() * (OverworldUndeadFamilies.isZombieFamily(zombie)
			? ZombieVoiceProfile.factor(zombie)
			: 1.0F);
	}
}
