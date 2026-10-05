package com.oldbook.controller.order;

import com.oldbook.constant.auth.VaiTro;
import com.oldbook.dto.common.ApiResponse;
import com.oldbook.dto.order.CreateOrderRequest;
import com.oldbook.dto.order.OrderResponse;
import com.oldbook.filter.auth.JwtAuthenticationFilter.AuthenticatedUserDetails;
import com.oldbook.service.order.DonHangService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/order/orders")
@RequiredArgsConstructor
public class DonHangController {

    private final DonHangService donHangService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request
    ) {
        Integer maND = getMaNDKhachHang();

        return ResponseEntity.ok(
                ApiResponse.<OrderResponse>builder()
                        .code(200)
                        .message("Đặt hàng thành công")
                        .data(donHangService.createOrder(maND, request))
                        .build()
        );
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyOrders() {
        Integer maND = getMaNDKhachHang();

        return ResponseEntity.ok(
                ApiResponse.success(donHangService.getMyOrders(maND))
        );
    }

    @GetMapping("/{maDH}")
    public ResponseEntity<ApiResponse<OrderResponse>> getMyOrder(
            @PathVariable Integer maDH
    ) {
        Integer maND = getMaNDKhachHang();

        return ResponseEntity.ok(
                ApiResponse.success(donHangService.getMyOrder(maND, maDH))
        );
    }

    private Integer getMaNDKhachHang() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !(authentication.getDetails() instanceof AuthenticatedUserDetails user)) {
            throw new IllegalStateException("Thông tin xác thực không hợp lệ");
        }

        if (!VaiTro.KHACH_HANG.name().equals(user.vaiTro())) {
            throw new AccessDeniedException(
                    "Chỉ khách hàng mới được đặt hàng"
            );
        }

        return user.maND();
    }
}
