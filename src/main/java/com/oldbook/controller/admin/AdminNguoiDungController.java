package com.oldbook.controller.admin;

import com.oldbook.dto.admin.AdminNguoiDungProfileResponse;
import com.oldbook.dto.common.ApiResponse;
import com.oldbook.service.admin.AdminNguoiDungService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/nguoi-dung")
@RequiredArgsConstructor
public class AdminNguoiDungController {

    private final AdminNguoiDungService adminNguoiDungService;

    @GetMapping("/{maND}")
    public ApiResponse<AdminNguoiDungProfileResponse> getProfile(
            @PathVariable Integer maND
    ) {
        return ApiResponse.success(
                adminNguoiDungService.getProfile(maND)
        );
    }
}
