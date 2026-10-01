package com.nhom9.oldbook_marketplace.store.service;

import com.nhom9.oldbook_marketplace.catalog.constant.TrangThaiDuyet;
import com.nhom9.oldbook_marketplace.catalog.entity.CuaHang;
import com.nhom9.oldbook_marketplace.common.exception.BusinessException;
import com.nhom9.oldbook_marketplace.identity.entity.NguoiDung;
import com.nhom9.oldbook_marketplace.identity.repository.NguoiDungRepository;
import com.nhom9.oldbook_marketplace.store.dto.CreateCuaHangRequest;
import com.nhom9.oldbook_marketplace.store.dto.CuaHangResponse;
import com.nhom9.oldbook_marketplace.store.dto.UpdateCuaHangRequest;
import com.nhom9.oldbook_marketplace.store.repository.CuaHangRepository;
import com.nhom9.oldbook_marketplace.store.security.StoreAccess;
import com.nhom9.oldbook_marketplace.system.repository.NhatKyHeThongRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoreService {
    private final CuaHangRepository cuaHangRepository;
    private final NguoiDungRepository nguoiDungRepository;
    private final StoreAccess storeAccess;
    private final NhatKyHeThongRepository nhatKyHeThongRepository;

    @Transactional(readOnly = true)
    public CuaHangResponse getMyStore() {
        Integer maND = storeAccess.requireOwner();
        CuaHang cuaHang = cuaHangRepository.findByChuShop_MaND(maND)
                .orElseThrow(() -> new BusinessException("Bạn chưa đăng ký cửa hàng"));
        CuaHangResponse response = CuaHangResponse.from(cuaHang);
        if (TrangThaiDuyet.TU_CHOI.name().equals(cuaHang.getTrangThaiDuyet())) {
            return nhatKyHeThongRepository
                    .findFirstByLoaiDoiTuongAndMaDoiTuongAndHanhDongOrderByThoiGianDescMaNhatKyDesc(
                            "CUA_HANG", cuaHang.getMaCH(), "TU_CHOI_CUA_HANG")
                    .map(log -> response.withLyDoTuChoi(log.getMoTa()))
                    .orElse(response);
        }
        return response;
    }

    @Transactional
    public CuaHangResponse create(CreateCuaHangRequest request) {
        Integer maND = storeAccess.requireOwner();
        if (cuaHangRepository.findByChuShop_MaND(maND).isPresent()) {
            throw new BusinessException("Bạn đã có cửa hàng");
        }
        String ten = requireText(request.tenCuaHang(), "Tên cửa hàng", 255);
        if (cuaHangRepository.existsByTenCuaHangIgnoreCase(ten)) {
            throw new BusinessException("Tên cửa hàng đã được sử dụng");
        }
        NguoiDung owner = nguoiDungRepository.findById(maND)
                .orElseThrow(() -> new BusinessException("Người dùng không tồn tại"));
        CuaHang cuaHang = CuaHang.builder()
                .chuShop(owner)
                .tenCuaHang(ten)
                .moTa(optionalText(request.moTa(), "Mô tả", 1000))
                .diaChiCH(requireText(request.diaChiCH(), "Địa chỉ cửa hàng", 500))
                .soDienThoaiCH(requireText(request.soDienThoaiCH(), "Số điện thoại cửa hàng", 20))
                .trangThaiDuyet(TrangThaiDuyet.CHO_DUYET.name())
                .build();
        return CuaHangResponse.from(cuaHangRepository.save(cuaHang));
    }

    @Transactional
    public CuaHangResponse updateMyStore(UpdateCuaHangRequest request) {
        Integer maND = storeAccess.requireOwner();
        CuaHang cuaHang = cuaHangRepository.findByChuShopMaNDForUpdate(maND)
                .orElseThrow(() -> new BusinessException("Bạn chưa đăng ký cửa hàng"));
        String ten = requireText(request.tenCuaHang(), "Tên cửa hàng", 255);
        if (cuaHangRepository.existsByTenCuaHangIgnoreCaseAndMaCHNot(ten, cuaHang.getMaCH())) {
            throw new BusinessException("Tên cửa hàng đã được sử dụng");
        }
        cuaHang.setTenCuaHang(ten);
        cuaHang.setMoTa(optionalText(request.moTa(), "Mô tả", 1000));
        cuaHang.setDiaChiCH(requireText(request.diaChiCH(), "Địa chỉ cửa hàng", 500));
        cuaHang.setSoDienThoaiCH(requireText(request.soDienThoaiCH(), "Số điện thoại cửa hàng", 20));
        cuaHang.setTrangThaiDuyet(TrangThaiDuyet.CHO_DUYET.name());
        return CuaHangResponse.from(cuaHangRepository.save(cuaHang));
    }

    private String requireText(String value, String field, int maxLength) {
        String normalized = optionalText(value, field, maxLength);
        if (normalized == null) {
            throw new BusinessException(field + " không được để trống");
        }
        return normalized;
    }

    private String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BusinessException(field + " vượt quá " + maxLength + " ký tự");
        }
        return normalized;
    }
}
