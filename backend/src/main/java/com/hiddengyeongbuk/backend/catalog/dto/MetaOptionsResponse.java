package com.hiddengyeongbuk.backend.catalog.dto;

import java.util.List;

public record MetaOptionsResponse(
        List<Option> durations, List<Option> difficulties, List<Option> interests, List<Option> hubs) {

    public record Option(String code, String labelKo, String labelEn) {}
}
