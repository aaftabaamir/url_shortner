package com.aaftab.shortener.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "links", indexes = {
        @Index(name = "idx_short_code", columnList = "shortCode", unique = true)
})
public class Link {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 16)
    private String shortCode;

    @Column(nullable = false, length = 2048)
    private String longUrl;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column
    private Long clickCount = 0L;

    protected Link() {
        // JPA
    }

    public Link(String shortCode, String longUrl) {
        this.shortCode = shortCode;
        this.longUrl = longUrl;
    }

    public Long getId() { return id; }
    public String getShortCode() { return shortCode; }
    public void setShortCode(String shortCode) { this.shortCode = shortCode; }
    public String getLongUrl() { return longUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getClickCount() { return clickCount; }
    public void setClickCount(Long clickCount) { this.clickCount = clickCount; }
}
