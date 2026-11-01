package com.aaftab.shortener.controller;

import com.aaftab.shortener.dto.LinkDtos.CreateLinkRequest;
import com.aaftab.shortener.dto.LinkDtos.CreateLinkResponse;
import com.aaftab.shortener.dto.LinkDtos.LinkStatsResponse;
import com.aaftab.shortener.exception.RateLimitExceededException;
import com.aaftab.shortener.model.Link;
import com.aaftab.shortener.service.LinkService;
import com.aaftab.shortener.service.RateLimiterService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
public class LinkController {

    private final LinkService linkService;
    private final RateLimiterService rateLimiterService;

    public LinkController(LinkService linkService, RateLimiterService rateLimiterService) {
        this.linkService = linkService;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("/api/v1/links")
    public ResponseEntity<CreateLinkResponse> createLink(
            @Valid @RequestBody CreateLinkRequest request,
            @RequestHeader("X-API-Key") String apiKey) {

        if (!rateLimiterService.tryConsume(apiKey)) {
            throw new RateLimitExceededException(apiKey);
        }

        Link link = linkService.createShortLink(request.longUrl());
        String shortUrl = "http://localhost:8080/" + link.getShortCode();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateLinkResponse(link.getShortCode(), shortUrl));
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> resolve(@PathVariable String shortCode) {
        String longUrl = linkService.resolve(shortCode);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(longUrl))
                .build();
    }

    @GetMapping("/api/v1/links/{shortCode}/stats")
    public ResponseEntity<LinkStatsResponse> getStats(
            @PathVariable String shortCode,
            @RequestHeader("X-API-Key") String apiKey) {

        Link link = linkService.getStats(shortCode);
        return ResponseEntity.ok(new LinkStatsResponse(
                link.getShortCode(), link.getLongUrl(), link.getClickCount()));
    }
}
