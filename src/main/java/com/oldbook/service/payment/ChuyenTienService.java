package com.oldbook.service.payment;

import com.oldbook.constant.payment.KetQuaDoiSoat;
import com.oldbook.constant.payment.LoaiChuyenTien;
import com.oldbook.constant.payment.TrangThaiChuyenTien;
import com.oldbook.dto.catalog.PageResponse;
import com.oldbook.dto.management.QuanLyDto.GiaoDichTrang;
import com.oldbook.dto.management.QuanLyDto.TomTatGiaoDich;
import com.oldbook.dto.payment.GiaoDichNganHangResponse;
import com.oldbook.dto.payment.TaiKhoanNhanRequest;
import com.oldbook.dto.payment.YeuCauChuyenTienResponse;
import com.oldbook.entity.catalog.CuaHang;
import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.entity.order.DonHangCuaHang;
import com.oldbook.entity.payment.GiaoDichNganHang;
import com.oldbook.entity.payment.PhienThanhToan;
import com.oldbook.entity.payment.ThanhToan;
import com.oldbook.entity.payment.YeuCauChuyenTien;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.repository.identity.TaiKhoanRepository;
import com.oldbook.repository.payment.GiaoDichNganHangRepository;
import com.oldbook.repository.payment.PhienThanhToanRepository;
import com.oldbook.repository.payment.ThanhToanRepository;
import com.oldbook.repository.payment.YeuCauChuyenTienRepository;
import com.oldbook.service.management.VanHanhThayDoiEvent;
import com.oldbook.service.notification.NghiepVuThongBaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Các khoản tiền sàn phải chuyển đi cho đơn đã thanh toán trước: hoàn cho khách và chi cho cửa hàng.
 *
 *   Sự kiện                                  | Khoản lập ra                         | Trạng thái đầu
 *   đơn con bị hủy hoặc bị hoàn hàng         | hoàn cho khách, bằng tổng đơn con    | CHO_THONG_TIN
 *   tiền về nhưng không tạo được đơn         | hoàn cho khách, bằng số đã nhận      | CHO_THONG_TIN
 *   đơn con giao thành công                  | chi cho cửa hàng, bằng doanh thu shop | CHO_XU_LY
 *
 * Khoản hoàn chờ khách xác nhận tài khoản nhận rồi mới sang CHO_XU_LY. Hệ thống không tự chuyển tiền:
 * Quản lý chuyển khoản bằng tài khoản của sàn rồi đánh dấu DA_CHUYEN.
 */
@Service
@RequiredArgsConstructor
public class ChuyenTienService {

    public static final String LOAI_GD_HOAN_TIEN = "HOAN_TIEN";
    public static final String LOAI_GD_CHI_CUA_HANG = "CHI_CUA_HANG";

    private static final String CONG_CHUYEN_KHOAN = "CHUYEN_KHOAN";
    private static final String GD_THANH_CONG = "THANH_CONG";
    private static final String HOAN_TIEN_KHACH = LoaiChuyenTien.HOAN_TIEN_KHACH.name();
    private static final String CHI_CUA_HANG = LoaiChuyenTien.CHI_CUA_HANG.name();

    private final YeuCauChuyenTienRepository yeuCauRepository;
    private final PhienThanhToanRepository phienRepository;
    private final GiaoDichNganHangRepository giaoDichRepository;
    private final ThanhToanRepository thanhToanRepository;
    private final TaiKhoanRepository taiKhoanRepository;
    private final NghiepVuThongBaoService thongBaoService;
    private final VietQrLinkBuilder vietQrLinkBuilder;
    private final ApplicationEventPublisher eventPublisher;

    // ---------------------------------------------------------------
    // Lập khoản (gọi từ quyết toán và từ bước xác nhận tiền về)
    // ---------------------------------------------------------------

