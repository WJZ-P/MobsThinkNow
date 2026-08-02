package com.wjz.mobsthinknow.ai.zombie.squad;

import com.wjz.mobsthinknow.ai.zombie.ZombieBodyAction;
import com.wjz.mobsthinknow.ai.zombie.ZombieBodyLanguage;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
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
		final @Nullable Mob leader,
		final List<RoleMember> members,
		final MobsThinkNowConfig config,
		final long now
	) {
		for (RoleMember member : members) {
			if (config.squadRoleNameTags) {
				this.applyRoleTag(member.mob(), member.role());
			} else {
				this.restoreName(member.mob());
			}
		}

		long phase = Math.max(0L, now - stateStartedAt);
		if (config.zombieBodyLanguage) {
			emitBodyLanguage(state, leader, members, phase);
		}
		if (!config.squadVisualEffects) {
			return;
		}

		if (leader != null) {
			emitLeaderAura(level, leader, now);
		}

		switch (state) {
			case FORMING, RALLYING -> emitMarchGrunts(level, members, phase);
			case BRIEFING, REORGANIZING -> emitBriefingConversation(level, squadId, leader, members, phase);
			case DEPLOYING -> emitRoleTrails(level, members, phase);
			case ENGAGING -> emitWarCryRipple(level, leader, members, phase);
		}
	}

	/** 成员正式离队时恢复原始名字与可见性；玩家中途用命名牌起的新名字优先保留。 */
	void restoreName(final Mob mob) {
		StoredName stored = this.storedNames.remove(mob.getId());
		if (stored == null) {
			return;
		}
		if (Objects.equals(mob.getCustomName(), stored.applied())) {
			mob.setCustomName(stored.name());
			mob.setCustomNameVisible(stored.visible());
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

	private void applyRoleTag(final Mob mob, final SquadRole role) {
		StoredName stored = this.storedNames.get(mob.getId());
		Component current = mob.getCustomName();
		if (stored != null && !Objects.equals(current, stored.applied())) {
			// 佩戴名牌期间被外部改名（玩家命名牌等）：以新名字为原名重新记录，别吞掉玩家的命名。
			stored = null;
		}
		if (stored != null && stored.appliedRole() == role) {
			return;
		}

		Component original = stored != null ? stored.name() : current;
		boolean originalVisible = stored != null ? stored.visible() : mob.isCustomNameVisible();
		Component base = original != null ? original : mob.getType().getDescription();
		Component tagged = Component.empty().append(base).append(Component.literal(" ")).append(roleTag(role));
		this.storedNames.put(mob.getId(), new StoredName(original, originalVisible, role, tagged));
		mob.setCustomName(tagged);
		mob.setCustomNameVisible(true);
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
			case RANGED -> "[Ranged]";
			case BREACHER -> "[Breacher]";
			case CARRIER -> "[Carrier]";
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
			case RANGED -> ChatFormatting.WHITE;
			case BREACHER -> ChatFormatting.DARK_GREEN;
			case CARRIER -> ChatFormatting.DARK_PURPLE;
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
			case RANGED -> new DustParticleOptions(0xE6E6E6, 0.9F);
			case BREACHER -> new DustParticleOptions(0x3D9B45, 0.9F);
			case CARRIER -> new DustParticleOptions(0x8D55C7, 0.9F);
		};
	}

	/** 首领常驻的金色头顶光环，让玩家一眼认出该优先处理谁。 */
	private static void emitLeaderAura(final ServerLevel level, final Mob leader, final long now) {
		if (now % 3L != 0L) {
			return;
		}
		level.sendParticles(
			LEADER_AURA_DUST,
			leader.getX(), leader.getEyeY() + 0.45, leader.getZ(),
			2, 0.2, 0.1, 0.2, 0.0
		);
	}

	/**
	 * 身体语言与声音/粒子开关解耦：服务器只在节拍命中时发布一次动作转换，客户端可再用本机配置隐藏。
	 */
	private static void emitBodyLanguage(
		final SquadState state,
		final @Nullable Mob leader,
		final List<RoleMember> members,
		final long phase
	) {
		if (state == SquadState.BRIEFING || state == SquadState.REORGANIZING) {
			if (leader != null && phase % BRIEFING_SENTENCE_TICKS == 0L) {
				playBodyAction(leader, ZombieBodyAction.COMMAND);
				return;
			}
			if (phase % BRIEFING_SENTENCE_TICKS == BRIEFING_SENTENCE_TICKS / 2) {
				List<RoleMember> followers = followersOf(members, leader);
				if (!followers.isEmpty()) {
					Mob speaker = followers.get((int)((phase / BRIEFING_SENTENCE_TICKS) % followers.size())).mob();
					playBodyAction(speaker, ZombieBodyAction.ACKNOWLEDGE);
				}
			}
			return;
		}
		if (state != SquadState.ENGAGING) {
			return;
		}
		if (phase == 0L && leader != null) {
			playBodyAction(leader, ZombieBodyAction.WAR_CRY);
			return;
		}
		if (phase < 2L || phase % 2L != 0L) {
			return;
		}
		List<RoleMember> followers = followersOf(members, leader);
		int index = (int)((phase - 2L) / 2L);
		if (index < Math.min(followers.size(), WAR_CRY_MAX_VOICES)) {
			playBodyAction(followers.get(index).mob(), ZombieBodyAction.WAR_CRY);
		}
	}

	/** 集结路上偶尔的低吼，暗示它们不是各自游荡而是在赶去汇合。 */
	private static void emitMarchGrunts(final ServerLevel level, final List<RoleMember> members, final long phase) {
		if (members.isEmpty() || phase % MARCH_GRUNT_TICKS != 0L) {
			return;
		}
		Mob speaker = members.get((int)((phase / MARCH_GRUNT_TICKS) % members.size())).mob();
		playVoice(level, speaker, 0.5F, 0.98F);
	}

	/**
	 * 会议对话：首领每一句低沉长吼配头顶怒气云（像在训话布置任务），
	 * 句间由成员轮流短促应声并冒出音符，形成一来一回的交流感。
	 */
	private static void emitBriefingConversation(
		final ServerLevel level,
		final long squadId,
		final @Nullable Mob leader,
		final List<RoleMember> members,
		final long phase
	) {
		if (leader != null && phase % BRIEFING_SENTENCE_TICKS == 0L) {
			// 不同小队的首领音高略有差异，多队同屏时不会像一个声音在循环。
			playVoice(level, leader, 1.1F, 0.62F + (squadId % 3L) * 0.03F);
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
		Mob speaker = followers.get((int)((phase / BRIEFING_SENTENCE_TICKS) % followers.size())).mob();
		playVoice(level, speaker, 0.65F, 1.08F);
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
			Mob mob = member.mob();
			level.sendParticles(
				roleDust(member.role()),
				mob.getX(), mob.getY() + 1.2, mob.getZ(),
				1, 0.15, 0.2, 0.15, 0.0
			);
		}
	}

	/** 进入交战的最初几 tick：首领一声怒吼，成员声浪依次跟上。 */
	private static void emitWarCryRipple(
		final ServerLevel level,
		final @Nullable Mob leader,
		final List<RoleMember> members,
		final long phase
	) {
		if (phase == 0L && leader != null) {
			// 首领先用低沉长吼下达进攻命令，成员再按两 tick 间隔依次应声。
			playVoice(level, leader, 1.3F, 0.55F);
			return;
		}
		if (phase < 2L || phase % 2L != 0L) {
			return;
		}
		List<RoleMember> followers = followersOf(members, leader);
		int index = (int)((phase - 2L) / 2L);
		if (index >= Math.min(followers.size(), WAR_CRY_MAX_VOICES)) {
			return;
		}
		Mob voice = followers.get(index).mob();
		playVoice(level, voice, 0.9F, 0.86F);
	}

	private static List<RoleMember> followersOf(
		final List<RoleMember> members,
		final @Nullable Mob leader
	) {
		List<RoleMember> followers = new ArrayList<>(members.size());
		for (RoleMember member : members) {
			if (leader == null || member.mob() != leader) {
				followers.add(member);
			}
		}
		return followers;
	}

	private static void playBodyAction(final Mob mob, final ZombieBodyAction action) {
		if (mob instanceof Zombie zombie) {
			ZombieBodyLanguage.play(zombie, action);
		}
	}

	private static void playVoice(final ServerLevel level, final Mob mob, final float volume, final float expression) {
		if (mob instanceof Zombie zombie) {
			var sound = zombie.getType() == EntityType.HUSK
				? SoundEvents.HUSK_AMBIENT
				: zombie.getType() == EntityType.DROWNED
					? (zombie.isInWater() ? SoundEvents.DROWNED_AMBIENT_WATER : SoundEvents.DROWNED_AMBIENT)
					: zombie.getType() == EntityType.ZOMBIE_VILLAGER
						? SoundEvents.ZOMBIE_VILLAGER_AMBIENT
						: SoundEvents.ZOMBIE_AMBIENT;
			level.playSound(
				null,
				zombie,
				sound,
				SoundSource.HOSTILE,
				volume,
				ZombieVoiceProfile.expressivePitch(zombie, expression)
			);
			return;
		}
		float individual = 0.92F + Math.floorMod(mob.getUUID().hashCode(), 17) / 100.0F;
		if (mob instanceof Creeper) {
			level.playSound(null, mob, SoundEvents.CREEPER_HURT, SoundSource.HOSTILE, volume * 0.45F, expression * individual);
			return;
		}
		if (mob instanceof Spider) {
			level.playSound(null, mob, SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, volume, expression * individual);
			return;
		}
		if (mob instanceof AbstractSkeleton skeleton) {
			var sound = skeleton.getType() == EntityType.STRAY
				? SoundEvents.STRAY_AMBIENT
				: skeleton.getType() == EntityType.BOGGED
					? SoundEvents.BOGGED_AMBIENT
					: skeleton.getType() == EntityType.PARCHED
						? SoundEvents.PARCHED_AMBIENT
						: SoundEvents.SKELETON_AMBIENT;
			level.playSound(null, skeleton, sound, SoundSource.HOSTILE, volume, expression * individual);
			return;
		}
		// 其余无专用声线成员使用 UUID 哈希提供稳定的小幅差异，重进世界后也不会变声。
		level.playSound(
			null,
			mob,
			SoundEvents.SKELETON_AMBIENT,
			SoundSource.HOSTILE,
			volume,
			expression * individual
		);
	}

	/** 协调器传入的成员及其当前职位快照。 */
	record RoleMember(Mob mob, SquadRole role) {
	}

	private record StoredName(@Nullable Component name, boolean visible, SquadRole appliedRole, Component applied) {
	}
}
