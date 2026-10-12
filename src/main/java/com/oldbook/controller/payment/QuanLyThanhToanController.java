package com.oldbook.controller.payment;

import com.oldbook.constant.payment.LoaiChuyenTien;
import com.oldbook.constant.payment.TrangThaiChuyenTien;
import com.oldbook.dto.catalog.PageResponse;
import com.oldbook.dto.common.ApiResponse;
import com.oldbook.dto.payment.GiaoDichNganHangResponse;
import com.oldbook.dto.payment.YeuCauChuyenTienResponse;
import com.oldbook.filter.auth.JwtAuthenticationFilter.AuthenticatedUserDetails;
import com.oldbook.service.payment.ChuyenTienService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/** Quản lý xử lý tiền sàn phải chuyển đi và xem sổ giao dịch ngân hàng. Quyền do luật /api/management/** kiểm. */
@RestController
@RequestMapping("/api/management/payments")
@RequiredArgsConstructor
public class QuanLyThanhToanController {

    private final ChuyenTienService chuyenTienService;

    @GetMapping("/transfers")
    public ResponseEntity<ApiResponse<PageResponse<YeuCauChuyenTienResponse>>> danhSach(
            @RequestParam LoaiChuyenTien loai,
            @RequestParam(required = false) TrangThaiChuyenTien trangThai,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(chuyenTienService.danhSach(loai, trangThai, page, size)));
    }

    /** Bấm sau khi đã chuyển khoản xong bằng tài khoản của sàn. */
    @PostMapping("/transfers/{maYeuCau}/done")
    public ResponseEntity<ApiResponse<YeuCauChuyenTienResponse>> danhDauDaChuyen(@PathVariable Integer maYeuCau) {
        return ResponseEntity.ok(ApiResponse.success(chuyenTienService.danhDauDaChuyen(maTK(), maYeuCau)));
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<PageResponse<GiaoDichNganHangResponse>>> giaoDich(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(chuyenTienService.danhSachGiaoDich(page, size)));
    }

    private static Integer maTK() {
        return ((AuthenticatedUserDetails) SecurityContextHolder.getContext().getAuthentication().getDetails()).maTK();
    }
}
