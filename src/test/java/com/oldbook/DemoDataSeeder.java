package com.oldbook;

import com.oldbook.entity.catalog.CuaHang;
import com.oldbook.entity.catalog.DanhMuc;
import com.oldbook.entity.catalog.Sach;
import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.entity.identity.TaiKhoan;
import com.oldbook.repository.catalog.DanhMucRepository;
import com.oldbook.repository.catalog.SachRepository;
import com.oldbook.repository.identity.NguoiDungRepository;
import com.oldbook.repository.identity.TaiKhoanRepository;
import com.oldbook.repository.store.CuaHangRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dữ liệu mẫu để xem thử giao diện trên máy khi chạy ứng dụng bằng H2 (database rỗng mỗi lần khởi động).
 * Chỉ nằm trong mã test và chỉ chạy khi bật profile "demo":
 *
 *   ./mvnw -q spring-boot:test-run -Dspring-boot.run.profiles=test,catalog-test,demo
 *       "-Dspring-boot.run.arguments=--server.port=18080 --spring.config.import="
 *
 * Tài khoản mẫu (mật khẩu chung là MAT_KHAU bên dưới, chỉ có giá trị trong database H2 tạm này):
 *   admin@oldbook.test      Quản trị viên
 *   quanly@oldbook.test     Quản lý
 *   khach@oldbook.test      Khách hàng (chưa có gian hàng, dùng để thử đăng ký gian hàng)
 *   nguoiban@oldbook.test   Khách hàng + Chủ cửa hàng (gian hàng đã duyệt, có sách)
 */
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    private static final String MAT_KHAU = "Demo@12345";

    private final NguoiDungRepository nguoiDungRepository;
    private final TaiKhoanRepository taiKhoanRepository;
    private final CuaHangRepository cuaHangRepository;
    private final DanhMucRepository danhMucRepository;
    private final SachRepository sachRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (nguoiDungRepository.count() > 0) {
            return;
        }

        taiKhoan(nguoiDung("admin@oldbook.test", "Quản Trị Mẫu", "0500000001"), "QUAN_TRI_VIEN");
        taiKhoan(nguoiDung("quanly@oldbook.test", "Quản Lý Mẫu", "0500000002"), "QUAN_LY");
        taiKhoan(nguoiDung("khach@oldbook.test", "Nông Hoàng Anh", "0500000003"), "KHACH_HANG");

        NguoiDung nguoiBan = nguoiDung("nguoiban@oldbook.test", "Trần Thu Hà", "0500000004");
        taiKhoan(nguoiBan, "KHACH_HANG");
        taiKhoan(nguoiBan, "CHU_CUA_HANG");

        NguoiDung nguoiBanKhac = nguoiDung("nguoiban2@oldbook.test", "Lê Minh Quân", "0500000005");
        taiKhoan(nguoiBanKhac, "CHU_CUA_HANG");

        CuaHang dinhLe = cuaHang(nguoiBan, "Tiệm sách Đinh Lễ", "12 Đinh Lễ, Hoàn Kiếm, Hà Nội");
        CuaHang gocXep = cuaHang(nguoiBanKhac, "Góc Xép Books", "45 Trần Hưng Đạo, Quận 1, TP. Hồ Chí Minh");

        Map<String, DanhMuc> danhMuc = new LinkedHashMap<>();
        for (String ten : new String[]{"Văn học", "Kinh tế", "Lịch sử", "Kỹ năng", "Thiếu nhi", "Bộ sách"}) {
            danhMuc.put(ten, danhMucRepository.save(DanhMuc.builder().tenDanhMuc(ten).build()));
        }

        sach(dinhLe, danhMuc.get("Văn học"), "Dế Mèn Phiêu Lưu Ký", "Tô Hoài", 82000, 45000, 92, 6, "/images/hero-books.jpg");
        sach(dinhLe, danhMuc.get("Văn học"), "Cây Cam Ngọt Của Tôi", "José Mauro de Vasconcelos", 108000, 65000, 90, 3, null);
        sach(dinhLe, danhMuc.get("Văn học"), "Hoàng Tử Bé", "Antoine de Saint-Exupéry", 70000, 42000, 95, 9, "/images/hero-books1.jpg");
        sach(dinhLe, danhMuc.get("Thiếu nhi"), "Doraemon Truyện Ngắn Tập 1", "Fujiko F. Fujio", 25000, 15000, 92, 12, null);
        sach(dinhLe, danhMuc.get("Thiếu nhi"), "Thám Tử Lừng Danh Conan Tập 100", "Gosho Aoyama", 25000, 15000, 90, 2, null);
        sach(dinhLe, danhMuc.get("Lịch sử"), "Sử Việt Nam Từ Nguồn Gốc", "Lê Thành Khôi", 220000, 130000, 88, 4, "/images/hero-books2.jpg");
        sach(dinhLe, danhMuc.get("Bộ sách"), "Bộ Truyện Kiều Chú Giải (3 cuốn)", "Nguyễn Du", 360000, 215000, 85, 2, null);

        sach(gocXep, danhMuc.get("Kinh tế"), "Kinh Tế Học Hài Hước", "Steven D. Levitt", 149000, 85000, 92, 5, null);
        sach(gocXep, danhMuc.get("Kinh tế"), "Quốc Gia Khởi Nghiệp", "Dan Senor, Saul Singer", 165000, 95000, 95, 7, "/images/hero-books1.jpg");
        sach(gocXep, danhMuc.get("Kỹ năng"), "Tâm Lý Học Về Tiền", "Morgan Housel", 189000, 110000, 90, 8, null);
        sach(gocXep, danhMuc.get("Kỹ năng"), "Điều Kỳ Diệu Của Tiệm Tạp Hóa Namiya", "Higashino Keigo", 140000, 95000, 93, 0, null);
        sach(gocXep, danhMuc.get("Lịch sử"), "Súng, Vi Trùng Và Thép", "Jared Diamond", 255000, 145000, 97, 3, "/images/hero-books.jpg");
        sach(gocXep, danhMuc.get("Bộ sách"), "Bộ Homo Deus và Sapiens (2 cuốn)", "Yuval Noah Harari", 418000, 250000, 92, 1, null);
    }

    private NguoiDung nguoiDung(String email, String hoTen, String soDienThoai) {
        return nguoiDungRepository.save(NguoiDung.builder()
                .email(email)
                .matKhau(passwordEncoder.encode(MAT_KHAU))
                .hoTen(hoTen)
                .soDienThoai(soDienThoai)
                .build());
    }

    private void taiKhoan(NguoiDung nguoiDung, String vaiTro) {
        taiKhoanRepository.save(TaiKhoan.builder().nguoiDung(nguoiDung).vaiTro(vaiTro).trangThai("HOAT_DONG").build());
    }

    private CuaHang cuaHang(NguoiDung chuShop, String ten, String diaChi) {
        return cuaHangRepository.save(CuaHang.builder()
                .chuShop(chuShop)
                .tenCuaHang(ten)
                .moTa("Sách cũ tuyển chọn, ảnh chụp thật.")
                .diaChiCH(diaChi)
                .soDienThoaiCH(chuShop.getSoDienThoai())
                .trangThaiDuyet("DA_DUYET")
                .build());
    }

    private void sach(CuaHang cuaHang, DanhMuc danhMuc, String ten, String tacGia,
                      int giaBia, int giaBan, int doMoi, int ton, String hinhAnh) {
        sachRepository.save(Sach.builder()
                .cuaHang(cuaHang)
                .danhMuc(danhMuc)
                .tenSach(ten)
                .tacGia(tacGia)
                .giaBia(BigDecimal.valueOf(giaBia))
                .giaBanCu(BigDecimal.valueOf(giaBan))
                .doMoiPercent(BigDecimal.valueOf(doMoi))
                .tinhTrangVatLy("Bìa gọn, ruột sạch, không ghi chú")
                .soLuongTon(ton)
                .hinhAnhUrl(hinhAnh)
                .trangThaiDuyet("DA_DUYET")
                .trangThaiBan("DANG_BAN")
                .build());
    }
}
