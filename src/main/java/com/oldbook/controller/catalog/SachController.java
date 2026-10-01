package com.oldbook.controller.catalog;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.oldbook.dto.common.ApiResponse;
import com.oldbook.entity.catalog.Sach;
import com.oldbook.service.catalog.SachService;

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