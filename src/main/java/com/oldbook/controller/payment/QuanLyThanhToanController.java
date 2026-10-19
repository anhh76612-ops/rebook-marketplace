package com.oldbook.controller.payment;

import com.oldbook.constant.payment.LoaiChuyenTien;
import com.oldbook.constant.payment.TrangThaiChuyenTien;
import com.oldbook.dto.catalog.PageResponse;
import com.oldbook.dto.common.ApiResponse;
import com.oldbook.constant.payment.KetQuaDoiSoat;
import com.oldbook.dto.management.QuanLyDto.GiaoDichTrang;
import com.oldbook.dto.payment.YeuCauChuyenTienResponse;
import com.oldbook.filter.auth.JwtAuthenticationFilter.AuthenticatedUserDetails;
import com.oldbook.service.payment.ChuyenTienService;
import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

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
            @RequestParam(required = false) String tuKhoa,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(chuyenTienService.danhSach(loai, trangThai, tuKhoa, page, size)));
    }

    /** Bấm sau khi đã chuyển khoản xong bằng tài khoản của sàn. */
    @PostMapping("/transfers/{maYeuCau}/done")
    public ResponseEntity<ApiResponse<YeuCauChuyenTienResponse>> danhDauDaChuyen(@PathVariable Integer maYeuCau) {
        return ResponseEntity.ok(ApiResponse.success(chuyenTienService.danhDauDaChuyen(maTK(), maYeuCau)));
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<GiaoDichTrang>> giaoDich(
            @RequestParam(required = false) String tuKhoa,
            @RequestParam(required = false) KetQuaDoiSoat ketQua,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tuNgay,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate denNgay,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                chuyenTienService.danhSachGiaoDich(tuKhoa, ketQua, tuNgay, denNgay, page, size)));
    }

    private static Integer maTK() {
        return ((AuthenticatedUserDetails) SecurityContextHolder.getContext().getAuthentication().getDetails()).maTK();
    }
}
