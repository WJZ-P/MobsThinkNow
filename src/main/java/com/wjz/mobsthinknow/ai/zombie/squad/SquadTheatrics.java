package com.wjz.mobsthinknow.ai.zombie.squad;

import com.wjz.mobsthinknow.ai.zombie.ZombieVoiceProfile;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.jspecify.annotations.Nullable;

/**
 * 小队的"剧场层"：职业名牌、首领光环、开会时的叫声与粒子对话。
 *
 * <p>只做表现，不影响任何战术决策；全部调用都在服务器主线程。所有节奏都由
 * {@code gameTime} 推导，因此不需要额外的每小队定时器状态。</p>
 */
public final class SquadTheatrics {
	private static final String ROLE_KEY_PREFIX = "mobsthinknow.role.";
	private static final int BRIEFING_SENTENCE_TICKS = 14;
	private static final int MARCH_GRUNT_TICKS = 22;
	private static final int WAR_CRY_MAX_VOICES = 20;
	private static final DustParticleOptions LEADER_AURA_DUST = new DustParticleOptions(0xFFC933, 1.0F);

	/** 记录被名牌覆盖前的原始名字，成员离队时恢复。 */
	private final Map<Integer, StoredName> storedNames = new HashMap<>();

	/** 协调器每 tick 对每支存活小队调用一次。 */
	void tickSquad(
		final ServerLevel level,
		final long squadId,
		final SquadState state,
		final long stateStartedAt,
		final @Nullable Zombie leader,
		final List<RoleMember> members,
		final MobsThinkNowConfig config,
		final long now
	) {
		for (RoleMember member : members) {
			if (config.squadRoleNameTags) {
				this.applyRoleTag(member.zombie(), member.role());
			} else {
				this.restoreName(member.zombie());
			}
		}

		if (!config.squadVisualEffects) {
			return;
		}

		if (leader != null) {
			emitLeaderAura(level, leader, now);
		}

		long phase = Math.max(0L, now - stateStartedAt);
			switch (state) {
			case FORMING, RALLYING -> emitMarchGrunts(level, members, phase);
			case BRIEFING, REORGANIZING -> emitBriefingConversation(level, squadId, leader, members, phase);
			case DEPLOYING -> emitRoleTrails(level, members, phase);
			case ENGAGING -> emitWarCryRipple(level, leader, members, phase);
		}
	}

	/** 成员正式离队时恢复原始名字与可见性；玩家中途用命名牌起的新名字优先保留。 */
	void restoreName(final Zombie zombie) {
		StoredName stored = this.storedNames.remove(zombie.getId());
		if (stored == null) {
			return;
		}
		if (Objects.equals(zombie.getCustomName(), stored.applied())) {
			zombie.setCustomName(stored.name());
			zombie.setCustomNameVisible(stored.visible());
		}
	}

	/**
	 * 服务器崩溃或强退可能把职业名牌写进存档。成员再次注册时剥掉残留，
	 * 避免"永久佩戴名牌"的僵尸出现（名牌还会阻止自然消失）。
	 */
	public static void stripLeftoverRoleTag(final Mob mob) {
		Component name = mob.getCustomName();
		if (name == null) {
			return;
		}

		// 名牌结构固定为 empty 根 + [原名, " ", 职业标签] 三个 sibling。
		List<Component> siblings = name.getSiblings();
		if (siblings.size() < 3) {
			return;
		}
		if (!(siblings.getLast().getContents() instanceof TranslatableContents tag)
			|| !tag.getKey().startsWith(ROLE_KEY_PREFIX)) {
			return;
		}

		Component base = siblings.getFirst();
		// 转化产物（溺尸等）的类型名与僵尸不同，这里按"任意实体类型名"判断是否应清空。
		boolean baseIsTypeName = base.getContents() instanceof TranslatableContents baseContents
			&& baseContents.getKey().startsWith("entity.");
		mob.setCustomName(baseIsTypeName ? null : base);
		mob.setCustomNameVisible(false);
	}

