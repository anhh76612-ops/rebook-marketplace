// src/main/java/com/nhom9/oldbook_marketplace/identity/service/ProfileService.java

package com.nhom9.oldbook_marketplace.identity.service;

import com.nhom9.oldbook_marketplace.common.exception.BusinessException;
import com.nhom9.oldbook_marketplace.identity.dto.ProfileResponse;
import com.nhom9.oldbook_marketplace.identity.dto.UpdateProfileRequest;
import com.nhom9.oldbook_marketplace.identity.entity.NguoiDung;
import com.nhom9.oldbook_marketplace.identity.repository.NguoiDungRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final NguoiDungRepository nguoiDungRepository;

    @Transactional
    public ProfileResponse updateProfile(
            Integer maND,
            UpdateProfileRequest request
    ) {

        // 1. Tìm người dùng từ JWT
        NguoiDung nguoiDung = nguoiDungRepository.findById(maND)
                .orElseThrow(() ->
                        new BusinessException(
                                "Người dùng không tồn tại"
                        )
                );

        // 2. Chuẩn hóa họ tên
        String hoTen = request.getHoTen().trim();

        if (hoTen.isEmpty()) {
            throw new BusinessException(
                    "Họ tên không được để trống"
            );
        }

        // 3. Chuẩn hóa số điện thoại
        String soDienThoai = normalizePhone(
                request.getSoDienThoai()
        );

        // 4. Kiểm tra số điện thoại có thuộc người khác không
        if (soDienThoai != null
                && nguoiDungRepository.existsBySoDienThoaiAndMaNDNot(
                soDienThoai,
                maND
        )) {

            throw new BusinessException(
                    "Số điện thoại đã được sử dụng"
            );
        }

        // 5. Cập nhật thông tin
        nguoiDung.setHoTen(hoTen);
        nguoiDung.setSoDienThoai(soDienThoai);

        String avatarUrl = request.getAvatarUrl();

        if (avatarUrl != null) {
            avatarUrl = avatarUrl.trim();

            if (avatarUrl.isEmpty()) {
                avatarUrl = null;
            }
        }

        nguoiDung.setAvatarUrl(avatarUrl);

        // 6. Lưu
        nguoiDungRepository.save(nguoiDung);

        // 7. Trả dữ liệu sau khi cập nhật
        return toProfileResponse(nguoiDung);
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Integer maND) {

        NguoiDung nguoiDung = nguoiDungRepository.findById(maND)
                .orElseThrow(() ->
                        new BusinessException(
                                "Người dùng không tồn tại"
                        )
                );

        return toProfileResponse(nguoiDung);
    }

    private ProfileResponse toProfileResponse(
            NguoiDung nguoiDung
    ) {
        return ProfileResponse.builder()
                .maND(nguoiDung.getMaND())
                .email(nguoiDung.getEmail())
                .hoTen(nguoiDung.getHoTen())
                .soDienThoai(nguoiDung.getSoDienThoai())
                .avatarUrl(nguoiDung.getAvatarUrl())
                .build();
    }

    private String normalizePhone(String soDienThoai) {

        if (soDienThoai == null) {
            return null;
        }

        String phone = soDienThoai.trim();

        if (phone.isEmpty()) {
            return null;
        }

        return phone;
    }
}