package com.wjz.mobsthinknow.ai.zombie.squad;

/** 供纯 Java 首领选举逻辑使用的数据投影，便于脱离 Minecraft 世界做单元测试。 */
public record SquadLeaderCandidate(int entityId, int intelligence, float health) {
}
