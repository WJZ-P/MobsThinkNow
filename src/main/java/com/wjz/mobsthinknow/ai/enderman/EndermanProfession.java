package com.wjz.mobsthinknow.ai.enderman;

import org.jspecify.annotations.Nullable;

/**
 * 普通末影人的持久职业身份。
 *
 * <p>编号会通过 {@code SynchedEntityData} 同步给客户端并写入存档，因此只允许在末尾追加，
 * 不能重排现有编号。</p>
 */
public enum EndermanProfession {
	NONE(0, null),
	RIFTBLADE(1, "riftblade"),
	VOID_GUARD(2, "void_guard"),
	VOID_LANCER(3, "void_lancer"),
	CREEPER_HERALD(4, "creeper_herald");

	private static final EndermanProfession[] VALUES = values();
	private final byte id;
	private final @Nullable String textureName;

	EndermanProfession(final int id, final @Nullable String textureName) {
		this.id = (byte)id;
		this.textureName = textureName;
	}

	public byte id() {
		return this.id;
	}

	public @Nullable String textureName() {
		return this.textureName;
	}

	public static EndermanProfession fromId(final int id) {
		return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
	}
}
