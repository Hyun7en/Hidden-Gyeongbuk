package com.hiddengyeongbuk.backend.catalog.dto;

public record ApiErrorResponse(ApiError error) {
    public record ApiError(String code, String message) {}
}
