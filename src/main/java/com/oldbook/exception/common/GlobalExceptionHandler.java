// File: src/main/java/com/nhom9/oldbook_marketplace/common/exception/GlobalExceptionHandler.java
package com.oldbook.exception.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.oldbook.dto.common.ApiResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Bắt lỗi nghiệp vụ (BusinessException)
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(400, e.getMessage()));
    }

    // Bắt lỗi Validate Request (ví dụ: @NotBlank, @Email)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(400, errorMessage));
    }
}