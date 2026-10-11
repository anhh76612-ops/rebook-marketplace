package com.oldbook.controller.catalog;

import com.oldbook.constant.auth.VaiTro;
import com.oldbook.dto.catalog.CreateDanhGiaRequest;
import com.oldbook.dto.catalog.DanhGiaResponse;
import com.oldbook.dto.common.ApiResponse;
import com.oldbook.filter.auth.JwtAuthenticationFilter.AuthenticatedUserDetails;
import com.oldbook.service.catalog.DanhGiaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class DanhGiaController {

    private final DanhGiaService danhGiaService;

    @PostMapping(value = "/order-items/{maCTDH}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<DanhGiaResponse>> createReview(
            @PathVariable Integer maCTDH,
            @Valid @RequestBody CreateDanhGiaRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                danhGiaService.createReview(getMaNDKhachHang(), maCTDH, request)));
    }

    @PostMapping(value = "/order-items/{maCTDH}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DanhGiaResponse>> createReviewWithMedia(
            @PathVariable Integer maCTDH,
            @Valid @ModelAttribute CreateDanhGiaRequest request,
            @RequestParam(name = "files", required = false) List<MultipartFile> files
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                danhGiaService.createReview(getMaNDKhachHang(), maCTDH, request, files)));
    }

    private Integer getMaNDKhachHang() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof AuthenticatedUserDetails user)
                || user.maND() == null || !VaiTro.KHACH_HANG.name().equals(user.vaiTro())) {
            throw new AccessDeniedException("Chỉ khách hàng mới được đánh giá sách đã mua");
        }
        return user.maND();
    }
}
