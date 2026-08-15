package com.hiddengyeongbuk.backend.catalog;

import com.hiddengyeongbuk.backend.catalog.course.NoCourseFoundException;
import com.hiddengyeongbuk.backend.catalog.dto.ApiErrorResponse;
import com.hiddengyeongbuk.backend.catalog.dto.ApiErrorResponse.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * API 명세서 1장 에러 응답 표준 { "error": { "code", "message" } }을 catalog 컨트롤러 전역에 적용한다.
 * 어떤 실패 경로로 들어와도(깨진 JSON, 필수 파라미터 누락, 예상 못 한 500) 같은 포맷이 나가야
 * 클라이언트가 에러 파싱 분기를 하나만 가질 수 있다.
 */
@RestControllerAdvice(basePackages = "com.hiddengyeongbuk.backend.catalog")
public class CatalogExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CatalogExceptionHandler.class);

    @ExceptionHandler({PlaceNotFoundException.class, HubNotFoundException.class, RegionNotFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFound(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(new ApiError("NOT_FOUND", e.getMessage())));
    }

    /**
     * 422 — 요청 자체는 올바른데 결과를 만들 수 없는 경우. 400(요청이 틀림)이나 빈 200(결과 없음)과
     * 구분해야 클라이언트가 "조건을 넓혀 보세요"를 띄울 수 있다.
     */
    @ExceptionHandler(NoCourseFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoCourseFound(NoCourseFoundException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiErrorResponse(new ApiError("NO_COURSE_FOUND", e.getMessage())));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> handleInvalidParam(Exception e) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse(new ApiError("INVALID_PARAM", e.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("요청 값이 올바르지 않습니다");
        return ResponseEntity.badRequest().body(new ApiErrorResponse(new ApiError("INVALID_PARAM", message)));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiErrorResponse> handleUnreadableRequest(Exception e) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(new ApiError("INVALID_PARAM", "요청 형식이 올바르지 않습니다: " + e.getMessage())));
    }

    /** 나머지 전부 — 내부 사정은 로그로만 남기고 클라이언트에는 상세를 흘리지 않는다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception e) {
        log.error("catalog API 처리되지 않은 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(new ApiError("INTERNAL_ERROR", "일시적인 오류가 발생했습니다")));
    }
}
