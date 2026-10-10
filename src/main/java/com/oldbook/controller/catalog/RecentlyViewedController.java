package com.oldbook.controller.catalog;

import com.oldbook.constant.auth.VaiTro;
import com.oldbook.dto.catalog.SachResponse;
import com.oldbook.dto.common.ApiResponse;
import com.oldbook.filter.auth.JwtAuthenticationFilter.AuthenticatedUserDetails;
import com.oldbook.service.catalog.SachDaXemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recently-viewed")
@RequiredArgsConstructor
public class RecentlyViewedController {

    private final SachDaXemService sachDaXemService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SachResponse>>> getRecentlyViewed() {
        return ResponseEntity.ok(ApiResponse.success(sachDaXemService.getRecentlyViewed(getMaNDKhachHang())));
    }

    @PutMapping("/{maSach}")
    public ResponseEntity<ApiResponse<SachResponse>> recordView(@PathVariable Integer maSach) {
        return ResponseEntity.ok(ApiResponse.success(sachDaXemService.recordView(getMaNDKhachHang(), maSach)));
    }

    private Integer getMaNDKhachHang() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof AuthenticatedUserDetails user)
                || user.maND() == null || !VaiTro.KHACH_HANG.name().equals(user.vaiTro())) {
            throw new AccessDeniedException("Chỉ khách hàng mới sử dụng được lịch sử sách đã xem");
        }
        return user.maND();
    }
}
