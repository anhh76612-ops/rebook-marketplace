package com.oldbook.service.admin;

import com.oldbook.dto.admin.AdminNguoiDungProfileResponse;
import com.oldbook.dto.admin.AdminNhatKyResponse;
import com.oldbook.dto.identity.AdminTaiKhoanResponse;
import com.oldbook.dto.identity.DiaChiResponse;
import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.entity.identity.TaiKhoan;
import com.oldbook.entity.system.NhatKyHeThong;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.repository.identity.DiaChiRepository;
import com.oldbook.repository.identity.NguoiDungRepository;
import com.oldbook.repository.identity.TaiKhoanRepository;
import com.oldbook.repository.store.CuaHangRepository;
import com.oldbook.repository.system.NhatKyHeThongRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminNguoiDungService {

    private final NguoiDungRepository nguoiDungRepository;
    private final TaiKhoanRepository taiKhoanRepository;
    private final DiaChiRepository diaChiRepository;
    private final CuaHangRepository cuaHangRepository;
    private final NhatKyHeThongRepository nhatKyHeThongRepository;

    @Transactional(readOnly = true)
    public AdminNguoiDungProfileResponse getProfile(Integer maND) {

        validateMaND(maND);

        NguoiDung nguoiDung = nguoiDungRepository.findById(maND)
                .orElseThrow(() -> new BusinessException("Người dùng không tồn tại"));

        return AdminNguoiDungProfileResponse.builder()
                .maND(nguoiDung.getMaND())
                .email(nguoiDung.getEmail())
                .hoTen(nguoiDung.getHoTen())
                .soDienThoai(nguoiDung.getSoDienThoai())
                .avatarUrl(nguoiDung.getAvatarUrl())
                .tieuSu(nguoiDung.getTieuSu())
                .ngayTao(nguoiDung.getNgayTao())
                .taiKhoans(taiKhoanRepository.findByNguoiDung_MaND(maND)
                        .stream()
                        .map(this::toTaiKhoanResponse)
                        .toList())
                .diaChis(diaChiRepository
                        .findAllByNguoiDung_MaNDOrderByLaMacDinhDescMaDiaChiDesc(maND)
                        .stream()
                        .map(this::toDiaChiResponse)
                        .toList())
                .cuaHang(cuaHangRepository.findByChuShop_MaND(maND)
                        .map(com.oldbook.dto.store.CuaHangResponse::from)
                        .orElse(null))
                .hoatDongGanDay(nhatKyHeThongRepository
                        .findTop20ByTaiKhoan_NguoiDung_MaNDOrderByThoiGianDescMaNhatKyDesc(maND)
                        .stream()
                        .map(this::toNhatKyResponse)
                        .toList())
                .build();
    }

    private AdminTaiKhoanResponse toTaiKhoanResponse(TaiKhoan taiKhoan) {
        NguoiDung nguoiDung = taiKhoan.getNguoiDung();

        return AdminTaiKhoanResponse.builder()
                .maTK(taiKhoan.getMaTK())
                .maND(nguoiDung.getMaND())
                .email(nguoiDung.getEmail())
                .hoTen(nguoiDung.getHoTen())
                .soDienThoai(nguoiDung.getSoDienThoai())
                .vaiTro(taiKhoan.getVaiTro())
                .trangThai(taiKhoan.getTrangThai())
                .ngayTao(taiKhoan.getNgayTao())
                .build();
    }

    private DiaChiResponse toDiaChiResponse(com.oldbook.entity.identity.DiaChi diaChi) {
        return DiaChiResponse.builder()
                .maDiaChi(diaChi.getMaDiaChi())
                .tenNguoiNhan(diaChi.getTenNguoiNhan())
                .soDienThoai(diaChi.getSoDienThoai())
                .tinhThanh(diaChi.getTinhThanh())
                .quanHuyen(diaChi.getQuanHuyen())
                .phuongXa(diaChi.getPhuongXa())
                .diaChiChiTiet(diaChi.getDiaChiChiTiet())
                .laMacDinh(diaChi.getLaMacDinh())
                .build();
    }

    private AdminNhatKyResponse toNhatKyResponse(NhatKyHeThong log) {
        String vaiTro = log.getTaiKhoan() == null
                ? null
                : log.getTaiKhoan().getVaiTro();

        Integer maTK = log.getTaiKhoan() == null
                ? null
                : log.getTaiKhoan().getMaTK();

        return AdminNhatKyResponse.builder()
                .maNhatKy(log.getMaNhatKy())
                .maTK(maTK)
                .vaiTro(vaiTro)
                .hanhDong(log.getHanhDong())
                .loaiDoiTuong(log.getLoaiDoiTuong())
                .maDoiTuong(log.getMaDoiTuong())
                .moTa(log.getMoTa())
                .diaChiIp(log.getDiaChiIp())
                .thoiGian(log.getThoiGian())
                .build();
    }

    private void validateMaND(Integer maND) {
        if (maND == null || maND <= 0) {
            throw new BusinessException("Mã người dùng không hợp lệ");
        }
    }
}
