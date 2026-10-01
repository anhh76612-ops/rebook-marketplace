package com.nhom9.oldbook_marketplace.store.controller;

import com.nhom9.oldbook_marketplace.common.dto.ApiResponse;
import com.nhom9.oldbook_marketplace.store.dto.CreateCuaHangRequest;
import com.nhom9.oldbook_marketplace.store.dto.CuaHangResponse;
import com.nhom9.oldbook_marketplace.store.dto.UpdateCuaHangRequest;
import com.nhom9.oldbook_marketplace.store.service.StoreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/store")
@RequiredArgsConstructor
public class StoreController {
    private final StoreService storeService;

    @GetMapping("/me")
    public ApiResponse<CuaHangResponse> getMyStore() {
        return ApiResponse.success(storeService.getMyStore());
    }

    @PostMapping
    public ApiResponse<CuaHangResponse> create(@Valid @RequestBody CreateCuaHangRequest request) {
        return ApiResponse.success(storeService.create(request));
    }

    @PutMapping("/me")
    public ApiResponse<CuaHangResponse> update(@Valid @RequestBody UpdateCuaHangRequest request) {
        return ApiResponse.success(storeService.updateMyStore(request));
    }
}
