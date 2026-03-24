package com.zyc.copier_v0.modules.account.config.service;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisRouteLockManager {

    private static final Logger log = LoggerFactory.getLogger(RedisRouteLockManager.class);
    private static final String LOCK_PREFIX = "lock:route:master:";
    private static final long DEFAULT_LOCK_TTL_SECONDS = 10;
    private static final long SPIN_INTERVAL_MILLIS = 50;

    private final StringRedisTemplate stringRedisTemplate;

    public RedisRouteLockManager(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void acquireLock(Long masterAccountId, long timeoutMillis) {
        if (masterAccountId == null) {
            return;
        }
        String key = LOCK_PREFIX + masterAccountId;
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMillis) {
            Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
                    key, "1", Duration.ofSeconds(DEFAULT_LOCK_TTL_SECONDS)
            );
            if (Boolean.TRUE.equals(acquired)) {
                return;
            }
            try {
                Thread.sleep(SPIN_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while acquiring route lock for master: " + masterAccountId, e);
            }
        }
        throw new IllegalStateException("Timeout acquiring route lock for master: " + masterAccountId);
    }

    public void releaseLock(Long masterAccountId) {
        if (masterAccountId == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(LOCK_PREFIX + masterAccountId);
        } catch (Exception ex) {
            log.warn("Failed to release route lock for master: {}", masterAccountId, ex);
        }
    }

    public <T> T executeWithMasterLocks(List<Long> masterAccountIds, long timeoutMillis, Supplier<T> action) {
        if (masterAccountIds == null || masterAccountIds.isEmpty()) {
            return action.get();
        }
        List<Long> sortedIds = masterAccountIds.stream().distinct().sorted().toList();
        int lockedCount = 0;
        try {
            for (Long id : sortedIds) {
                acquireLock(id, timeoutMillis);
                lockedCount++;
            }
            return action.get();
        } finally {
            for (int i = 0; i < lockedCount; i++) {
                releaseLock(sortedIds.get(i));
            }
        }
    }
}
