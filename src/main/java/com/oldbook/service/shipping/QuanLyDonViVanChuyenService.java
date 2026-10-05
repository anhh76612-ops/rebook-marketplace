package com.oldbook.service.shipping;

import com.oldbook.constant.shipping.TrangThaiDonViVanChuyen;
import com.oldbook.dto.shipping.CapNhatDonViVanChuyenRequest;
import com.oldbook.dto.shipping.DonViVanChuyenResponse;
import com.oldbook.dto.shipping.TaoDonViVanChuyenRequest;
import com.oldbook.entity.shipping.DonViVanChuyen;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.repository.shipping.DonViVanChuyenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Quản lý / quản trị viên thêm và sửa đối tác vận chuyển.
 * Tạo hãng với mã X là đủ: trang /shipping/X và API /api/shipping/X/... dùng được ngay (định tuyến theo {maCode}).
 */
@Service
@RequiredArgsConstructor
public class QuanLyDonViVanChuyenService {

    private final DonViVanChuyenRepository donViRepository;
    private final DonViVanChuyenResolver donViResolver;

    @Transactional(readOnly = true)
    public List<DonViVanChuyenResponse> getAll() {
        return donViRepository.findAllByOrderByMaDVVCAsc().stream()
                .map(DonViVanChuyenResponse::from)
                .toList();
    }

    @Transactional
    public DonViVanChuyenResponse create(TaoDonViVanChuyenRequest req) {
        String maCode = DonViVanChuyenResolver.normalize(req.getMaCode());
        if (maCode == null) {
            throw new BusinessException("Mã đơn vị vận chuyển chỉ gồm chữ, số, '-' hoặc '_' (2-30 ký tự)");
        }
        if (donViRepository.existsByMaCode(maCode)) {
            throw new BusinessException("Mã đơn vị vận chuyển đã tồn tại: " + maCode);
        }

        DonViVanChuyen dvvc = DonViVanChuyen.builder()
                .maCode(maCode)
                .trangThai(TrangThaiDonViVanChuyen.HOAT_DONG.name())
                .build();
        apply(dvvc, req);

        return DonViVanChuyenResponse.from(donViRepository.save(dvvc));
    }

    @Transactional
    public DonViVanChuyenResponse update(String maCode, CapNhatDonViVanChuyenRequest req) {
        DonViVanChuyen dvvc = donViResolver.require(maCode);
        apply(dvvc, req);
        return DonViVanChuyenResponse.from(donViRepository.save(dvvc));
    }

    /** Ngừng hoạt động: khách không chọn được ở checkout, trang/API của hãng không tiếp nhận/cập nhật đơn được nữa. */
    @Transactional
    public DonViVanChuyenResponse changeStatus(String maCode, String trangThai) {
        DonViVanChuyen dvvc = donViResolver.require(maCode);
        TrangThaiDonViVanChuyen status;
        try {
            status = TrangThaiDonViVanChuyen.valueOf(trangThai.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new BusinessException("Trạng thái chỉ gồm: HOAT_DONG hoặc NGUNG_HOAT_DONG");
        }
        dvvc.setTrangThai(status.name());
        return DonViVanChuyenResponse.from(donViRepository.save(dvvc));
    }

    private void apply(DonViVanChuyen dvvc, CapNhatDonViVanChuyenRequest req) {
        dvvc.setTenDonVi(req.getTenDonVi().trim());
        dvvc.setPhiVanChuyen(req.getPhiVanChuyen());
        dvvc.setThoiGianGiaoDuKien(req.getThoiGianGiaoDuKien());
        dvvc.setSoDienThoai(blankToNull(req.getSoDienThoai()));
        dvvc.setEmail(blankToNull(req.getEmail()));
        dvvc.setThongTinTichHop(blankToNull(req.getThongTinTichHop()));
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
