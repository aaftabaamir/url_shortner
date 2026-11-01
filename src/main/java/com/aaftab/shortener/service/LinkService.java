package com.aaftab.shortener.service;

import com.aaftab.shortener.config.AppConfig;
import com.aaftab.shortener.dto.LinkClickedEvent;
import com.aaftab.shortener.exception.LinkNotFoundException;
import com.aaftab.shortener.model.Link;
import com.aaftab.shortener.repository.LinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class LinkService {

    private static final Logger log = LoggerFactory.getLogger(LinkService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final String CACHE_PREFIX = "link:";

    private final LinkRepository linkRepository;
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, LinkClickedEvent> kafkaTemplate;
    private final Base62Encoder base62Encoder;

    public LinkService(LinkRepository linkRepository,
                        StringRedisTemplate redisTemplate,
                        KafkaTemplate<String, LinkClickedEvent> kafkaTemplate,
                        Base62Encoder base62Encoder) {
        this.linkRepository = linkRepository;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.base62Encoder = base62Encoder;
    }

    /**
     * Creates a short link. Writes to Postgres (source of truth) then
     * proactively populates the cache (write-through) so the very first
     * redirect for a brand-new link is already a cache hit.
     */
    public Link createShortLink(String longUrl) {
        // Persist first with an empty code to obtain the auto-increment ID
        // (Base62 encoding requires the ID to exist first), then set the
        // derived short code and save again. Two writes, but both are cheap
        // single-row upserts and this only happens on link *creation*, which
        // is a tiny fraction of traffic compared to redirects.
        Link link = linkRepository.save(new Link("", longUrl));
        String shortCode = base62Encoder.encode(link.getId());
        link.setShortCode(shortCode);
        link = linkRepository.save(link);

        redisTemplate.opsForValue().set(CACHE_PREFIX + shortCode, longUrl, CACHE_TTL);
        return link;
    }

    /**
     * Resolves a short code to its long URL. Cache-aside: check Redis first,
     * fall back to Postgres on miss, then populate the cache for next time.
     */
    public String resolve(String shortCode) {
        String cacheKey = CACHE_PREFIX + shortCode;
        String cached = redisTemplate.opsForValue().get(cacheKey);

        String longUrl;
        if (cached != null) {
            longUrl = cached;
        } else {
            Link link = linkRepository.findByShortCode(shortCode)
                    .orElseThrow(() -> new LinkNotFoundException(shortCode));
            longUrl = link.getLongUrl();
            redisTemplate.opsForValue().set(cacheKey, longUrl, CACHE_TTL);
        }

        publishClickEventAsync(shortCode);
        return longUrl;
    }

    public Link getStats(String shortCode) {
        return linkRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new LinkNotFoundException(shortCode));
    }

    /**
     * Fires the click event to Kafka without blocking the caller. If Kafka is
     * briefly unavailable, the redirect still succeeds — analytics is
     * best-effort by design, not a source of truth.
     */
    private void publishClickEventAsync(String shortCode) {
        try {
            kafkaTemplate.send(AppConfig.LINK_CLICKED_TOPIC, shortCode,
                    new LinkClickedEvent(shortCode, Instant.now()));
        } catch (Exception e) {
            log.warn("Failed to publish click event for {}: {}", shortCode, e.getMessage());
        }
    }
}
