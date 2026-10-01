package com.oldbook.controller.identity;

import com.oldbook.dto.common.ApiResponse;
import com.oldbook.dto.identity.CreateAddressRequest;
import com.oldbook.dto.identity.DiaChiResponse;
import com.oldbook.entity.identity.DiaChi;
import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.filter.auth.JwtAuthenticationFilter.AuthenticatedUserDetails;
import com.oldbook.repository.identity.DiaChiRepository;
import com.oldbook.repository.identity.NguoiDungRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/profile/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final DiaChiRepository diaChiRepository;
    private final NguoiDungRepository nguoiDungRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DiaChiResponse>>> getMyAddresses() {
        Integer maND = getMaNDKhachHang();

        List<DiaChiResponse> addresses = diaChiRepository
                .findAllByNguoiDung_MaNDOrderByLaMacDinhDescMaDiaChiAsc(maND)
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(addresses));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<DiaChiResponse>> createAddress(
            @Valid @RequestBody CreateAddressRequest request
    ) {
        Integer maND = getMaNDKhachHang();

        NguoiDung nguoiDung = nguoiDungRepository.findById(maND)
                .orElseThrow(() -> new BusinessException("Không tìm thấy người dùng"));

        List<DiaChi> existing = diaChiRepository
                .findAllByNguoiDung_MaNDOrderByLaMacDinhDescMaDiaChiAsc(maND);

        // Địa chỉ đầu tiên luôn là mặc định
        boolean makeDefault = existing.isEmpty() || Boolean.TRUE.equals(request.getLaMacDinh());

        if (makeDefault) {
            existing.stream()
                    .filter(a -> Boolean.TRUE.equals(a.getLaMacDinh()))
                    .forEach(a -> a.setLaMacDinh(false));
            diaChiRepository.saveAll(existing);
        }

        DiaChi saved = diaChiRepository.save(DiaChi.builder()
                .nguoiDung(nguoiDung)
                .tenNguoiNhan(request.getTenNguoiNhan().trim())
                .soDienThoai(request.getSoDienThoai().trim())
                .tinhThanh(request.getTinhThanh().trim())
                .quanHuyen(request.getQuanHuyen().trim())
                .phuongXa(request.getPhuongXa().trim())
                .diaChiChiTiet(request.getDiaChiChiTiet().trim())
                .laMacDinh(makeDefault)
                .build());

        return ResponseEntity.ok(ApiResponse.success(toResponse(saved)));
    }

    private DiaChiResponse toResponse(DiaChi d) {
        return DiaChiResponse.builder()
                .maDiaChi(d.getMaDiaChi())
                .tenNguoiNhan(d.getTenNguoiNhan())
                .soDienThoai(d.getSoDienThoai())
                .tinhThanh(d.getTinhThanh())
                .quanHuyen(d.getQuanHuyen())
                .phuongXa(d.getPhuongXa())
                .diaChiChiTiet(d.getDiaChiChiTiet())
                .laMacDinh(Boolean.TRUE.equals(d.getLaMacDinh()))
                .build();
    }

    private Integer getMaNDKhachHang() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getDetails() instanceof AuthenticatedUserDetails user)) {
            throw new IllegalStateException("Thông tin xác thực không hợp lệ");
        }
        if (!"KHACH_HANG".equals(user.vaiTro())) {
            throw new AccessDeniedException("Chỉ khách hàng mới được quản lý địa chỉ giao hàng");
        }
        return user.maND();
    }
}