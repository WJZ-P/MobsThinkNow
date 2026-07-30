package com.wjz.mobsthinknow.ai.zombie.squad;

/** 供纯 Java 首领选举逻辑使用的数据投影；随机票来自实体 UUID，因此跨物种且与生成顺序无关。 */
public record SquadLeaderCandidate(int entityId, int intelligence, long randomTicket) {
}