	private void applyRoleTag(final Zombie zombie, final SquadRole role) {
		StoredName stored = this.storedNames.get(zombie.getId());
		Component current = zombie.getCustomName();
		if (stored != null && !Objects.equals(current, stored.applied())) {
			// 佩戴名牌期间被外部改名（玩家命名牌等）：以新名字为原名重新记录，别吞掉玩家的命名。
			stored = null;
		}
		if (stored != null && stored.appliedRole() == role) {
			return;
		}

		Component original = stored != null ? stored.name() : current;
		boolean originalVisible = stored != null ? stored.visible() : zombie.isCustomNameVisible();
		Component base = original != null ? original : zombie.getType().getDescription();
		Component tagged = Component.empty().append(base).append(Component.literal(" ")).append(roleTag(role));
		this.storedNames.put(zombie.getId(), new StoredName(original, originalVisible, role, tagged));
		zombie.setCustomName(tagged);
		zombie.setCustomNameVisible(true);
	}

	/** 未安装本 Mod 的原版客户端无法解析语言键，因此带英文回退文本。 */
	private static MutableComponent roleTag(final SquadRole role) {
		String key = ROLE_KEY_PREFIX + role.name().toLowerCase(Locale.ROOT);
		return Component.translatableWithFallback(key, fallbackFor(role)).withStyle(colorFor(role));
	}

	private static String fallbackFor(final SquadRole role) {
		return switch (role) {
			case LEADER -> "[Leader]";
			case PRESSURER -> "[Pressurer]";
			case FLANK_LEFT -> "[Left Flank]";
			case FLANK_RIGHT -> "[Right Flank]";
			case CUTOFF -> "[Cutoff]";
			case SUPPORT -> "[Support]";
		};
	}

	private static ChatFormatting colorFor(final SquadRole role) {
		return switch (role) {
			case LEADER -> ChatFormatting.GOLD;
			case PRESSURER -> ChatFormatting.RED;
			case FLANK_LEFT -> ChatFormatting.GREEN;
			case FLANK_RIGHT -> ChatFormatting.AQUA;
			case CUTOFF -> ChatFormatting.LIGHT_PURPLE;
			case SUPPORT -> ChatFormatting.BLUE;
		};
	}

	private static DustParticleOptions roleDust(final SquadRole role) {
		return switch (role) {
			case LEADER -> LEADER_AURA_DUST;
			case PRESSURER -> new DustParticleOptions(0xE04B3A, 0.9F);
			case FLANK_LEFT -> new DustParticleOptions(0x4BD37B, 0.9F);
			case FLANK_RIGHT -> new DustParticleOptions(0x3FB8E0, 0.9F);
			case CUTOFF -> new DustParticleOptions(0xB05CE6, 0.9F);
			case SUPPORT -> new DustParticleOptions(0x3F72E0, 0.9F);
		};
	}

	/** 首领常驻的金色头顶光环，让玩家一眼认出该优先处理谁。 */
	private static void emitLeaderAura(final ServerLevel level, final Zombie leader, final long now) {
		if (now % 3L != 0L) {
			return;
		}
		level.sendParticles(
			LEADER_AURA_DUST,
			leader.getX(), leader.getEyeY() + 0.45, leader.getZ(),
			2, 0.2, 0.1, 0.2, 0.0
		);
	}

	/** 集结路上偶尔的低吼，暗示它们不是各自游荡而是在赶去汇合。 */
	private static void emitMarchGrunts(final ServerLevel level, final List<RoleMember> members, final long phase) {
		if (members.isEmpty() || phase % MARCH_GRUNT_TICKS != 0L) {
			return;
		}
		Zombie speaker = members.get((int)((phase / MARCH_GRUNT_TICKS) % members.size())).zombie();
		level.playSound(
			null,
			speaker,
			SoundEvents.ZOMBIE_AMBIENT,
			SoundSource.HOSTILE,
			0.5F,
			ZombieVoiceProfile.expressivePitch(speaker, 0.98F)
		);
	}

