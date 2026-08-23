package com.example.app.domain.scrap.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ScrapResponse(int scrapCount, @JsonProperty("isScrapped") boolean isScrapped) {
}
