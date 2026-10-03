package com.oldbook.controller.shipping;

import com.oldbook.dto.common.ApiResponse;
import com.oldbook.dto.shipping.CapNhatVanChuyenRequest;
import com.oldbook.dto.shipping.TiepNhanVanChuyenRequest;
import com.oldbook.dto.shipping.VanChuyenResponse;
import com.oldbook.service.shipping.VanChuyenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
public class VanChuyenController {
    private final VanChuyenService vanChuyenService;

    // ===== 1. Cài đặt tiếp nhận yêu cầu vận chuyển =====

    @PostMapping("/requests")
    public ResponseEntity<ApiResponse<VanChuyenResponse>> tiepNhan(@Valid @RequestBody TiepNhanVanChuyenRequest request) {
        return ResponseEntity.ok(ApiResponse.success(vanChuyenService.tiepNhan(request)));
    }

    @GetMapping("/requests/pending")
    public ResponseEntity<ApiResponse<?>> getPendingRequests() {
        return ResponseEntity.ok(ApiResponse.success(vanChuyenService.getYeuCauChuaTiepNhan()));
    }

    // Chỉ đọc: lấy danh sách đơn vị vận chuyển để chọn khi tiếp nhận
    @GetMapping("/partners")
    public ResponseEntity<ApiResponse<?>> getPartners() {
        return ResponseEntity.ok(ApiResponse.success(vanChuyenService.getPartners()));
    }

    // ===== 2. Cài đặt cập nhật trạng thái vận chuyển và kết quả giao hàng, hoàn trả =====

    @GetMapping("/requests")
    public ResponseEntity<ApiResponse<?>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(vanChuyenService.getAll()));
    }

    @GetMapping("/requests/{id}")
    public ResponseEntity<ApiResponse<VanChuyenResponse>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(vanChuyenService.getById(id)));
    }

    @PatchMapping("/requests/{id}/status")
    public ResponseEntity<ApiResponse<VanChuyenResponse>> updateStatus(
            @PathVariable Integer id,
            @Valid @RequestBody CapNhatVanChuyenRequest request) {
        return ResponseEntity.ok(ApiResponse.success(vanChuyenService.capNhat(id, request)));
    }

    @PatchMapping("/requests/{id}/result")
    public ResponseEntity<ApiResponse<VanChuyenResponse>> updateResult(
            @PathVariable Integer id,
            @Valid @RequestBody CapNhatVanChuyenRequest request) {
        return ResponseEntity.ok(ApiResponse.success(vanChuyenService.capNhat(id, request)));
    }
}