	/**
	 * 会议对话：首领每一句低沉长吼配头顶怒气云（像在训话布置任务），
	 * 句间由成员轮流短促应声并冒出音符，形成一来一回的交流感。
	 */
	private static void emitBriefingConversation(
		final ServerLevel level,
		final long squadId,
		final @Nullable Zombie leader,
		final List<RoleMember> members,
		final long phase
	) {
		if (leader != null && phase % BRIEFING_SENTENCE_TICKS == 0L) {
			// 不同小队的首领音高略有差异，多队同屏时不会像一个声音在循环。
			float pitch = ZombieVoiceProfile.expressivePitch(leader, 0.62F + (squadId % 3L) * 0.03F);
			level.playSound(null, leader, SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 1.1F, pitch);
			level.sendParticles(
				ParticleTypes.ANGRY_VILLAGER,
				leader.getX(), leader.getEyeY() + 0.6, leader.getZ(),
				2, 0.25, 0.1, 0.25, 0.0
			);
			return;
		}

		if (phase % BRIEFING_SENTENCE_TICKS != BRIEFING_SENTENCE_TICKS / 2) {
			return;
		}
		List<RoleMember> followers = new ArrayList<>(members.size());
		for (RoleMember member : members) {
			if (member.role() != SquadRole.LEADER) {
				followers.add(member);
			}
		}
		if (followers.isEmpty()) {
			return;
		}
		Zombie speaker = followers.get((int)((phase / BRIEFING_SENTENCE_TICKS) % followers.size())).zombie();
		level.playSound(
			null,
			speaker,
			SoundEvents.ZOMBIE_AMBIENT,
			SoundSource.HOSTILE,
			0.65F,
			ZombieVoiceProfile.expressivePitch(speaker, 1.08F)
		);
		level.sendParticles(
			ParticleTypes.NOTE,
			speaker.getX(), speaker.getEyeY() + 0.5, speaker.getZ(),
			1, 0.1, 0.1, 0.1, 0.0
		);
	}

	/** 部署散开时按职业颜色拖出粒子轨迹，玩家能直接读出"谁要去哪一侧"。 */
	private static void emitRoleTrails(final ServerLevel level, final List<RoleMember> members, final long phase) {
		if (phase % 6L != 0L) {
			return;
		}
		for (RoleMember member : members) {
			Zombie zombie = member.zombie();
			level.sendParticles(
				roleDust(member.role()),
				zombie.getX(), zombie.getY() + 1.2, zombie.getZ(),
				1, 0.15, 0.2, 0.15, 0.0
			);
		}
	}

	/** 进入交战的最初几 tick：首领一声怒吼，成员声浪依次跟上。 */
	private static void emitWarCryRipple(
		final ServerLevel level,
		final @Nullable Zombie leader,
		final List<RoleMember> members,
		final long phase
	) {
		if (phase == 0L && leader != null) {
			// 首领先用低沉长吼下达进攻命令，成员再按两 tick 间隔依次应声。
			level.playSound(
				null,
				leader,
				SoundEvents.ZOMBIE_AMBIENT,
				SoundSource.HOSTILE,
				1.3F,
				ZombieVoiceProfile.expressivePitch(leader, 0.55F)
			);
			return;
		}
		if (phase < 2L || phase % 2L != 0L) {
			return;
		}
		List<RoleMember> followers = new ArrayList<>(members.size());
		for (RoleMember member : members) {
			if (leader == null || member.zombie() != leader) {
				followers.add(member);
			}
		}
		int index = (int)((phase - 2L) / 2L);
		if (index >= Math.min(followers.size(), WAR_CRY_MAX_VOICES)) {
			return;
		}
		Zombie voice = followers.get(index).zombie();
		level.playSound(
			null,
			voice,
			SoundEvents.ZOMBIE_AMBIENT,
			SoundSource.HOSTILE,
			0.9F,
			ZombieVoiceProfile.expressivePitch(voice, 0.86F)
		);
	}

	/** 协调器传入的成员及其当前职位快照。 */
	record RoleMember(Zombie zombie, SquadRole role) {
	}

	private record StoredName(@Nullable Component name, boolean visible, SquadRole appliedRole, Component applied) {
	}
}
