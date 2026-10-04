package com.oldbook.controller.shipping;

import com.oldbook.dto.common.ApiResponse;
import com.oldbook.dto.shipping.CapNhatDonViVanChuyenRequest;
import com.oldbook.dto.shipping.DoiTrangThaiDonViVanChuyenRequest;
import com.oldbook.dto.shipping.DonViVanChuyenResponse;
import com.oldbook.dto.shipping.TaoDonViVanChuyenRequest;
import com.oldbook.service.shipping.QuanLyDonViVanChuyenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Chỉ QUAN_LY / QUAN_TRI_VIEN (cấu hình trong SecurityConfig). */
@RestController
@RequestMapping("/api/management/shipping-carriers")
@RequiredArgsConstructor
public class QuanLyDonViVanChuyenController {

    private final QuanLyDonViVanChuyenService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DonViVanChuyenResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(service.getAll()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DonViVanChuyenResponse>> create(@Valid @RequestBody TaoDonViVanChuyenRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.create(request)));
    }

    @PutMapping("/{maCode}")
    public ResponseEntity<ApiResponse<DonViVanChuyenResponse>> update(
            @PathVariable String maCode,
            @Valid @RequestBody CapNhatDonViVanChuyenRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(maCode, request)));
    }

    @PatchMapping("/{maCode}/status")
    public ResponseEntity<ApiResponse<DonViVanChuyenResponse>> changeStatus(
            @PathVariable String maCode,
            @Valid @RequestBody DoiTrangThaiDonViVanChuyenRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.changeStatus(maCode, request.getTrangThai())));
    }
}
