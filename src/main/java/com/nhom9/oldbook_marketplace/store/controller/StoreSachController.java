package com.nhom9.oldbook_marketplace.store.controller;

import com.nhom9.oldbook_marketplace.catalog.dto.PageResponse;
import com.nhom9.oldbook_marketplace.catalog.dto.SachResponse;
import com.nhom9.oldbook_marketplace.common.dto.ApiResponse;
import com.nhom9.oldbook_marketplace.store.dto.CreateSachRequest;
import com.nhom9.oldbook_marketplace.store.dto.UpdateSachRequest;
import com.nhom9.oldbook_marketplace.store.dto.UpdateTonKhoRequest;
import com.nhom9.oldbook_marketplace.store.dto.UpdateTrangThaiBanRequest;
import com.nhom9.oldbook_marketplace.store.service.StoreSachService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/store/me/sach")
@RequiredArgsConstructor
public class StoreSachController {
    private final StoreSachService storeSachService;

    @GetMapping
    public ApiResponse<PageResponse<SachResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "ngayTao,desc") String sort,
            @RequestParam(required = false) String trangThaiDuyet) {
        return ApiResponse.success(storeSachService.listMyBooks(page, size, sort, trangThaiDuyet));
    }

    @GetMapping("/{maSach}")
    public ApiResponse<SachResponse> get(@PathVariable Integer maSach) {
        return ApiResponse.success(storeSachService.getMyBook(maSach));
    }

    @PostMapping
    public ApiResponse<SachResponse> create(@Valid @RequestBody CreateSachRequest request) {
        return ApiResponse.success(storeSachService.create(request));
    }

    @PutMapping("/{maSach}")
    public ApiResponse<SachResponse> update(@PathVariable Integer maSach,
            @Valid @RequestBody UpdateSachRequest request) {
        return ApiResponse.success(storeSachService.update(maSach, request));
    }

    @PatchMapping("/{maSach}/ton-kho")
    public ApiResponse<SachResponse> updateStock(@PathVariable Integer maSach,
            @Valid @RequestBody UpdateTonKhoRequest request) {
        return ApiResponse.success(storeSachService.updateStock(maSach, request));
    }

    @PatchMapping("/{maSach}/trang-thai-ban")
    public ApiResponse<SachResponse> updateSaleStatus(@PathVariable Integer maSach,
            @Valid @RequestBody UpdateTrangThaiBanRequest request) {
        return ApiResponse.success(storeSachService.updateSaleStatus(maSach, request));
    }
}
