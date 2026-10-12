package com.oldbook.controller.store;

import com.oldbook.dto.common.ApiResponse;
import com.oldbook.dto.payment.TaiKhoanNhanRequest;
import com.oldbook.dto.store.CreateCuaHangRequest;
import com.oldbook.dto.store.CuaHangResponse;
import com.oldbook.dto.store.DangKyGianHangResponse;
import com.oldbook.dto.store.UpdateCuaHangRequest;
import com.oldbook.service.store.StoreService;
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

    @PostMapping("/register")
    public ApiResponse<DangKyGianHangResponse> register(@Valid @RequestBody CreateCuaHangRequest request) {
        return ApiResponse.success(storeService.registerAsCustomer(request));
    }

    @PutMapping("/me")
    public ApiResponse<CuaHangResponse> update(@Valid @RequestBody UpdateCuaHangRequest request) {
        return ApiResponse.success(storeService.updateMyStore(request));
    }

    /** Tài khoản nhận tiền sàn chi; đổi được mà không phải chờ duyệt lại cửa hàng. */
    @PutMapping("/me/bank-account")
    public ApiResponse<CuaHangResponse> updateBankAccount(@Valid @RequestBody TaiKhoanNhanRequest request) {
        return ApiResponse.success(storeService.capNhatTaiKhoanNhanTien(request));
    }
}
