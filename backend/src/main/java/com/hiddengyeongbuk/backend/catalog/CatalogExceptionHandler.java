package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.catalog.dto.ApiErrorResponse;
import com.hiddengyeongbuk.backend.catalog.dto.ApiErrorResponse.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** API 명세서 1장 에러 응답 표준 { "error": { "code", "message" } }을 catalog 컨트롤러 전역에 적용한다. */
@RestControllerAdvice(basePackages = "com.hiddengyeongbuk.backend.catalog")
public class CatalogExceptionHandler {

    @ExceptionHandler(PlaceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(PlaceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(new ApiError("NOT_FOUND", e.getMessage())));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> handleInvalidParam(Exception e) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse(new ApiError("INVALID_PARAM", e.getMessage())));
    }
}
