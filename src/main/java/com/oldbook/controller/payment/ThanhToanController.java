package com.oldbook.controller.payment;

import com.oldbook.constant.auth.VaiTro;
import com.oldbook.dto.common.ApiResponse;
import com.oldbook.dto.order.CreateOrderRequest;
import com.oldbook.dto.payment.PaymentConfigResponse;
import com.oldbook.dto.payment.PhienThanhToanResponse;
import com.oldbook.dto.payment.TaiKhoanNhanRequest;
import com.oldbook.dto.payment.YeuCauChuyenTienResponse;
import com.oldbook.filter.auth.JwtAuthenticationFilter.AuthenticatedUserDetails;
import com.oldbook.service.payment.ChuyenTienService;
import com.oldbook.service.payment.PhienThanhToanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Thanh toán chuyển khoản phía khách hàng: phiên thanh toán và các khoản hoàn tiền của chính họ. */
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class ThanhToanController {

    private final PhienThanhToanService phienService;
    private final ChuyenTienService chuyenTienService;

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<PaymentConfigResponse>> config() {
        return ResponseEntity.ok(ApiResponse.success(phienService.cauHinh()));
    }

    /** Nhận đúng nội dung của yêu cầu đặt hàng; đơn chỉ được tạo khi tiền về. */
    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<PhienThanhToanResponse>> taoPhien(
            @Valid @RequestBody CreateOrderRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(phienService.taoPhien(maNDKhachHang(), request)));
    }

    @GetMapping("/sessions/{maPhien}")
    public ResponseEntity<ApiResponse<PhienThanhToanResponse>> layPhien(@PathVariable Integer maPhien) {
        return ResponseEntity.ok(ApiResponse.success(phienService.layPhien(maNDKhachHang(), maPhien)));
    }

    @DeleteMapping("/sessions/{maPhien}")
    public ResponseEntity<ApiResponse<PhienThanhToanResponse>> huyPhien(@PathVariable Integer maPhien) {
        return ResponseEntity.ok(ApiResponse.success(phienService.huyPhien(maNDKhachHang(), maPhien)));
    }

    @GetMapping("/refunds")
    public ResponseEntity<ApiResponse<List<YeuCauChuyenTienResponse>>> hoanTienCuaToi() {
        return ResponseEntity.ok(ApiResponse.success(chuyenTienService.hoanTienCuaToi(maNDKhachHang())));
    }

    @PutMapping("/refunds/{maYeuCau}/account")
    public ResponseEntity<ApiResponse<YeuCauChuyenTienResponse>> nhapTaiKhoanNhan(
            @PathVariable Integer maYeuCau,
            @Valid @RequestBody TaiKhoanNhanRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                chuyenTienService.nhapTaiKhoanNhan(maNDKhachHang(), maYeuCau, request)
        ));
    }

    private Integer maNDKhachHang() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !(authentication.getDetails() instanceof AuthenticatedUserDetails user)) {
            throw new IllegalStateException("Thông tin xác thực không hợp lệ");
        }
        if (!VaiTro.KHACH_HANG.name().equals(user.vaiTro())) {
            throw new AccessDeniedException("Chỉ khách hàng mới dùng được chức năng thanh toán");
        }
        return user.maND();
    }
}
