package com.nhom9.oldbook_marketplace.controller;

import com.nhom9.oldbook_marketplace.dto.ApiResponse;
import com.nhom9.oldbook_marketplace.entity.catalog.Sach;
import com.nhom9.oldbook_marketplace.service.SachService;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping
    public ApiResponse<List<Sach>> getAll() {
        return ApiResponse.success(sachService.getAll());
    }
}