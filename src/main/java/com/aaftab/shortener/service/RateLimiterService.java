package com.aaftab.shortener.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Distributed token-bucket rate limiter backed by Redis, so the limit is
 * enforced consistently across all stateless app instances behind the load
 * balancer (a per-JVM in-memory limiter would let a client get N * instanceCount
 * requests through instead of N).
 *
 * Simplified fixed-refill implementation for clarity; a production version
 * would use a Lua script for atomic check-and-decrement.
 */
@Service
public class RateLimiterService {

    private static final int BUCKET_CAPACITY = 100; // requests
    private static final Duration REFILL_WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redisTemplate;

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryConsume(String apiKey) {
        String key = "ratelimit:" + apiKey;
        Long current = redisTemplate.opsForValue().increment(key);
        if (current != null && current == 1L) {
            redisTemplate.expire(key, REFILL_WINDOW);
        }
        return current != null && current <= BUCKET_CAPACITY;
    }
}
