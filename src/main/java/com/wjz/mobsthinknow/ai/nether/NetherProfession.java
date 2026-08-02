package com.wjz.mobsthinknow.ai.nether;

import org.jspecify.annotations.Nullable;

/**
 * 下界智能单位的持久职业身份。
 *
 * <p>职业编号会通过 {@code SynchedEntityData} 发给客户端，因此皮肤不会因临时换手、寻路或
 * 攻击阶段变化而闪烁。编号只允许追加，已有编号不能重排，以保证旧存档仍能正确读取。</p>
 */
public enum NetherProfession {
	NONE(0, NetherProfessionFamily.NONE, null),

	PIGLIN_MARKSMAN(1, NetherProfessionFamily.PIGLIN, "marksman"),
	PIGLIN_VANGUARD(2, NetherProfessionFamily.PIGLIN, "vanguard"),
	PIGLIN_COMMANDER(3, NetherProfessionFamily.PIGLIN, "commander"),

	BLAZE_SKIRMISHER(4, NetherProfessionFamily.BLAZE, "skirmisher"),
	BLAZE_VOLLEYMASTER(5, NetherProfessionFamily.BLAZE, "volleymaster"),
	BLAZE_CINDER_GUARD(6, NetherProfessionFamily.BLAZE, "cinder_guard"),

	GHAST_ARTILLERY(7, NetherProfessionFamily.GHAST, "artillery"),
	GHAST_SPOTTER(8, NetherProfessionFamily.GHAST, "spotter"),
	GHAST_SIEGEBREAKER(9, NetherProfessionFamily.GHAST, "siegebreaker"),

	HOGLIN_CHARGER(10, NetherProfessionFamily.HOGLIN, "charger"),
	HOGLIN_BULWARK(11, NetherProfessionFamily.HOGLIN, "bulwark"),
	HOGLIN_RAVAGER(12, NetherProfessionFamily.HOGLIN, "ravager"),

	MAGMA_HUNTER(13, NetherProfessionFamily.MAGMA_CUBE, "hunter"),
	MAGMA_AMBUSHER(14, NetherProfessionFamily.MAGMA_CUBE, "ambusher"),
	MAGMA_TITAN(15, NetherProfessionFamily.MAGMA_CUBE, "titan");

	private static final NetherProfession[] VALUES = values();
	private final byte id;
	private final NetherProfessionFamily family;
	private final @Nullable String textureName;

	NetherProfession(
		final int id,
		final NetherProfessionFamily family,
		final @Nullable String textureName
	) {
		this.id = (byte)id;
		this.family = family;
		this.textureName = textureName;
	}

	public byte id() {
		return this.id;
	}

	public NetherProfessionFamily family() {
		return this.family;
	}

	public @Nullable String textureName() {
		return this.textureName;
	}

	public boolean belongsTo(final NetherProfessionFamily expectedFamily) {
		return this.family == expectedFamily;
	}

	public static NetherProfession fromId(final int id) {
		return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
	}
}
