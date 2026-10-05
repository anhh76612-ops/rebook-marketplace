package com.oldbook.controller.catalog;

import com.oldbook.constant.auth.VaiTro;
import com.oldbook.dto.catalog.SachResponse;
import com.oldbook.dto.common.ApiResponse;
import com.oldbook.filter.auth.JwtAuthenticationFilter.AuthenticatedUserDetails;
import com.oldbook.service.catalog.YeuThichService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final YeuThichService yeuThichService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SachResponse>>> getWishlist() {
        return ResponseEntity.ok(ApiResponse.success(yeuThichService.getWishlist(getMaNDKhachHang())));
    }

    @PutMapping("/{maSach}")
    public ResponseEntity<ApiResponse<SachResponse>> addBook(@PathVariable Integer maSach) {
        return ResponseEntity.ok(ApiResponse.success(yeuThichService.addBook(getMaNDKhachHang(), maSach)));
    }

    @DeleteMapping("/{maSach}")
    public ResponseEntity<ApiResponse<Void>> removeBook(@PathVariable Integer maSach) {
        yeuThichService.removeBook(getMaNDKhachHang(), maSach);
        return ResponseEntity.ok(ApiResponse.success("Đã bỏ yêu thích sách"));
    }

    private Integer getMaNDKhachHang() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof AuthenticatedUserDetails user)
                || user.maND() == null || !VaiTro.KHACH_HANG.name().equals(user.vaiTro())) {
            throw new AccessDeniedException("Chỉ khách hàng mới sử dụng được danh sách yêu thích");
        }
        return user.maND();
    }
}
