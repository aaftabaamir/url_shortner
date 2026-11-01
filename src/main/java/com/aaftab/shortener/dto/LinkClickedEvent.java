package com.aaftab.shortener.dto;

import java.time.Instant;

/**
 * Fired asynchronously whenever a short link is resolved. Consumed by the
 * analytics consumer, completely decoupled from the redirect hot path.
 */
public record LinkClickedEvent(String shortCode, Instant clickedAt) {
}