    @Transactional(propagation = Propagation.MANDATORY)
    public void taoHoanTienChoDon(DonHangCuaHang order, String lyDo) {
        if (yeuCauRepository.existsByLoaiAndDonHangCuaHang_MaDHCH(HOAN_TIEN_KHACH, order.getMaDHCH())) {
            return;
        }
        BigDecimal soTien = nz(order.getTongTienShopOrder());
        if (soTien.signum() <= 0) {
            return;
        }

        NguoiDung khach = order.getDonHang().getNguoiMua();
        YeuCauChuyenTien yeuCau = YeuCauChuyenTien.builder()
                .loai(HOAN_TIEN_KHACH)
                .donHangCuaHang(order)
                .nguoiNhan(khach)
                .soTien(soTien)
                .trangThai(TrangThaiChuyenTien.CHO_THONG_TIN.name())
                .lyDo(cut(lyDo))
                .build();

        // Điền sẵn tài khoản nhận hoàn theo thứ tự ưu tiên:
        //   1. tài khoản hoàn khách chọn lúc hủy đơn (hoặc ảnh QR khách tải lên);
        //   2. tài khoản người chuyển trong giao dịch ngân hàng (Casso gửi kèm).
        // Có tài khoản thì khoản hoàn vào thẳng CHO_XU_LY, Quản lý chuyển được ngay.
        PhienThanhToan phien = phienRepository.findFirstByDonHang_MaDH(order.getDonHang().getMaDH()).orElse(null);
        if (phien != null && coTaiKhoanUuTien(phien)) {
            yeuCau.setMaNganHang(phien.getHoanMaNganHang());
            yeuCau.setSoTaiKhoan(phien.getHoanSoTaiKhoan());
            yeuCau.setTenChuTaiKhoan(phien.getHoanTenChuTaiKhoan());
            yeuCau.setQrNganHangUrl(phien.getHoanQrUrl());
        } else if (phien != null) {
            giaoDichRepository.findFirstByPhien_MaPhienAndKetQuaOrderByMaGiaoDichDesc(
                    phien.getMaPhien(), KetQuaDoiSoat.KHOP.name())
                    .ifPresent(giaoDich -> dienSanTaiKhoan(yeuCau, giaoDich));
        }
        boolean daCoNoiNhan = (yeuCau.getSoTaiKhoan() != null && !yeuCau.getSoTaiKhoan().isBlank())
                || (yeuCau.getQrNganHangUrl() != null && !yeuCau.getQrNganHangUrl().isBlank());
        if (daCoNoiNhan) {
            yeuCau.setTrangThai(TrangThaiChuyenTien.CHO_XU_LY.name());
        }

        yeuCauRepository.save(yeuCau);

        String moTaDon = "Đơn #" + order.getMaDHCH() + " đã thanh toán trước bị hủy hoặc hoàn hàng.";
        if (daCoNoiNhan) {
            thongBaoService.coKhoanCanChuyen(true, soTien, moTaDon + " Đã có tài khoản nhận, có thể chuyển ngay.");
        } else {
            thongBaoService.canTaiKhoanHoanTien(khach, soTien, moTaDon);
            thongBaoService.coKhoanCanChuyen(true, soTien, moTaDon + " Chờ khách nhập tài khoản nhận.");
        }
    }

