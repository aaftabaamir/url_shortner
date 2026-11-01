package com.aaftab.shortener.consumer;

import com.aaftab.shortener.config.AppConfig;
import com.aaftab.shortener.dto.LinkClickedEvent;
import com.aaftab.shortener.model.Link;
import com.aaftab.shortener.repository.LinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Consumes click events completely independently of the request-serving app
 * instances. This consumer can lag, restart, or be scaled to N partitions/
 * consumers without the redirect path (LinkController -> LinkService) ever
 * noticing — that decoupling is the entire point of putting Kafka in the
 * middle instead of writing analytics synchronously in the request thread.
 */
@Component
public class AnalyticsConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsConsumer.class);

    private final LinkRepository linkRepository;

    public AnalyticsConsumer(LinkRepository linkRepository) {
        this.linkRepository = linkRepository;
    }

    @KafkaListener(topics = AppConfig.LINK_CLICKED_TOPIC, groupId = "analytics-consumer-group")
    public void onLinkClicked(LinkClickedEvent event) {
        Optional<Link> linkOpt = linkRepository.findByShortCode(event.shortCode());
        if (linkOpt.isEmpty()) {
            log.warn("Received click event for unknown short code: {}", event.shortCode());
            return;
        }

        Link link = linkOpt.get();
        link.setClickCount(link.getClickCount() + 1);
        linkRepository.save(link);
        log.debug("Recorded click for {} at {}", event.shortCode(), event.clickedAt());
    }
}
