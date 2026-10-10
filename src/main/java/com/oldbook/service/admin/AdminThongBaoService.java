package com.oldbook.service.admin;

import com.oldbook.constant.auth.VaiTro;
import com.oldbook.constant.notification.MucDoThongBao;
import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.entity.identity.TaiKhoan;
import com.oldbook.repository.identity.TaiKhoanRepository;
import com.oldbook.service.notification.ThongBaoService;
import com.oldbook.service.notification.ThongBaoService.NoiDungThongBao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Quyết định sự kiện tài khoản nào thì báo cho Quản trị viên, và ở mức độ nào.
 *
 * Quản trị viên chỉ cần biết những thay đổi chạm tới quyền quản trị:
 *
 *   Sự kiện       | liên quan Quản trị viên | liên quan Quản lý | vai trò khác
 *   tạo tài khoản | QUAN_TRONG              | THONG_TIN         | không báo
 *   khóa          | NGHIEM_TRONG            | THONG_TIN         | không báo
 *   mở khóa       | QUAN_TRONG              | THONG_TIN         | không báo
 *   đổi vai trò   | QUAN_TRONG              | THONG_TIN         | không báo
 *
 * Với đổi vai trò, "liên quan" tính cả vai trò cũ lẫn vai trò mới.
 * Việc khóa hay đổi vai trò của khách hàng và chủ cửa hàng là vận hành thường ngày, vẫn xem được trong nhật ký.
 */
@Service
@RequiredArgsConstructor
public class AdminThongBaoService {

    private static final String QUAN_TRI_VIEN = VaiTro.QUAN_TRI_VIEN.name();
    private static final String QUAN_LY = VaiTro.QUAN_LY.name();

    private final ThongBaoService thongBaoService;
    private final TaiKhoanRepository taiKhoanRepository;

    @Transactional
    public void taiKhoanDuocTao(Integer maTKThucHien, TaiKhoan taiKhoan) {

        MucDoThongBao mucDo = mucDo(MucDoThongBao.QUAN_TRONG, taiKhoan.getVaiTro());
        if (mucDo == null) {
            return;
        }

        gui(maTKThucHien, taiKhoan, "TAO_TAI_KHOAN", mucDo,
                "Tài khoản " + tenVaiTro(taiKhoan.getVaiTro()) + " mới được tạo",
                " đã tạo tài khoản #" + taiKhoan.getMaTK() + " cho " + ten(taiKhoan.getNguoiDung()) + ".");
    }

    @Transactional
    public void taiKhoanBiKhoa(Integer maTKThucHien, TaiKhoan taiKhoan, String lyDo) {

        MucDoThongBao mucDo = mucDo(MucDoThongBao.NGHIEM_TRONG, taiKhoan.getVaiTro());
        if (mucDo == null) {
            return;
        }

        gui(maTKThucHien, taiKhoan, "KHOA_TAI_KHOAN", mucDo,
                "Tài khoản " + tenVaiTro(taiKhoan.getVaiTro()) + " bị khóa",
                " đã khóa tài khoản #" + taiKhoan.getMaTK() + " của " + ten(taiKhoan.getNguoiDung())
                        + ". Lý do: " + lyDo);
    }

    @Transactional
    public void taiKhoanDuocMoKhoa(Integer maTKThucHien, TaiKhoan taiKhoan) {

        MucDoThongBao mucDo = mucDo(MucDoThongBao.QUAN_TRONG, taiKhoan.getVaiTro());
        if (mucDo == null) {
            return;
        }

        gui(maTKThucHien, taiKhoan, "MO_KHOA_TAI_KHOAN", mucDo,
                "Tài khoản " + tenVaiTro(taiKhoan.getVaiTro()) + " được mở khóa",
                " đã mở khóa tài khoản #" + taiKhoan.getMaTK() + " của " + ten(taiKhoan.getNguoiDung()) + ".");
    }

    @Transactional
    public void vaiTroThayDoi(Integer maTKThucHien, TaiKhoan taiKhoan, String vaiTroCu, String vaiTroMoi) {

        MucDoThongBao mucDo = mucDo(MucDoThongBao.QUAN_TRONG, vaiTroCu, vaiTroMoi);
        if (mucDo == null) {
            return;
        }

        gui(maTKThucHien, taiKhoan, "THAY_DOI_VAI_TRO", mucDo,
                "Đổi vai trò: " + tenVaiTro(vaiTroCu) + " sang " + tenVaiTro(vaiTroMoi),
                " đã đổi vai trò tài khoản #" + taiKhoan.getMaTK() + " của " + ten(taiKhoan.getNguoiDung())
                        + " từ " + tenVaiTro(vaiTroCu) + " sang " + tenVaiTro(vaiTroMoi) + ".");
    }

    // null nghĩa là không cần báo.
    private MucDoThongBao mucDo(MucDoThongBao khiLienQuanQuanTriVien, String... vaiTroLienQuan) {
        boolean coQuanLy = false;
        for (String vaiTro : vaiTroLienQuan) {
            if (QUAN_TRI_VIEN.equals(vaiTro)) {
                return khiLienQuanQuanTriVien;
            }
            coQuanLy = coQuanLy || QUAN_LY.equals(vaiTro);
        }
        return coQuanLy ? MucDoThongBao.THONG_TIN : null;
    }

    private void gui(
            Integer maTKThucHien,
            TaiKhoan taiKhoan,
            String loai,
            MucDoThongBao mucDo,
            String tieuDe,
            String hanhDong
    ) {

        String nguoiThucHien = taiKhoanRepository.findById(maTKThucHien)
                .map(thucHien -> ten(thucHien.getNguoiDung()))
                .orElse("Một quản trị viên");

        // Trang Tài khoản & Vai trò tự lọc theo ?tuKhoa=, mở ra là thấy đúng tài khoản cần xem.
        String duongDan = "/admin/accounts?tuKhoa="
                + URLEncoder.encode(taiKhoan.getNguoiDung().getEmail(), StandardCharsets.UTF_8);

        thongBaoService.guiChoVaiTro(
                VaiTro.QUAN_TRI_VIEN,
                maTKThucHien,
                new NoiDungThongBao(loai, mucDo, tieuDe, nguoiThucHien + hanhDong, duongDan)
        );
    }

    private static String ten(NguoiDung nguoiDung) {
        String hoTen = nguoiDung.getHoTen();
        return hoTen == null || hoTen.isBlank() ? nguoiDung.getEmail() : hoTen;
    }

    private static String tenVaiTro(String vaiTro) {
        try {
            return VaiTro.valueOf(vaiTro).getTenHienThi();
        } catch (IllegalArgumentException | NullPointerException ex) {
            return String.valueOf(vaiTro);
        }
    }
}
