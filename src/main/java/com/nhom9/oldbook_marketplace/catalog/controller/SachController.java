package com.nhom9.oldbook_marketplace.catalog.controller;

import com.nhom9.oldbook_marketplace.common.dto.ApiResponse;
import com.nhom9.oldbook_marketplace.catalog.dto.DanhMucResponse;
import com.nhom9.oldbook_marketplace.catalog.dto.PageResponse;
import com.nhom9.oldbook_marketplace.catalog.dto.SachResponse;
import com.nhom9.oldbook_marketplace.catalog.dto.SachSearchRequest;
import com.nhom9.oldbook_marketplace.catalog.service.SachService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sach")
public class SachController {

    private final SachService sachService;

    public SachController(SachService sachService) {
        this.sachService = sachService;
    }

    @GetMapping // tìm kiếm và lọc sách
    public ApiResponse<PageResponse<SachResponse>> search(@ModelAttribute SachSearchRequest request) {
        return ApiResponse.success(sachService.search(request));
    }

    @GetMapping("/{maSach}")
    public ApiResponse<SachResponse> getDetail(@PathVariable Integer maSach) {
        return ApiResponse.success(sachService.getDetail(maSach));
    }

    @GetMapping("/danh-muc")
    public ApiResponse<List<DanhMucResponse>> getDanhMuc() {
        return ApiResponse.success(sachService.getDanhMuc());
    }
}
