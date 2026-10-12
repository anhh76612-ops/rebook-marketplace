package com.oldbook.service.shipping;

import com.oldbook.constant.shipping.TrangThaiDonViVanChuyen;
import com.oldbook.constant.shipping.TrangThaiVanChuyen;
import com.oldbook.dto.shipping.CapNhatDonViVanChuyenRequest;
import com.oldbook.dto.shipping.DanhSachDonViVanChuyenResponse;
import com.oldbook.dto.shipping.QuanLyDonViVanChuyenResponse;
import com.oldbook.dto.shipping.TaoDonViVanChuyenRequest;
import com.oldbook.dto.shipping.ThongKeDonViVanChuyenResponse;
import com.oldbook.entity.shipping.DonViVanChuyen;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.repository.shipping.DonViVanChuyenRepository;
import com.oldbook.repository.shipping.VanChuyenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class QuanLyDonViVanChuyenService {

    private static final List<String> TRANG_THAI_KET_THUC = List.of(
            TrangThaiVanChuyen.DA_GIAO.name(),
            TrangThaiVanChuyen.DA_HOAN_TRA.name(),
            TrangThaiVanChuyen.DA_HUY.name()
    );

    private final DonViVanChuyenRepository donViRepository;
    private final DonViVanChuyenResolver donViResolver;
    private final VanChuyenRepository vanChuyenRepository;

    @Transactional(readOnly = true)
    public DanhSachDonViVanChuyenResponse getAll(String tuKhoa, String trangThai, String baseUrl) {
        TrangThaiDonViVanChuyen locTrangThai = parseTrangThai(trangThai, true);
        String tuKhoaChuan = chuanHoaTimKiem(tuKhoa);

        List<DonViVanChuyen> tatCa = donViRepository.findAllByOrderByMaDVVCAsc();
        Map<Integer, Long> tongVanDon = toMap(vanChuyenRepository.demVanDonTheoDonVi());
        Map<Integer, Long> dangXuLy = toMap(vanChuyenRepository.demVanDonDangXuLyTheoDonVi(TRANG_THAI_KET_THUC));
        Map<Integer, Long> daGiao = toMap(vanChuyenRepository.demVanDonTheoDonViVaTrangThai(TrangThaiVanChuyen.DA_GIAO.name()));

        long soDangHopTac = tatCa.stream().filter(DonViVanChuyenResolver::isActive).count();
        ThongKeDonViVanChuyenResponse thongKe = ThongKeDonViVanChuyenResponse.builder()
                .tongDoiTac(tatCa.size())
                .dangHopTac(soDangHopTac)
                .tamDung(tatCa.size() - soDangHopTac)
                .vanDonDangXuLy(dangXuLy.values().stream().mapToLong(Long::longValue).sum())
                .build();

        List<QuanLyDonViVanChuyenResponse> danhSach = tatCa.stream()
                .filter(dvvc -> locTrangThai == null || locTrangThai.name().equalsIgnoreCase(dvvc.getTrangThai()))
                .filter(dvvc -> tuKhoaChuan == null || khopTuKhoa(dvvc, tuKhoaChuan))
                .map(dvvc -> QuanLyDonViVanChuyenResponse.from(dvvc, baseUrl,
                        tongVanDon.getOrDefault(dvvc.getMaDVVC(), 0L),
                        dangXuLy.getOrDefault(dvvc.getMaDVVC(), 0L),
                        daGiao.getOrDefault(dvvc.getMaDVVC(), 0L)))
                .toList();

        return DanhSachDonViVanChuyenResponse.builder()
                .thongKe(thongKe)
                .danhSach(danhSach)
                .build();
    }

    @Transactional(readOnly = true)
    public QuanLyDonViVanChuyenResponse getDetail(String maCode, String baseUrl) {
        return toResponse(donViResolver.require(maCode), baseUrl);
    }

    @Transactional
    public QuanLyDonViVanChuyenResponse create(TaoDonViVanChuyenRequest req, String baseUrl) {
        String maCode = DonViVanChuyenResolver.normalize(req.getMaCode());
        if (maCode == null) {
            throw new BusinessException("Mã đơn vị vận chuyển chỉ gồm chữ, số, '-' hoặc '_' (2-30 ký tự)");
        }
        if (donViRepository.existsByMaCode(maCode)) {
            throw new BusinessException("Mã đơn vị vận chuyển đã tồn tại: " + maCode);
        }
        String tenDonVi = req.getTenDonVi().trim();
        kiemTraTrungTen(tenDonVi, null);

        DonViVanChuyen dvvc = DonViVanChuyen.builder()
                .maCode(maCode)
                .trangThai(TrangThaiDonViVanChuyen.HOAT_DONG.name())
                .ngayCapNhatTrangThai(LocalDateTime.now())
                .build();
        apply(dvvc, req);

        return toResponse(donViRepository.save(dvvc), baseUrl);
    }

    @Transactional
    public QuanLyDonViVanChuyenResponse update(String maCode, CapNhatDonViVanChuyenRequest req, String baseUrl) {
        DonViVanChuyen dvvc = donViResolver.require(maCode);
        kiemTraTrungTen(req.getTenDonVi().trim(), dvvc.getMaDVVC());
        apply(dvvc, req);
        return toResponse(donViRepository.save(dvvc), baseUrl);
    }

    @Transactional
    public QuanLyDonViVanChuyenResponse changeStatus(String maCode, String trangThai, String lyDo, String baseUrl) {
        DonViVanChuyen dvvc = donViResolver.require(maCode);
        TrangThaiDonViVanChuyen moi = parseTrangThai(trangThai, false);

        if (moi.name().equalsIgnoreCase(dvvc.getTrangThai())) {
            throw new BusinessException(moi == TrangThaiDonViVanChuyen.HOAT_DONG
                    ? "Đơn vị vận chuyển đang hợp tác"
                    : "Đơn vị vận chuyển đã tạm dừng hợp tác");
        }

        if (moi == TrangThaiDonViVanChuyen.NGUNG_HOAT_DONG) {
            String lyDoChuan = blankToNull(lyDo);
            if (lyDoChuan == null) {
                throw new BusinessException("Vui lòng nhập lý do tạm dừng hợp tác");
            }
            dvvc.setLyDoTamDung(lyDoChuan);
        } else {
            dvvc.setLyDoTamDung(null);
        }

        dvvc.setTrangThai(moi.name());
        dvvc.setNgayCapNhatTrangThai(LocalDateTime.now());
        return toResponse(donViRepository.save(dvvc), baseUrl);
    }

    private QuanLyDonViVanChuyenResponse toResponse(DonViVanChuyen dvvc, String baseUrl) {
        Integer id = dvvc.getMaDVVC();
        if (id == null) {
            return QuanLyDonViVanChuyenResponse.from(dvvc, baseUrl, 0, 0, 0);
        }
        return QuanLyDonViVanChuyenResponse.from(dvvc, baseUrl,
                vanChuyenRepository.countByDonViVanChuyen_MaDVVC(id),
                vanChuyenRepository.countByDonViVanChuyen_MaDVVCAndTrangThaiVanChuyenNotIn(id, TRANG_THAI_KET_THUC),
                vanChuyenRepository.countByDonViVanChuyen_MaDVVCAndTrangThaiVanChuyen(id, TrangThaiVanChuyen.DA_GIAO.name()));
    }

    private void kiemTraTrungTen(String tenDonVi, Integer boQuaMaDVVC) {
        String tenChuan = chuanHoaTimKiem(tenDonVi);
        boolean trung = donViRepository.findAll().stream()
                .filter(dvvc -> !Objects.equals(dvvc.getMaDVVC(), boQuaMaDVVC))
                .anyMatch(dvvc -> Objects.equals(chuanHoaTimKiem(dvvc.getTenDonVi()), tenChuan));
        if (trung) {
            throw new BusinessException("Tên đơn vị vận chuyển đã tồn tại: " + tenDonVi);
        }
    }

    private void apply(DonViVanChuyen dvvc, CapNhatDonViVanChuyenRequest req) {
        dvvc.setTenDonVi(req.getTenDonVi().trim());
        dvvc.setPhiVanChuyen(req.getPhiVanChuyen());
        dvvc.setThoiGianGiaoDuKien(req.getThoiGianGiaoDuKien());
        dvvc.setNguoiLienHe(blankToNull(req.getNguoiLienHe()));
        dvvc.setSoDienThoai(blankToNull(req.getSoDienThoai()));
        dvvc.setEmail(blankToNull(req.getEmail()));
        dvvc.setDiaChi(blankToNull(req.getDiaChi()));
        dvvc.setWebsite(blankToNull(req.getWebsite()));
        dvvc.setGhiChu(blankToNull(req.getGhiChu()));
        dvvc.setThongTinTichHop(blankToNull(req.getThongTinTichHop()));
    }

    private static TrangThaiDonViVanChuyen parseTrangThai(String trangThai, boolean choPhepRong) {
        if (trangThai == null || trangThai.isBlank()) {
            if (choPhepRong) {
                return null;
            }
            throw new BusinessException("Trạng thái không được để trống");
        }
        try {
            return TrangThaiDonViVanChuyen.valueOf(trangThai.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Trạng thái chỉ gồm: HOAT_DONG hoặc NGUNG_HOAT_DONG");
        }
    }

    private static boolean khopTuKhoa(DonViVanChuyen dvvc, String tuKhoa) {
        return Stream.of(dvvc.getMaCode(), dvvc.getTenDonVi(), dvvc.getNguoiLienHe(),
                        dvvc.getSoDienThoai(), dvvc.getEmail())
                .map(QuanLyDonViVanChuyenService::chuanHoaTimKiem)
                .filter(Objects::nonNull)
                .anyMatch(giaTri -> giaTri.contains(tuKhoa));
    }

    private static String chuanHoaTimKiem(String value) {
        String v = blankToNull(value);
        if (v == null) {
            return null;
        }
        String khongDau = Normalizer.normalize(v, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');
        return khongDau.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static Map<Integer, Long> toMap(List<Object[]> rows) {
        Map<Integer, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] != null && row[1] != null) {
                result.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
            }
        }
        return result;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
