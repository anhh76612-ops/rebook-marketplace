package com.oldbook.controller.catalog;

import com.oldbook.dto.catalog.SachResponse;
import com.oldbook.service.catalog.SachService;
import com.oldbook.dto.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public ApiResponse<List<SachResponse>> getAll(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category
    ) {
        return ApiResponse.success(sachService.timSachCongKhai(q, category));
    }
}
