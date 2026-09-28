package com.nhom9.oldbook_marketplace.auth.controller;

import com.nhom9.oldbook_marketplace.auth.dto.LoginRequest;
import com.nhom9.oldbook_marketplace.auth.dto.LoginResponse;
import com.nhom9.oldbook_marketplace.auth.dto.RegisterRequest;
import com.nhom9.oldbook_marketplace.auth.dto.SelectRoleRequest;
import com.nhom9.oldbook_marketplace.auth.service.AuthService;
import com.nhom9.oldbook_marketplace.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Đăng ký tài khoản mới.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        authService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đăng ký tài khoản thành công"));
    }

    /**
     * Đăng nhập.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        LoginResponse response = authService.login(request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Chọn vai trò để lấy JWT chính thức.
     */
    @PostMapping("/select-role")
    public ResponseEntity<ApiResponse<LoginResponse>> selectRole(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @Valid @RequestBody SelectRoleRequest request
    ) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "Thiếu token xác thực"));
        }

        String token = authorization.substring(7);

        LoginResponse response = authService.selectRole(token, request.getMaTK());

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}