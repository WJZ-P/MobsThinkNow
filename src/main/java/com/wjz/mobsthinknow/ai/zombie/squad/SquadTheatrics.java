package com.wjz.mobsthinknow.ai.zombie.squad;

import com.wjz.mobsthinknow.ai.zombie.SmartZombieMetrics;
import com.wjz.mobsthinknow.ai.zombie.ZombieArmory;
import com.wjz.mobsthinknow.ai.zombie.ZombieBodyAction;
import com.wjz.mobsthinknow.ai.zombie.ZombieBodyLanguage;
import com.wjz.mobsthinknow.ai.zombie.ZombieEngineerProfile;
import com.wjz.mobsthinknow.ai.zombie.ZombieIntelligence;
import com.wjz.mobsthinknow.ai.zombie.ZombieVoiceProfile;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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
	private static final int MARCH_GRUNT_TICKS = 22;
	private static final int WAR_CRY_MAX_VOICES = 20;
	private static final DustParticleOptions LEADER_AURA_DUST = new DustParticleOptions(0xFFC933, 1.0F);

	/** 记录被名牌覆盖前的原始名字，成员离队时恢复。 */
	private final Map<UUID, StoredName> storedNames = new HashMap<>();

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
		long phase = Math.max(0L, now - stateStartedAt);
		List<RoleMember> followers = followersOf(members, leader);
		SquadSocialChoreography.Scene scene = SquadSocialChoreography.sceneAt(
			state,
			squadId,
			phase,
			participantsOf(followers),
			new SquadSocialChoreography.Timing(config.briefingTicks, config.regroupTicks)
		);
		this.tickSquad(level, squadId, state, leader, members, config, now, phase, scene);
	}

	/** 协调器已经计算过场景时复用同一快照，动作、声音与注视不会重复分配列表。 */
	void tickSquad(
		final ServerLevel level,
		final long squadId,
		final SquadState state,
		final @Nullable Mob leader,
		final List<RoleMember> members,
		final MobsThinkNowConfig config,
		final long now,
		final long phase,
		final SquadSocialChoreography.Scene scene
	) {
		for (RoleMember member : members) {
			if (config.squadRoleNameTags) {
				this.applyRoleTag(member.mob(), member.role());
			} else {
				this.restoreName(member.mob());
			}
		}

		if (config.zombieBodyLanguage) {
			emitBodyLanguage(state, leader, members, phase, scene);
		}
		if (!config.squadVisualEffects) {
			return;
		}

		if (leader != null) {
			emitLeaderAura(level, leader, now);
		}
		emitSocialConversation(level, squadId, leader, members, scene);

		switch (state) {
			case FORMING, BRIEFING, REORGANIZING -> {
				// 社交音画已由上面的同一份编排节拍输出。
			}
			case RALLYING -> emitMarchGrunts(level, members, phase);
			case DEPLOYING -> emitRoleTrails(level, members, phase);
			case ENGAGING -> emitWarCryRipple(level, leader, members, phase);
		}
	}

	/** 成员正式离队时恢复原始名字与可见性；玩家中途用命名牌起的新名字优先保留。 */
	void restoreName(final Mob mob) {
		StoredName stored = this.storedNames.remove(mob.getUUID());
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
		StoredName stored = this.storedNames.get(mob.getUUID());
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
		this.storedNames.put(mob.getUUID(), new StoredName(original, originalVisible, role, tagged));
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
		final long phase,
		final SquadSocialChoreography.Scene scene
	) {
		List<RoleMember> zombieFollowers = zombieFollowersOf(members, leader);
		for (SquadSocialChoreography.Cue cue : scene.cues()) {
			Mob actor = resolveCueActor(cue, leader, members);
			if (actor != null && playBodyAction(actor, cue.action())) {
				if (cue.leader()) {
					SmartZombieMetrics.leaderSocialGesture();
				} else {
					SmartZombieMetrics.memberSocialGesture();
				}
			}
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
		List<RoleMember> followers = zombieFollowers;
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

	/** 用与身体动作完全相同的节拍补充声音和粒子，关闭身体语言后仍保留可读的会议对话。 */
	private static void emitSocialConversation(
		final ServerLevel level,
		final long squadId,
		final @Nullable Mob leader,
		final List<RoleMember> members,
		final SquadSocialChoreography.Scene scene
	) {
		for (SquadSocialChoreography.Cue cue : scene.cues()) {
			Mob actor = resolveCueActor(cue, leader, members);
			if (actor != null) {
				emitSocialCue(level, squadId, actor, cue.action());
			}
		}
	}

	private static void emitSocialCue(
		final ServerLevel level,
		final long squadId,
		final Mob actor,
		final ZombieBodyAction action
	) {
		switch (action) {
			case CALL_TO_MEETING -> {
				playVoice(level, actor, 1.15F, 0.66F + Math.floorMod(squadId, 3L) * 0.03F);
				emitCueParticles(level, actor, ParticleTypes.ANGRY_VILLAGER, 2, 0.24);
			}
			case SURVEY_MEMBERS -> emitCueParticles(level, actor, ParticleTypes.NOTE, 1, 0.05);
			case COMMAND, COMMAND_LEFT, COMMAND_RIGHT, ADVANCE_ORDER -> {
				playVoice(level, actor, 1.08F, 0.61F + Math.floorMod(squadId, 4L) * 0.025F);
				emitCueParticles(level, actor, ParticleTypes.ANGRY_VILLAGER, 2, 0.22);
			}
			case NOD -> emitCueParticles(level, actor, ParticleTypes.HAPPY_VILLAGER, 1, 0.12);
			case ACKNOWLEDGE -> {
				playVoice(level, actor, 0.58F, 1.08F);
				emitCueParticles(level, actor, ParticleTypes.HAPPY_VILLAGER, 1, 0.12);
			}
			case SHAKE_HEAD -> {
				playVoice(level, actor, 0.44F, 0.90F);
				emitCueParticles(level, actor, ParticleTypes.SMOKE, 1, 0.10);
			}
			case CONFER -> emitCueParticles(level, actor, ParticleTypes.NOTE, 1, 0.08);
			case SHIELD_TAP -> level.playSound(
				null,
				actor.getX(), actor.getY(), actor.getZ(),
				SoundEvents.SHIELD_BLOCK.value(),
				SoundSource.HOSTILE,
				0.24F,
				1.35F
			);
			case SWORD_INSPECT, AXE_SHOULDER -> emitCueParticles(
				level,
				actor,
				ParticleTypes.SMOKE,
				1,
				0.04
			);
			case ENGINEER_CHECK -> emitCueParticles(level, actor, ParticleTypes.NOTE, 1, 0.05);
			case CONFUSED_TILT -> playVoice(level, actor, 0.34F, 1.12F);
			case SUCCESSION_LOOK_AROUND -> emitCueParticles(level, actor, ParticleTypes.SMOKE, 1, 0.08);
			case SUCCESSION_SALUTE -> {
				playVoice(level, actor, 1.25F, 0.56F);
				emitCueParticles(level, actor, ParticleTypes.ANGRY_VILLAGER, 3, 0.26);
			}
			default -> {
				// 战斗动作和持续动作由各自 Goal 的声音管线负责。
			}
		}
	}

	private static void emitCueParticles(
		final ServerLevel level,
		final Mob actor,
		final net.minecraft.core.particles.ParticleOptions particle,
		final int count,
		final double spread
	) {
		level.sendParticles(
			particle,
			actor.getX(), actor.getEyeY() + 0.48, actor.getZ(),
			count, spread, 0.10, spread, 0.0
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

	private static List<RoleMember> zombieFollowersOf(
		final List<RoleMember> members,
		final @Nullable Mob leader
	) {
		List<RoleMember> followers = new ArrayList<>(members.size());
		for (RoleMember member : members) {
			if (member.mob() != leader && member.mob() instanceof Zombie) {
				followers.add(member);
			}
		}
		return followers;
	}

	static List<SquadSocialChoreography.Participant> participantsOf(final List<RoleMember> members) {
		List<SquadSocialChoreography.Participant> participants = new ArrayList<>(members.size());
		for (RoleMember member : members) {
			participants.add(new SquadSocialChoreography.Participant(
				member.mob().getId(),
				member.briefingRole(),
				member.intelligence(),
				stableKey(member.mob()),
				member.routeOutcome(),
				member.idleStyle()
			));
		}
		return List.copyOf(participants);
	}

	private static @Nullable Mob resolveCueActor(
		final SquadSocialChoreography.Cue cue,
		final @Nullable Mob leader,
		final List<RoleMember> members
	) {
		if (cue.leader()) {
			return leader;
		}
		for (RoleMember member : members) {
			if (member.mob().getId() == cue.actorEntityId()) {
				return member.mob();
			}
		}
		return null;
	}

	private static long stableKey(final Mob mob) {
		return mob.getUUID().getMostSignificantBits()
			^ Long.rotateLeft(mob.getUUID().getLeastSignificantBits(), 17);
	}

	private static int defaultIntelligence(final Mob mob) {
		return mob instanceof Zombie zombie ? ZombieIntelligence.get(zombie) : 5;
	}

	private static SquadSocialChoreography.IdleStyle defaultIdleStyle(final Mob mob, final int intelligence) {
		if (!(mob instanceof Zombie zombie)) {
			return SquadSocialChoreography.IdleStyle.NONE;
		}
		if (ZombieEngineerProfile.isEngineer(zombie)) {
			return SquadSocialChoreography.IdleStyle.ENGINEER;
		}
		if (ZombieArmory.hasShield(zombie)) {
			return SquadSocialChoreography.IdleStyle.SHIELD;
		}
		return switch (ZombieArmory.weaponClassOf(zombie.getMainHandItem())) {
			case SWORD -> SquadSocialChoreography.IdleStyle.SWORD;
			case AXE -> SquadSocialChoreography.IdleStyle.AXE;
			default -> intelligence <= 4
				? SquadSocialChoreography.IdleStyle.CONFUSED
				: SquadSocialChoreography.IdleStyle.NONE;
		};
	}

	private static boolean playBodyAction(final Mob mob, final ZombieBodyAction action) {
		if (mob instanceof Zombie zombie) {
			return ZombieBodyLanguage.play(zombie, action);
		}
		return false;
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

	/** 协调器传入的成员、当前职位、原会议职位与真实路线报告快照。 */
	record RoleMember(
		Mob mob,
		SquadRole role,
		SquadRole briefingRole,
		SquadRouteOutcome routeOutcome,
		int intelligence,
		SquadSocialChoreography.IdleStyle idleStyle
	) {
		RoleMember(final Mob mob, final SquadRole role) {
			this(
				mob,
				role,
				role,
				SquadRouteOutcome.UNASSESSED,
				defaultIntelligence(mob),
				defaultIdleStyle(mob, defaultIntelligence(mob))
			);
		}
	}

	private record StoredName(@Nullable Component name, boolean visible, SquadRole appliedRole, Component applied) {
	}
}
