package com.oldbook.service.notification;

import com.oldbook.constant.auth.TrangThaiTaiKhoan;
import com.oldbook.constant.auth.VaiTro;
import com.oldbook.constant.notification.MucDoThongBao;
import com.oldbook.entity.catalog.CuaHang;
import com.oldbook.entity.catalog.Sach;
import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.entity.identity.TaiKhoan;
import com.oldbook.entity.order.DonHangCuaHang;
import com.oldbook.repository.identity.TaiKhoanRepository;
import com.oldbook.service.notification.ThongBaoService.NoiDungThongBao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quyết định sự kiện nghiệp vụ nào thì báo cho ai, với khách hàng, chủ cửa hàng và quản lý.
 * (Thông báo cho quản trị viên có quy tắc riêng trong AdminThongBaoService.)
 *
 *   Sự kiện                            | Người nhận
 *   gian hàng đăng ký hoặc gửi duyệt lại | mọi Quản lý
 *   cửa hàng, sách được duyệt / bị từ chối | chủ cửa hàng đó
 *   đơn được xác nhận, đổi trạng thái giao | khách mua
 *   được thêm vai trò                     | các tài khoản đang có của người đó
 *
 * Các service khác chỉ gọi một dòng vào đây, không tự soạn nội dung thông báo.
 * Thông báo gắn với tài khoản: người có nhiều vai trò thấy thông báo của tài khoản đang dùng.
 */
@Service
@RequiredArgsConstructor
public class NghiepVuThongBaoService {

    private static final String TRANG_KIEM_DUYET = "/store-moderation";
    private static final String TRANG_CUA_HANG = "/store/books";
    private static final String TRANG_DON_HANG = "/orders";

    private final ThongBaoService thongBaoService;
    private final TaiKhoanRepository taiKhoanRepository;

    @Transactional
    public void gianHangChoDuyet(CuaHang cuaHang, boolean guiLai) {
        thongBaoService.guiChoVaiTro(VaiTro.QUAN_LY, null, new NoiDungThongBao(
                "GIAN_HANG_CHO_DUYET",
                MucDoThongBao.QUAN_TRONG,
                guiLai ? "Gian hàng gửi duyệt lại" : "Gian hàng mới chờ duyệt",
                "\"" + cuaHang.getTenCuaHang() + "\""
                        + (guiLai ? " vừa sửa thông tin và gửi duyệt lại." : " vừa đăng ký và đang chờ xét duyệt."),
                TRANG_KIEM_DUYET
        ));
    }

    @Transactional
    public void cuaHangDuocDuyet(CuaHang cuaHang) {
        guiChoChuCuaHang(cuaHang, new NoiDungThongBao(
                "CUA_HANG_DUOC_DUYET",
                MucDoThongBao.THONG_TIN,
                "Gian hàng đã được duyệt",
                "\"" + cuaHang.getTenCuaHang() + "\" đã được duyệt. Bạn có thể bắt đầu đăng sách.",
                TRANG_CUA_HANG
        ));
    }

    @Transactional
    public void cuaHangBiTuChoi(CuaHang cuaHang, String lyDo) {
        guiChoChuCuaHang(cuaHang, new NoiDungThongBao(
                "CUA_HANG_BI_TU_CHOI",
                MucDoThongBao.QUAN_TRONG,
                "Gian hàng chưa được duyệt",
                "\"" + cuaHang.getTenCuaHang() + "\" bị từ chối. Lý do: " + lyDo,
                TRANG_CUA_HANG
        ));
    }

    @Transactional
    public void sachDuocDuyet(Sach sach) {
        guiChoChuCuaHang(sach.getCuaHang(), new NoiDungThongBao(
                "SACH_DUOC_DUYET",
                MucDoThongBao.THONG_TIN,
                "Sách đã được duyệt",
                "\"" + sach.getTenSach() + "\" đã được duyệt và hiển thị cho người mua.",
                TRANG_CUA_HANG
        ));
    }

    @Transactional
    public void sachBiTuChoi(Sach sach, String lyDo) {
        guiChoChuCuaHang(sach.getCuaHang(), new NoiDungThongBao(
                "SACH_BI_TU_CHOI",
                MucDoThongBao.QUAN_TRONG,
                "Sách chưa được duyệt",
                "\"" + sach.getTenSach() + "\" bị từ chối. Lý do: " + lyDo,
                TRANG_CUA_HANG
        ));
    }

