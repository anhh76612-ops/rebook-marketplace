package com.oldbook.controller.store;

import com.oldbook.dto.catalog.PageResponse;
import com.oldbook.dto.catalog.SachResponse;
import com.oldbook.dto.common.ApiResponse;
import com.oldbook.dto.store.CreateSachRequest;
import com.oldbook.dto.store.UpdateSachRequest;
import com.oldbook.dto.store.UpdateTonKhoRequest;
import com.oldbook.dto.store.UpdateTrangThaiBanRequest;
import com.oldbook.service.store.StoreSachService;
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
