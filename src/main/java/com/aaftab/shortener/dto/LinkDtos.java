package com.aaftab.shortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class LinkDtos {

    public record CreateLinkRequest(
            @NotBlank
            @Pattern(regexp = "^https?://.+", message = "longUrl must start with http:// or https://")
            String longUrl
    ) {}

    public record CreateLinkResponse(String shortCode, String shortUrl) {}

    public record LinkStatsResponse(String shortCode, String longUrl, long clickCount) {}
}