    @Transactional
    public void donHangDuocXacNhan(DonHangCuaHang donHang) {
        guiChoKhachMua(donHang, new NoiDungThongBao(
                "DON_HANG_DUOC_XAC_NHAN",
                MucDoThongBao.THONG_TIN,
                "Đơn #" + donHang.getMaDHCH() + " đã được xác nhận",
                tenCuaHang(donHang) + " đã xác nhận đơn và đang chờ đơn vị vận chuyển tới lấy hàng.",
                TRANG_DON_HANG
        ));
    }

    @Transactional
    public void donHangDoiTrangThaiGiao(DonHangCuaHang donHang, String tenTrangThai) {
        guiChoKhachMua(donHang, new NoiDungThongBao(
                "DON_HANG_DOI_TRANG_THAI",
                MucDoThongBao.THONG_TIN,
                "Đơn #" + donHang.getMaDHCH() + ": " + tenTrangThai,
                "Đơn hàng từ " + tenCuaHang(donHang) + " vừa chuyển sang trạng thái \"" + tenTrangThai + "\".",
                TRANG_DON_HANG
        ));
    }

    /**
     * Tài khoản mới chưa từng được đăng nhập, nên báo vào các tài khoản người đó đang dùng:
     * đó là nơi họ sẽ thấy và biết rằng mình có thể chuyển sang vai trò mới.
     */
    @Transactional
    public void vaiTroDuocThem(TaiKhoan taiKhoanMoi) {
        String tenVaiTro = VaiTro.valueOf(taiKhoanMoi.getVaiTro()).getTenHienThi();
        NoiDungThongBao noiDung = new NoiDungThongBao(
                "DUOC_THEM_VAI_TRO",
                MucDoThongBao.QUAN_TRONG,
                "Bạn có thêm vai trò " + tenVaiTro,
                "Quản trị viên vừa thêm vai trò " + tenVaiTro
                        + " cho bạn. Mở menu tài khoản để chuyển sang vai trò này.",
                null
        );

        taiKhoanRepository
                .findByNguoiDung_MaNDAndTrangThai(
                        taiKhoanMoi.getNguoiDung().getMaND(),
                        TrangThaiTaiKhoan.HOAT_DONG.name()
                )
                .stream()
                .filter(taiKhoan -> !taiKhoan.getMaTK().equals(taiKhoanMoi.getMaTK()))
                .forEach(taiKhoan -> thongBaoService.guiChoTaiKhoan(taiKhoan, noiDung));
    }

    private void guiChoChuCuaHang(CuaHang cuaHang, NoiDungThongBao noiDung) {
        if (cuaHang == null) {
            return;
        }
        guiChoTaiKhoanCua(cuaHang.getChuShop(), VaiTro.CHU_CUA_HANG, noiDung);
    }

    private void guiChoKhachMua(DonHangCuaHang donHang, NoiDungThongBao noiDung) {
        if (donHang.getDonHang() == null) {
            return;
        }
        guiChoTaiKhoanCua(donHang.getDonHang().getNguoiMua(), VaiTro.KHACH_HANG, noiDung);
    }

    // Người nhận không còn tài khoản hoạt động ở vai trò đó thì bỏ qua, không coi là lỗi.
    private void guiChoTaiKhoanCua(NguoiDung nguoiDung, VaiTro vaiTro, NoiDungThongBao noiDung) {
        if (nguoiDung == null) {
            return;
        }
        taiKhoanRepository
                .findByNguoiDung_MaNDAndVaiTro(nguoiDung.getMaND(), vaiTro.name())
                .filter(taiKhoan -> TrangThaiTaiKhoan.HOAT_DONG.name().equals(taiKhoan.getTrangThai()))
                .ifPresent(taiKhoan -> thongBaoService.guiChoTaiKhoan(taiKhoan, noiDung));
    }

    private static String tenCuaHang(DonHangCuaHang donHang) {
        return donHang.getCuaHang() == null ? "Cửa hàng" : donHang.getCuaHang().getTenCuaHang();
    }
}
