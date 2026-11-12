package com.aftersales.agent.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 智能体会话管理器：按 chatId 复用同一个 {@link AftersalesAgent} 实例，从而保留多轮对话上下文。
 * <p>
 * 设计取舍：
 * <ul>
 *   <li>LRU 上限 {@value #MAX_SESSIONS} 个会话，避免内存无限增长；</li>
 *   <li>空闲超过 {@value #IDLE_TTL_MS} 毫秒的会话在下次访问时被回收；</li>
 *   <li>同一 chatId 的并发请求由 Agent 自身的 RUNNING 状态拒绝，返回可读提示。</li>
 * </ul>
 */
@Slf4j
@Component
public class AgentChatSessionManager {

    private static final int MAX_SESSIONS = 32;
    private static final long IDLE_TTL_MS = 30 * 60 * 1000L;

    private final Map<String, AftersalesAgent> sessions = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, Long> lastAccess = new HashMap<>();

    /**
     * 获取或创建 chatId 对应的智能体实例（访问顺序 LRU）。
     *
     * @param chatId  会话标识
     * @param factory 实例工厂（首次访问时创建）
     * @return 该会话专属的智能体
     */
    public synchronized AftersalesAgent getOrCreate(String chatId, Supplier<AftersalesAgent> factory) {
        evictExpired();

        lastAccess.put(chatId, System.currentTimeMillis());
        AftersalesAgent agent = sessions.get(chatId);
        if (agent != null) {
            return agent;
        }

        agent = factory.get();
        agent.setKeepHistory(true);
        sessions.put(chatId, agent);
        lastAccess.put(chatId, System.currentTimeMillis());

        if (sessions.size() > MAX_SESSIONS) {
            Iterator<Map.Entry<String, AftersalesAgent>> iterator = sessions.entrySet().iterator();
            if (iterator.hasNext()) {
                String eldest = iterator.next().getKey();
                iterator.remove();
                lastAccess.remove(eldest);
                log.info("agent session evicted by LRU: chatId={}", eldest);
            }
        }
        log.info("agent session created: chatId={} total={}", chatId, sessions.size());
        return agent;
    }

    /**
     * 回收空闲超时的会话。
     */
    private void evictExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> iterator = lastAccess.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (now - entry.getValue() > IDLE_TTL_MS) {
                sessions.remove(entry.getKey());
                iterator.remove();
                log.info("agent session expired: chatId={}", entry.getKey());
            }
        }
    }

    /**
     * 当前活跃会话数（供测试与监控使用）。
     *
     * @return 会话数量
     */
    public synchronized int size() {
        return sessions.size();
    }
}
