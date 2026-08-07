package com.wjz.mobsthinknow.ai.zombie.squad;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 协调器交给单个混编怪物的只读命令快照。
 *
 * @param term 首领任期；首领更替时递增
 * @param planEpoch 同一任期内的计划版本；重新集结或重新部署时递增
 * @param combatEpoch 本次统一交战时间轴；重新部署或紧急接敌时递增
 * @param combatCycle 当前或下一次总攻轮次
 * @param combatBeat 所有成员共享的战斗节拍
 * @param combatExecuteAt 当前节拍等待的统一执行 tick
 * @param combatBeatEndsAt 当前节拍结束 tick
 * @param assaultPlan 本轮按首领智力与队伍构成冻结的总攻方案
 * @param shieldOrder 盾阵成员当前的举盾/出击职责；非盾阵成员为 NONE
 */
public record SquadDirective(
	long squadId,
	int term,
	int planEpoch,
	int combatEpoch,
	long combatCycle,
	SquadCombatBeat combatBeat,
	long combatExecuteAt,
	long combatBeatEndsAt,
	SquadState state,
	SquadAssaultPlan assaultPlan,
	ObservedTargetTactic observedTargetTactic,
	SquadRole role,
	@Nullable Vec3 destination,
	@Nullable Vec3 focusPosition,
	SquadShieldOrder shieldOrder,
	boolean hasSharedTargetMemory
) {
	public boolean isMeetingPhase() {
		return this.state == SquadState.FORMING
			|| this.state == SquadState.RALLYING
			|| this.state == SquadState.BRIEFING
			|| this.state == SquadState.REORGANIZING;
	}

	public boolean isCombatPhase() {
		return this.state == SquadState.DEPLOYING || this.state == SquadState.ENGAGING;
	}

	/** 重整和准备阶段继续执行阵位命令；贴身自卫仍可由紧急仲裁层抢占。 */
	public boolean holdsCombatFormation() {
		return this.isCombatPhase() && this.combatBeat.holdsFormation();
	}

	public boolean allowsMeleeAttack() {
		return this.state == SquadState.ENGAGING && this.combatBeat.allowsMeleeAttack();
	}

	public boolean allowsRangedAttack() {
		return this.combatBeat.allowsRangedAttack()
			&& (this.state == SquadState.DEPLOYING || this.state == SquadState.ENGAGING);
	}
}
