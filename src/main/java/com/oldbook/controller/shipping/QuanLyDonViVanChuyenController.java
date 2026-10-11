package com.oldbook.controller.shipping;

import com.oldbook.dto.common.ApiResponse;
import com.oldbook.dto.shipping.CapNhatDonViVanChuyenRequest;
import com.oldbook.dto.shipping.DanhSachDonViVanChuyenResponse;
import com.oldbook.dto.shipping.DoiTrangThaiDonViVanChuyenRequest;
import com.oldbook.dto.shipping.QuanLyDonViVanChuyenResponse;
import com.oldbook.dto.shipping.TaoDonViVanChuyenRequest;
import com.oldbook.service.shipping.QuanLyDonViVanChuyenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/management/shipping-carriers")
@RequiredArgsConstructor
public class QuanLyDonViVanChuyenController {

    private final QuanLyDonViVanChuyenService service;

    @GetMapping
    public ResponseEntity<ApiResponse<DanhSachDonViVanChuyenResponse>> getAll(
            @RequestParam(name = "tuKhoa", required = false) String tuKhoa,
            @RequestParam(name = "trangThai", required = false) String trangThai) {
        return ResponseEntity.ok(ApiResponse.success(service.getAll(tuKhoa, trangThai, baseUrl())));
    }

    @GetMapping("/{maCode}")
    public ResponseEntity<ApiResponse<QuanLyDonViVanChuyenResponse>> getDetail(@PathVariable("maCode") String maCode) {
        return ResponseEntity.ok(ApiResponse.success(service.getDetail(maCode, baseUrl())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<QuanLyDonViVanChuyenResponse>> create(
            @Valid @RequestBody TaoDonViVanChuyenRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.create(request, baseUrl())));
    }

    @PutMapping("/{maCode}")
    public ResponseEntity<ApiResponse<QuanLyDonViVanChuyenResponse>> update(
            @PathVariable("maCode") String maCode,
            @Valid @RequestBody CapNhatDonViVanChuyenRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.update(maCode, request, baseUrl())));
    }

    @PatchMapping("/{maCode}/status")
    public ResponseEntity<ApiResponse<QuanLyDonViVanChuyenResponse>> changeStatus(
            @PathVariable("maCode") String maCode,
            @Valid @RequestBody DoiTrangThaiDonViVanChuyenRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                service.changeStatus(maCode, request.getTrangThai(), request.getLyDo(), baseUrl())));
    }

    private static String baseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
