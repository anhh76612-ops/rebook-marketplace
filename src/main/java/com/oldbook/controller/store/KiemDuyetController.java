package com.oldbook.controller.store;

import com.oldbook.dto.catalog.PageResponse;
import com.oldbook.dto.catalog.SachResponse;
import com.oldbook.dto.common.ApiResponse;
import com.oldbook.dto.store.CuaHangResponse;
import com.oldbook.dto.store.KiemDuyetSearchRequest;
import com.oldbook.dto.store.TuChoiRequest;
import com.oldbook.service.store.KiemDuyetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/store/kiem-duyet")
@RequiredArgsConstructor
public class KiemDuyetController {

    private final KiemDuyetService kiemDuyetService;

    @GetMapping("/cua-hang")
    public ApiResponse<PageResponse<CuaHangResponse>> cuaHang(
            @Valid @ModelAttribute KiemDuyetSearchRequest request) {
        return ApiResponse.success(kiemDuyetService.timCuaHang(request));
    }

    @GetMapping("/sach")
    public ApiResponse<PageResponse<SachResponse>> sach(
            @Valid @ModelAttribute KiemDuyetSearchRequest request) {
        return ApiResponse.success(kiemDuyetService.timSach(request));
    }

    @GetMapping("/cua-hang/{maCH}")
    public ApiResponse<CuaHangResponse> chiTietCuaHang(@PathVariable Integer maCH) {
        return ApiResponse.success(kiemDuyetService.chiTietCuaHang(maCH));
    }

    @GetMapping("/sach/{maSach}")
    public ApiResponse<SachResponse> chiTietSach(@PathVariable Integer maSach) {
        return ApiResponse.success(kiemDuyetService.chiTietSach(maSach));
    }

    @PostMapping("/cua-hang/{maCH}/duyet")
    public ApiResponse<CuaHangResponse> duyetCuaHang(@PathVariable Integer maCH) {
        return ApiResponse.success(kiemDuyetService.duyetCuaHang(maCH));
    }

    @PostMapping("/cua-hang/{maCH}/tu-choi")
    public ApiResponse<CuaHangResponse> tuChoiCuaHang(
            @PathVariable Integer maCH, @Valid @RequestBody TuChoiRequest request) {
        return ApiResponse.success(kiemDuyetService.tuChoiCuaHang(maCH, request));
    }

    @PostMapping("/sach/{maSach}/duyet")
    public ApiResponse<SachResponse> duyetSach(@PathVariable Integer maSach) {
        return ApiResponse.success(kiemDuyetService.duyetSach(maSach));
    }

    @PostMapping("/sach/{maSach}/tu-choi")
    public ApiResponse<SachResponse> tuChoiSach(
            @PathVariable Integer maSach, @Valid @RequestBody TuChoiRequest request) {
        return ApiResponse.success(kiemDuyetService.tuChoiSach(maSach, request));
    }
}
