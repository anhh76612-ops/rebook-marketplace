package com.oldbook.controller.identity;

import com.oldbook.dto.common.ApiResponse;
import com.oldbook.dto.identity.DiaChiRequest;
import com.oldbook.dto.identity.DiaChiResponse;
import com.oldbook.service.identity.DiaChiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/profile/addresses")
@RequiredArgsConstructor
public class DiaChiController {

    private final DiaChiService diaChiService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DiaChiResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(diaChiService.getAll(getMaND())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DiaChiResponse>> create(
            @Valid @RequestBody DiaChiRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(diaChiService.create(getMaND(), request))
        );
    }

    @PutMapping("/{maDiaChi}")
    public ResponseEntity<ApiResponse<DiaChiResponse>> update(
            @PathVariable Integer maDiaChi,
            @Valid @RequestBody DiaChiRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        diaChiService.update(getMaND(), maDiaChi, request)
                )
        );
    }

    @PatchMapping("/{maDiaChi}/default")
    public ResponseEntity<ApiResponse<DiaChiResponse>> setDefault(
            @PathVariable Integer maDiaChi
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        diaChiService.setDefault(getMaND(), maDiaChi)
                )
        );
    }

    @DeleteMapping("/{maDiaChi}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Integer maDiaChi
    ) {
        diaChiService.delete(getMaND(), maDiaChi);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa địa chỉ"));
    }

    private Integer getMaND() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        return (Integer) authentication.getPrincipal();
    }
}
