package com.wjz.mobsthinknow.ai.nether;

import net.minecraft.util.Mth;

/**
 * 职业对既有战术状态机的纯参数修正。
 *
 * <p>这里不查询世界、不扫描实体；每次决策仍保持 O(1)，并且所有倍率都可独立做单元测试。</p>
 */
public final class NetherProfessionTactics {
	private NetherProfessionTactics() {
	}

	public static double piglinMoveSpeedMultiplier(final NetherProfession profession) {
		return switch (profession) {
			case PIGLIN_MARKSMAN -> 0.96;
			case PIGLIN_COMMANDER -> 1.04;
			case PIGLIN_VANGUARD -> 1.10;
			default -> 1.0;
		};
	}

	public static double blazeRangeMultiplier(final NetherProfession profession) {
		return switch (profession) {
			case BLAZE_VOLLEYMASTER -> 1.12;
			case BLAZE_CINDER_GUARD -> 0.72;
			default -> 1.0;
		};
	}

	public static int blazeVolleySize(final int baseVolley, final NetherProfession profession) {
		return switch (profession) {
			case BLAZE_VOLLEYMASTER -> Math.min(5, baseVolley + 1);
			case BLAZE_CINDER_GUARD -> Math.max(2, baseVolley - 1);
			default -> baseVolley;
		};
	}

	public static int blazeChargeTicks(final int baseTicks, final NetherProfession profession) {
		return switch (profession) {
			case BLAZE_VOLLEYMASTER -> baseTicks + 5;
			case BLAZE_CINDER_GUARD -> Math.max(16, baseTicks - 6);
			default -> baseTicks;
		};
	}

	public static double blazeUncertaintyMultiplier(final NetherProfession profession) {
		return switch (profession) {
			case BLAZE_VOLLEYMASTER -> 0.82;
			case BLAZE_CINDER_GUARD -> 1.15;
			default -> 1.0;
		};
	}

	public static double ghastPredictionMultiplier(final NetherProfession profession) {
		return switch (profession) {
			case GHAST_SPOTTER -> 1.25;
			case GHAST_SIEGEBREAKER -> 0.88;
			default -> 1.0;
		};
	}

	public static double ghastRelocationRadiusMultiplier(final NetherProfession profession) {
		return switch (profession) {
			case GHAST_SPOTTER -> 1.16;
			case GHAST_SIEGEBREAKER -> 0.82;
			default -> 1.0;
		};
	}

	public static int ghastExplosionPower(final int basePower, final NetherProfession profession) {
		return profession == NetherProfession.GHAST_SIEGEBREAKER
			? Mth.clamp(basePower + 1, 1, 3)
			: basePower;
	}

	public static int hoglinWindupTicks(final int baseTicks, final NetherProfession profession) {
		return switch (profession) {
			case HOGLIN_RAVAGER -> Math.max(7, baseTicks - 4);
			case HOGLIN_BULWARK -> baseTicks + 4;
			default -> baseTicks;
		};
	}

	public static double hoglinImpulseMultiplier(final NetherProfession profession) {
		return switch (profession) {
			case HOGLIN_RAVAGER -> 1.14;
			case HOGLIN_BULWARK -> 0.88;
			default -> 1.0;
		};
	}

	public static double magmaPounceMultiplier(final NetherProfession profession) {
		return switch (profession) {
			case MAGMA_AMBUSHER -> 1.14;
			case MAGMA_TITAN -> 0.90;
			default -> 1.0;
		};
	}

	/** 下界人形亡灵的寻路倍率；只是 Goal 的速度输入，不永久篡改实体属性。 */
	public static double undeadMoveSpeed(final NetherProfession profession) {
		return switch (profession) {
			case ZOMBIFIED_PIGLIN_LANCER -> 1.08;
			case ZOMBIFIED_PIGLIN_BERSERKER -> 1.18;
			case ZOMBIFIED_PIGLIN_WARCALLER -> 1.03;
			case WITHER_SKELETON_DUELIST -> 1.08;
			case WITHER_SKELETON_REAPER -> 1.16;
			case WITHER_SKELETON_HEXER -> 0.96;
			default -> 1.0;
		};
	}

	/** 命中后的真实攻击间隔；越激进的职业越短，但下界亡灵仍保留明确反击窗口。 */
	public static int undeadAttackIntervalTicks(final NetherProfession profession) {
		return switch (profession) {
			case ZOMBIFIED_PIGLIN_BERSERKER -> 16;
			case ZOMBIFIED_PIGLIN_WARCALLER -> 20;
			case WITHER_SKELETON_REAPER -> 18;
			case WITHER_SKELETON_DUELIST -> 22;
			default -> 20;
		};
	}

	/** 命中后保持面向目标并侧后撤的时间，决斗型职业会主动留出更大间隙。 */
	public static int undeadRecoveryTicks(final NetherProfession profession) {
		return switch (profession) {
			case ZOMBIFIED_PIGLIN_BERSERKER -> 5;
			case ZOMBIFIED_PIGLIN_WARCALLER -> 9;
			case WITHER_SKELETON_REAPER -> 6;
			case WITHER_SKELETON_DUELIST -> 11;
			default -> 7;
		};
	}

	public static boolean undeadUsesLunge(final NetherProfession profession) {
		return profession == NetherProfession.ZOMBIFIED_PIGLIN_BERSERKER
			|| profession == NetherProfession.WITHER_SKELETON_REAPER;
	}

	public static int undeadLungeWindupTicks(final NetherProfession profession) {
		return profession == NetherProfession.ZOMBIFIED_PIGLIN_BERSERKER ? 6 : 8;
	}

	public static double undeadLungeImpulse(final NetherProfession profession) {
		return profession == NetherProfession.ZOMBIFIED_PIGLIN_BERSERKER ? 0.52 : 0.46;
	}
}