    private static boolean coTaiKhoanUuTien(PhienThanhToan phien) {
        return (phien.getHoanSoTaiKhoan() != null && !phien.getHoanSoTaiKhoan().isBlank())
                || (phien.getHoanQrUrl() != null && !phien.getHoanQrUrl().isBlank());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void taoHoanTienChoPhien(PhienThanhToan phien, GiaoDichNganHang giaoDich, String lyDo) {
        if (yeuCauRepository.existsByGiaoDichNganHang_MaGiaoDich(giaoDich.getMaGiaoDich())) {
            return;
        }

        YeuCauChuyenTien yeuCau = YeuCauChuyenTien.builder()
                .loai(HOAN_TIEN_KHACH)
                .phien(phien)
                .giaoDichNganHang(giaoDich)
                .nguoiNhan(phien.getNguoiMua())
                .soTien(giaoDich.getSoTien())
                .trangThai(TrangThaiChuyenTien.CHO_THONG_TIN.name())
                .lyDo(cut(lyDo))
                .build();
        dienSanTaiKhoan(yeuCau, giaoDich);
        yeuCauRepository.save(yeuCau);

        String moTa = "Đã nhận tiền cho mã " + phien.getMaThanhToan() + " nhưng không tạo được đơn hàng: " + lyDo;
        thongBaoService.canTaiKhoanHoanTien(phien.getNguoiMua(), giaoDich.getSoTien(), moTa);
        thongBaoService.coKhoanCanChuyen(true, giaoDich.getSoTien(), moTa);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void taoChiChoCuaHang(DonHangCuaHang order) {
        if (yeuCauRepository.existsByLoaiAndDonHangCuaHang_MaDHCH(CHI_CUA_HANG, order.getMaDHCH())) {
            return;
        }
        BigDecimal soTien = nz(order.getDoanhThuShop());
        CuaHang cuaHang = order.getCuaHang();
        if (soTien.signum() <= 0 || cuaHang == null) {
            return;
        }

        yeuCauRepository.save(YeuCauChuyenTien.builder()
                .loai(CHI_CUA_HANG)
                .donHangCuaHang(order)
                .nguoiNhan(cuaHang.getChuShop())
                .soTien(soTien)
                .trangThai(TrangThaiChuyenTien.CHO_XU_LY.name())
                .build());

        thongBaoService.coKhoanCanChuyen(false, soTien,
                "Đơn #" + order.getMaDHCH() + " của \"" + cuaHang.getTenCuaHang() + "\" đã giao thành công.");
        if (!coNoiNhan(cuaHang)) {
            thongBaoService.canTaiKhoanNhanTien(cuaHang, order.getMaDHCH());
        }
    }

    // ---------------------------------------------------------------
    // Khách hàng
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<YeuCauChuyenTienResponse> hoanTienCuaToi(Integer maND) {
        return yeuCauRepository.findAllByLoaiAndNguoiNhan_MaNDOrderByMaYeuCauDesc(HOAN_TIEN_KHACH, maND).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public YeuCauChuyenTienResponse nhapTaiKhoanNhan(Integer maND, Integer maYeuCau, TaiKhoanNhanRequest request) {
        YeuCauChuyenTien yeuCau = yeuCauRepository.findByIdForUpdate(maYeuCau)
                .filter(y -> HOAN_TIEN_KHACH.equals(y.getLoai()) && maND.equals(y.getNguoiNhan().getMaND()))
                .orElseThrow(() -> new BusinessException("Yêu cầu hoàn tiền không tồn tại"));

        if (TrangThaiChuyenTien.DA_CHUYEN.name().equals(yeuCau.getTrangThai())) {
            throw new BusinessException("Khoản này đã được hoàn, không thể đổi tài khoản nhận");
        }

        boolean lanDau = TrangThaiChuyenTien.CHO_THONG_TIN.name().equals(yeuCau.getTrangThai());

        request.kiemTra();
        yeuCau.setMaNganHang(request.maNganHangGon());
        yeuCau.setSoTaiKhoan(request.soTaiKhoanGon());
        yeuCau.setTenChuTaiKhoan(request.tenChuTaiKhoanGon());
        yeuCau.setQrNganHangUrl(request.qrGon());
        yeuCau.setTrangThai(TrangThaiChuyenTien.CHO_XU_LY.name());
        yeuCauRepository.save(yeuCau);
        eventPublisher.publishEvent(new VanHanhThayDoiEvent(VanHanhThayDoiEvent.REFUNDS));

        if (lanDau) {
            thongBaoService.coKhoanCanChuyen(true, yeuCau.getSoTien(),
                    "Khách đã nhập tài khoản nhận cho khoản hoàn " + noiDungChuyenKhoan(yeuCau) + ".");
        }

        return toResponse(yeuCau);
    }

    // ---------------------------------------------------------------
    // Quản lý
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<YeuCauChuyenTienResponse> danhSach(
            LoaiChuyenTien loai, TrangThaiChuyenTien trangThai, int page, int size
    ) {
        return danhSach(loai, trangThai, null, page, size);
    }

    /** tuKhoa: mã đơn, tên / SĐT / email người nhận; trống thì không lọc. */
    @Transactional(readOnly = true)
    public PageResponse<YeuCauChuyenTienResponse> danhSach(
            LoaiChuyenTien loai, TrangThaiChuyenTien trangThai, String tuKhoa, int page, int size
    ) {
        String tk = tuKhoa == null ? "" : tuKhoa.trim().toLowerCase();
        return PageResponse.of(yeuCauRepository
                .timTheoLoaiVaTuKhoa(loai.name(), trangThai == null ? null : trangThai.name(),
                        tk, "%" + tk + "%", trang(page, size))
                .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<GiaoDichNganHangResponse> danhSachGiaoDich(int page, int size) {
        return PageResponse.of(giaoDichRepository
                .findAllByOrderByMaGiaoDichDesc(trang(page, size))
                .map(GiaoDichNganHangResponse::from));
    }

    /** Giao dịch có lọc, kèm tổng số / tổng tiền khớp / tổng tiền lệch của toàn bộ kết quả lọc. */
    @Transactional(readOnly = true)
    public GiaoDichTrang danhSachGiaoDich(
            String tuKhoa, KetQuaDoiSoat ketQua, LocalDate tuNgay, LocalDate denNgay, int page, int size
    ) {
        String kq = ketQua == null ? "" : ketQua.name();
        LocalDateTime tu = (tuNgay == null ? LocalDate.of(2000, 1, 1) : tuNgay).atStartOfDay();
        LocalDateTime den = (denNgay == null ? LocalDate.of(2999, 1, 1) : denNgay).plusDays(1).atStartOfDay();
        String mau = "%" + (tuKhoa == null ? "" : tuKhoa.trim().toLowerCase()) + "%";

        var trangKetQua = giaoDichRepository.loc(kq, tu, den, mau, trang(page, size)).map(GiaoDichNganHangResponse::from);
        var tomTat = giaoDichRepository.tomTat(kq, tu, den, mau);
        return new GiaoDichTrang(trangKetQua.getContent(), trangKetQua.getNumber(), trangKetQua.getSize(),
                trangKetQua.getTotalElements(), trangKetQua.getTotalPages(),
                new TomTatGiaoDich(tomTat.getTongSo(), tomTat.getTongTienKhop(), tomTat.getTongTienLech()));
    }

    @Transactional
    public YeuCauChuyenTienResponse danhDauDaChuyen(Integer maTKXuLy, Integer maYeuCau) {
        YeuCauChuyenTien yeuCau = yeuCauRepository.findByIdForUpdate(maYeuCau)
                .orElseThrow(() -> new BusinessException("Khoản chuyển tiền không tồn tại"));

        if (TrangThaiChuyenTien.DA_CHUYEN.name().equals(yeuCau.getTrangThai())) {
            throw new BusinessException("Khoản này đã được đánh dấu là đã chuyển");
        }

        boolean hoanTien = HOAN_TIEN_KHACH.equals(yeuCau.getLoai());
        CuaHang cuaHang = yeuCau.getDonHangCuaHang() == null ? null : yeuCau.getDonHangCuaHang().getCuaHang();

        if (hoanTien && !TrangThaiChuyenTien.CHO_XU_LY.name().equals(yeuCau.getTrangThai())) {
            throw new BusinessException("Khách chưa nhập tài khoản nhận hoàn tiền");
        }
        if (!hoanTien && !coNoiNhan(cuaHang)) {
            throw new BusinessException("Cửa hàng chưa khai tài khoản nhận tiền");
        }

        LocalDateTime now = LocalDateTime.now();
        yeuCau.setTrangThai(TrangThaiChuyenTien.DA_CHUYEN.name());
        yeuCau.setNguoiXuLy(taiKhoanRepository.getReferenceById(maTKXuLy));
        yeuCau.setNgayXuLy(now);
        yeuCauRepository.save(yeuCau);
        eventPublisher.publishEvent(new VanHanhThayDoiEvent(hoanTien ? VanHanhThayDoiEvent.REFUNDS : VanHanhThayDoiEvent.PAYOUTS));

        // Khoản gắn với đơn hàng thì ghi thêm vào sổ thanh_toan, cùng chỗ với các khoản quyết toán khác.
        if (yeuCau.getDonHangCuaHang() != null) {
            DonHangCuaHang order = yeuCau.getDonHangCuaHang();
            thanhToanRepository.save(ThanhToan.builder()
                    .donHang(order.getDonHang())
                    .donHangCuaHang(order)
                    .congThanhToan(CONG_CHUYEN_KHOAN)
                    .loaiGiaoDich(hoanTien ? LOAI_GD_HOAN_TIEN : LOAI_GD_CHI_CUA_HANG)
                    .maGiaoDichCong(noiDungChuyenKhoan(yeuCau))
                    .soTien(yeuCau.getSoTien())
                    .trangThaiGiaoDich(GD_THANH_CONG)
                    .thoiGianTT(now)
                    .build());
        }

        if (hoanTien) {
            thongBaoService.daHoanTien(yeuCau.getNguoiNhan(), yeuCau.getSoTien());
        } else {
            thongBaoService.daChiChoCuaHang(cuaHang, yeuCau.getDonHangCuaHang().getMaDHCH(), yeuCau.getSoTien());
        }

        return toResponse(yeuCau);
    }

    // ---------------------------------------------------------------

    private YeuCauChuyenTienResponse toResponse(YeuCauChuyenTien yeuCau) {
        DonHangCuaHang order = yeuCau.getDonHangCuaHang();
        CuaHang cuaHang = order == null ? null : order.getCuaHang();
        NguoiDung nguoiNhan = yeuCau.getNguoiNhan();
        boolean chiCuaHang = CHI_CUA_HANG.equals(yeuCau.getLoai());

        // Khoản chi đọc tài khoản hiện tại của cửa hàng, nên cửa hàng khai sau vẫn được dùng ngay.
        String maNganHang = chiCuaHang && cuaHang != null ? cuaHang.getMaNganHang() : yeuCau.getMaNganHang();
        String soTaiKhoan = chiCuaHang && cuaHang != null ? cuaHang.getSoTaiKhoan() : yeuCau.getSoTaiKhoan();
        String tenChuTaiKhoan = chiCuaHang && cuaHang != null
                ? cuaHang.getTenChuTaiKhoan() : yeuCau.getTenChuTaiKhoan();
        String noiDung = noiDungChuyenKhoan(yeuCau);

        return new YeuCauChuyenTienResponse(
                yeuCau.getMaYeuCau(),
                yeuCau.getLoai(),
                yeuCau.getTrangThai(),
                yeuCau.getSoTien(),
                noiDung,
                order == null ? null : order.getDonHang().getMaDH(),
                order == null ? null : order.getMaDHCH(),
                yeuCau.getLyDo(),
                nguoiNhan.getHoTen(),
                nguoiNhan.getEmail(),
                nguoiNhan.getSoDienThoai(),
                cuaHang == null ? null : cuaHang.getTenCuaHang(),
                maNganHang,
                soTaiKhoan,
                tenChuTaiKhoan,
                vietQrLinkBuilder.build(maNganHang, soTaiKhoan, tenChuTaiKhoan, yeuCau.getSoTien(), noiDung),
                yeuCau.getNgayTao(),
                yeuCau.getNgayXuLy(),
                order == null ? null : order.getHuyBoi(),
                order == null ? null : order.getLyDoHuy(),
                yeuCau.getMaDotChi(),
                chiCuaHang && cuaHang != null ? cuaHang.getQrNganHangUrl() : yeuCau.getQrNganHangUrl()
        );
    }

    private static String noiDungChuyenKhoan(YeuCauChuyenTien yeuCau) {
        return LoaiChuyenTien.valueOf(yeuCau.getLoai()).getTienTo() + yeuCau.getMaYeuCau();
    }

    private static void dienSanTaiKhoan(YeuCauChuyenTien yeuCau, GiaoDichNganHang giaoDich) {
        yeuCau.setMaNganHang(giaoDich.getMaNganHangChuyen());
        yeuCau.setSoTaiKhoan(giaoDich.getSoTaiKhoanChuyen());
        yeuCau.setTenChuTaiKhoan(giaoDich.getTenNguoiChuyen());
    }

    /** Có số tài khoản hoặc ảnh mã QR ngân hàng: đủ để Quản lý chuyển tiền. */
    private static boolean coNoiNhan(CuaHang cuaHang) {
        return coTaiKhoan(cuaHang) || (cuaHang != null && cuaHang.getQrNganHangUrl() != null
                && !cuaHang.getQrNganHangUrl().isBlank());
    }

    private static boolean coTaiKhoan(CuaHang cuaHang) {
        return cuaHang != null
                && cuaHang.getMaNganHang() != null && !cuaHang.getMaNganHang().isBlank()
                && cuaHang.getSoTaiKhoan() != null && !cuaHang.getSoTaiKhoan().isBlank();
    }

    private static PageRequest trang(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
    }

    private static String cut(String value) {
        return value == null || value.length() <= 500 ? value : value.substring(0, 500);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
