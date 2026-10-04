package com.oldbook.controller.shipping;

import com.oldbook.dto.common.ApiResponse;
import com.oldbook.dto.shipping.CapNhatVanChuyenRequest;
import com.oldbook.dto.shipping.DonViVanChuyenResponse;
import com.oldbook.dto.shipping.TiepNhanVanChuyenRequest;
import com.oldbook.dto.shipping.VanChuyenResponse;
import com.oldbook.dto.shipping.YeuCauVanChuyenResponse;
import com.oldbook.service.shipping.VanChuyenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API cho đơn vị vận chuyển, không cần đăng nhập. Mỗi hãng có bộ API riêng theo mã:
 *   /api/shipping/GHN/...   /api/shipping/JT/...
 * Thêm hãng mới (qua /api/management/shipping-carriers) là có ngay bộ API này, không phải sửa code.
 */
@RestController
@RequestMapping("/api/shipping/{maCode}")
@RequiredArgsConstructor
public class VanChuyenController {
    private final VanChuyenService vanChuyenService;

    @GetMapping
    public ResponseEntity<ApiResponse<DonViVanChuyenResponse>> getDonVi(@PathVariable String maCode) {
        return ResponseEntity.ok(ApiResponse.success(vanChuyenService.getDonVi(maCode)));
    }

    // ===== 1. Tiếp nhận yêu cầu vận chuyển =====

    @GetMapping("/requests/pending")
    public ResponseEntity<ApiResponse<List<YeuCauVanChuyenResponse>>> getPendingRequests(@PathVariable String maCode) {
        return ResponseEntity.ok(ApiResponse.success(vanChuyenService.getYeuCauChuaTiepNhan(maCode)));
    }

    @PostMapping("/requests")
    public ResponseEntity<ApiResponse<VanChuyenResponse>> tiepNhan(
            @PathVariable String maCode,
            @Valid @RequestBody TiepNhanVanChuyenRequest request) {
        return ResponseEntity.ok(ApiResponse.success(vanChuyenService.tiepNhan(maCode, request)));
    }

    // ===== 2. Cập nhật trạng thái vận chuyển và kết quả giao hàng, hoàn trả =====

    @GetMapping("/requests")
    public ResponseEntity<ApiResponse<List<VanChuyenResponse>>> getAll(@PathVariable String maCode) {
        return ResponseEntity.ok(ApiResponse.success(vanChuyenService.getAll(maCode)));
    }

    @GetMapping("/requests/{id}")
    public ResponseEntity<ApiResponse<VanChuyenResponse>> getById(
            @PathVariable String maCode,
            @PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(vanChuyenService.getById(maCode, id)));
    }

    @PatchMapping("/requests/{id}/status")
    public ResponseEntity<ApiResponse<VanChuyenResponse>> updateStatus(
            @PathVariable String maCode,
            @PathVariable Integer id,
            @Valid @RequestBody CapNhatVanChuyenRequest request) {
        return ResponseEntity.ok(ApiResponse.success(vanChuyenService.capNhat(maCode, id, request)));
    }

    @PatchMapping("/requests/{id}/result")
    public ResponseEntity<ApiResponse<VanChuyenResponse>> updateResult(
            @PathVariable String maCode,
            @PathVariable Integer id,
            @Valid @RequestBody CapNhatVanChuyenRequest request) {
        return ResponseEntity.ok(ApiResponse.success(vanChuyenService.capNhatKetQua(maCode, id, request)));
    }
}
