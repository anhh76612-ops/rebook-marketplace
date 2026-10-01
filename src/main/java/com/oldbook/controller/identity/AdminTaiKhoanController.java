package com.oldbook.controller.identity;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.oldbook.dto.common.ApiResponse;
import com.oldbook.dto.identity.AdminTaiKhoanResponse;
import com.oldbook.dto.identity.ChangeRoleRequest;
import com.oldbook.dto.identity.CreateTaiKhoanRequest;
import com.oldbook.dto.identity.LockTaiKhoanRequest;
import com.oldbook.filter.auth.JwtAuthenticationFilter.AuthenticatedUserDetails;
import com.oldbook.service.identity.AdminTaiKhoanService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/tai-khoan")
@RequiredArgsConstructor
public class AdminTaiKhoanController {

    private final AdminTaiKhoanService adminTaiKhoanService;

    /**
     * =========================================================
     * 1. XEM DANH SÁCH / TÌM KIẾM / LỌC TÀI KHOẢN
     * =========================================================
     *
     * GET /api/admin/tai-khoan
     *
     * Ví dụ:
     * GET /api/admin/tai-khoan
     * GET /api/admin/tai-khoan?tuKhoa=abc
     * GET /api/admin/tai-khoan?vaiTro=KHACH_HANG
     * GET /api/admin/tai-khoan?trangThai=HOAT_DONG
     * GET /api/admin/tai-khoan?tuKhoa=abc&vaiTro=KHACH_HANG&trangThai=HOAT_DONG
     */
    @GetMapping
    public ApiResponse<List<AdminTaiKhoanResponse>> getAll(
            @RequestParam(required = false) String tuKhoa,
            @RequestParam(required = false) String vaiTro,
            @RequestParam(required = false) String trangThai
    ) {
        return ApiResponse.success(
                adminTaiKhoanService.getAll(
                        tuKhoa,
                        vaiTro,
                        trangThai
                )
        );
    }

    /**
     * =========================================================
     * 2. TẠO TÀI KHOẢN
     * =========================================================
     *
     * POST /api/admin/tai-khoan
     */
    @PostMapping
    public ApiResponse<AdminTaiKhoanResponse> create(
            @RequestBody CreateTaiKhoanRequest request
    ) {
        AuthenticatedUserDetails currentUser =
                getCurrentUser();

        AdminTaiKhoanResponse response =
                adminTaiKhoanService.create(
                        request,
                        currentUser.maTK(),
                        getClientIp()
                );

        return ApiResponse.success(response);
    }

    /**
     * =========================================================
     * 3. KHÓA TÀI KHOẢN
     * =========================================================
     *
     * PUT /api/admin/tai-khoan/{maTK}/khoa
     */
    @PutMapping("/{maTK}/khoa")
    public ApiResponse<Void> lock(
            @PathVariable Integer maTK,
            @RequestBody LockTaiKhoanRequest request
    ) {
        AuthenticatedUserDetails currentUser =
                getCurrentUser();

        adminTaiKhoanService.lock(
                maTK,
                request,
                currentUser.maTK(),
                getClientIp()
        );

        return ApiResponse.success(
                "Khóa tài khoản thành công"
        );
    }

    /**
     * =========================================================
     * 4. MỞ KHÓA TÀI KHOẢN
     * =========================================================
     *
     * PUT /api/admin/tai-khoan/{maTK}/mo-khoa
     */
    @PutMapping("/{maTK}/mo-khoa")
    public ApiResponse<Void> unlock(
            @PathVariable Integer maTK
    ) {
        AuthenticatedUserDetails currentUser =
                getCurrentUser();

        adminTaiKhoanService.unlock(
                maTK,
                currentUser.maTK(),
                getClientIp()
        );

        return ApiResponse.success(
                "Mở khóa tài khoản thành công"
        );
    }

    /**
     * =========================================================
     * 5. ĐỔI VAI TRÒ
     * =========================================================
     *
     * PUT /api/admin/tai-khoan/{maTK}/vai-tro
     */
    @PutMapping("/{maTK}/vai-tro")
    public ApiResponse<AdminTaiKhoanResponse> changeRole(
            @PathVariable Integer maTK,
            @RequestBody ChangeRoleRequest request
    ) {
        AuthenticatedUserDetails currentUser =
                getCurrentUser();

        AdminTaiKhoanResponse response =
                adminTaiKhoanService.changeRole(
                        maTK,
                        request,
                        currentUser.maTK(),
                        getClientIp()
                );

        return ApiResponse.success(response);
    }

    /**
     * =========================================================
     * HÀM LẤY USER HIỆN TẠI TỪ JWT
     * =========================================================
     */
    private AuthenticatedUserDetails getCurrentUser() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null) {
            throw new IllegalStateException(
                    "Không xác định được người dùng hiện tại"
            );
        }

        Object details =
                authentication.getDetails();

        if (!(details instanceof AuthenticatedUserDetails userDetails)) {
            throw new IllegalStateException(
                    "Thông tin xác thực không hợp lệ"
            );
        }

        return userDetails;
    }

    /**
     * =========================================================
     * LẤY IP CLIENT
     * =========================================================
     */
    private String getClientIp() {

        HttpServletRequest request =
                getCurrentHttpRequest();

        String xForwardedFor =
                request.getHeader("X-Forwarded-For");

        if (xForwardedFor != null
                && !xForwardedFor.isBlank()) {

            return xForwardedFor
                    .split(",")[0]
                    .trim();
        }

        String xRealIp =
                request.getHeader("X-Real-IP");

        if (xRealIp != null
                && !xRealIp.isBlank()) {

            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    /**
     * =========================================================
     * LẤY HTTP REQUEST HIỆN TẠI
     * =========================================================
     */
    private HttpServletRequest getCurrentHttpRequest() {

        return ((org.springframework.web.context.request.ServletRequestAttributes)
                org.springframework.web.context.request.RequestContextHolder
                        .currentRequestAttributes())
                .getRequest();
    }
}