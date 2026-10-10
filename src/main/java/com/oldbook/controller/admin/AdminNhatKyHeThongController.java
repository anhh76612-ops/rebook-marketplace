package com.oldbook.controller.admin;

import com.oldbook.dto.admin.AdminNhatKyResponse;
import com.oldbook.dto.admin.AdminNhatKySummaryResponse;
import com.oldbook.dto.catalog.PageResponse;
import com.oldbook.dto.common.ApiResponse;
import com.oldbook.service.admin.AdminNhatKyHeThongService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/nhat-ky")
@RequiredArgsConstructor
public class AdminNhatKyHeThongController {

    private final AdminNhatKyHeThongService adminNhatKyHeThongService;


    /**
     * =========================================================
     * Danh sách nhật ký
     * =========================================================
     */
    @GetMapping
    public ApiResponse<PageResponse<AdminNhatKyResponse>> search(
            @RequestParam(required = false)
            String tuKhoa,

            @RequestParam(required = false)
            String hanhDong,

            @RequestParam(required = false)
            String vaiTro,

            @RequestParam(required = false)
            String diaChiIp,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate tuNgay,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate denNgay,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size
    ) {

        return ApiResponse.success(
                adminNhatKyHeThongService.search(
                        tuKhoa,
                        hanhDong,
                        vaiTro,
                        diaChiIp,
                        tuNgay,
                        denNgay,
                        page,
                        size
                )
        );
    }


    /**
     * =========================================================
     * KPI
     * =========================================================
     */
    @GetMapping("/summary")
    public ApiResponse<AdminNhatKySummaryResponse> summary() {

        return ApiResponse.success(
                adminNhatKyHeThongService.getSummary()
        );
    }


    /**
     * =========================================================
     * Xuất CSV
     * =========================================================
     */
    @GetMapping("/export")
    public ApiResponse<List<AdminNhatKyResponse>> export(
            @RequestParam(required = false)
            String tuKhoa,

            @RequestParam(required = false)
            String hanhDong,

            @RequestParam(required = false)
            String vaiTro,

            @RequestParam(required = false)
            String diaChiIp,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate tuNgay,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate denNgay
    ) {

        return ApiResponse.success(
                adminNhatKyHeThongService.export(
                        tuKhoa,
                        hanhDong,
                        vaiTro,
                        diaChiIp,
                        tuNgay,
                        denNgay
                )
        );
    }
}