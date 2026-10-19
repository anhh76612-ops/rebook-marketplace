package com.oldbook.service.management;

import com.oldbook.constant.payment.LoaiChuyenTien;
import com.oldbook.constant.payment.TrangThaiChuyenTien;
import com.oldbook.dto.management.QuanLyDto.DonChi;
import com.oldbook.dto.management.QuanLyDto.DotChiXemTruoc;
import com.oldbook.dto.management.QuanLyDto.KetQuaNhac;
import com.oldbook.dto.management.QuanLyDto.NhomChi;
import com.oldbook.dto.management.QuanLyDto.TaiKhoanNhan;
import com.oldbook.entity.catalog.CuaHang;
import com.oldbook.entity.order.DonHangCuaHang;
import com.oldbook.entity.payment.YeuCauChuyenTien;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.repository.notification.ThongBaoRepository;
import com.oldbook.repository.payment.YeuCauChuyenTienRepository;
import com.oldbook.repository.store.CuaHangRepository;
import com.oldbook.service.notification.NghiepVuThongBaoService;
import com.oldbook.service.payment.ChuyenTienService;
import com.oldbook.service.payment.PhienThanhToanService;
import com.oldbook.service.payment.VietQrLinkBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Chi trả doanh thu cho cửa hàng theo đợt: gom nhiều đơn của MỘT cửa hàng thành một lần chuyển khoản.
 *
 * Mã đợt chi (DC + 8 ký tự) được tính từ danh sách khoản và tổng tiền, nên không cần lưu lúc xem trước: lúc xác nhận,
 * máy chủ tính lại và so khớp. Nếu trong lúc Quản lý đang chuyển khoản mà có khoản bị đổi hoặc đã được người khác
 * đánh dấu thì mã không còn khớp và việc xác nhận bị từ chối.
 */
@Service
@RequiredArgsConstructor
public class QuanLyChiTraService {

    public static final String TIEN_TO_DOT_CHI = "DC";
    private static final long GIO_GIOI_HAN_NHAC = 1;

    private static final String CHI_CUA_HANG = LoaiChuyenTien.CHI_CUA_HANG.name();
    private static final String CHO_XU_LY = TrangThaiChuyenTien.CHO_XU_LY.name();
    private static final String DA_CHUYEN = TrangThaiChuyenTien.DA_CHUYEN.name();

    private final YeuCauChuyenTienRepository yeuCauRepository;
    private final CuaHangRepository cuaHangRepository;
    private final ThongBaoRepository thongBaoRepository;
    private final NghiepVuThongBaoService thongBaoService;
    private final ChuyenTienService chuyenTienService;
    private final VietQrLinkBuilder vietQrLinkBuilder;
    private final ApplicationEventPublisher eventPublisher;

    /** Các khoản chi gom theo cửa hàng. trangThai: CHO_XU_LY (mặc định) hoặc DA_CHUYEN. */
    @Transactional(readOnly = true)
    public List<NhomChi> theoCuaHang(String trangThai) {
        String tt = DA_CHUYEN.equals(trangThai) ? DA_CHUYEN : CHO_XU_LY;

        List<YeuCauChuyenTien> yeuCaus = yeuCauRepository.timChiTheoTrangThai(CHI_CUA_HANG, tt);
        Map<Integer, List<YeuCauChuyenTien>> theoCh = yeuCaus.stream().collect(Collectors.groupingBy(
                y -> y.getDonHangCuaHang().getCuaHang().getMaCH(), LinkedHashMap::new, Collectors.toList()));

        List<NhomChi> nhoms = new ArrayList<>();
        for (List<YeuCauChuyenTien> ds : theoCh.values()) {
            CuaHang ch = ds.get(0).getDonHangCuaHang().getCuaHang();
            List<DonChi> don = ds.stream().map(QuanLyChiTraService::toDon).toList();
            LocalDateTime daNhac = thongBaoRepository
                    .findFirstByLoaiAndTaiKhoanNhan_NguoiDung_MaNDOrderByNgayTaoDesc(
                            NghiepVuThongBaoService.LOAI_NHAC_KHAI_TAI_KHOAN, ch.getChuShop().getMaND())
                    .map(t -> t.getNgayTao()).orElse(null);

            nhoms.add(new NhomChi(
                    ch.getMaCH(), ch.getTenCuaHang(), ch.getChuShop().getHoTen(), ch.getChuShop().getSoDienThoai(),
                    coNoiNhan(ch), ch.getMaNganHang(), ch.getSoTaiKhoan(), ch.getTenChuTaiKhoan(), ch.getQrNganHangUrl(),
                    don.size(),
                    cong(don, DonChi::tienHang), cong(don, DonChi::hoaHong), cong(don, DonChi::thucNhan),
                    daNhac, don));
        }
        nhoms.sort(Comparator.comparing(NhomChi::tongThucNhan).reversed());
        return nhoms;
    }

    @Transactional(readOnly = true)
    public DotChiXemTruoc xemTruoc(List<Integer> maYeuCau) {
        List<YeuCauChuyenTien> ds = layKhoanCuaMotCuaHang(maYeuCau);
        CuaHang ch = ds.get(0).getDonHangCuaHang().getCuaHang();
        if (!coNoiNhan(ch)) {
            throw new BusinessException("Cửa hàng chưa khai tài khoản nhận tiền");
        }
        BigDecimal tong = tongTien(ds);
        String maDotChi = maDotChi(ds, tong);
        // Có số tài khoản thì dựng VietQR chuẩn; chỉ có ảnh QR thì trả ảnh để Quản lý tự nhập số tiền và nội dung.
        String qr = coTaiKhoan(ch)
                ? vietQrLinkBuilder.build(ch.getMaNganHang(), ch.getSoTaiKhoan(), ch.getTenChuTaiKhoan(), tong, maDotChi)
                : null;
        return new DotChiXemTruoc(maDotChi, ch.getMaCH(), ch.getTenCuaHang(), ds.size(), tong, qr,
                new TaiKhoanNhan(ch.getMaNganHang(), ch.getSoTaiKhoan(), ch.getTenChuTaiKhoan()),
                coTaiKhoan(ch) ? null : ch.getQrNganHangUrl());
    }

    /** Đánh dấu cả đợt là đã chuyển, trong một giao dịch: một khoản lỗi thì không khoản nào bị ghi. */
    @Transactional
    public int xacNhanDaChuyen(Integer maTKXuLy, String maDotChi, List<Integer> maYeuCau) {
        List<YeuCauChuyenTien> ds = layKhoanCuaMotCuaHang(maYeuCau);
        if (maDotChi == null || !maDotChi.equals(maDotChi(ds, tongTien(ds)))) {
            throw new BusinessException("Đợt chi đã thay đổi, vui lòng tạo lại mã đợt chi");
        }
        for (YeuCauChuyenTien y : ds) {
            chuyenTienService.danhDauDaChuyen(maTKXuLy, y.getMaYeuCau());
            y.setMaDotChi(maDotChi);
            yeuCauRepository.save(y);
        }
        eventPublisher.publishEvent(new VanHanhThayDoiEvent(VanHanhThayDoiEvent.PAYOUTS));
        return ds.size();
    }

    @Transactional
    public KetQuaNhac nhacCuaHang(Integer maCH) {
        CuaHang ch = cuaHangRepository.findById(maCH)
                .orElseThrow(() -> new BusinessException("Cửa hàng không tồn tại"));
        if (coNoiNhan(ch)) {
            throw new BusinessException("Cửa hàng đã khai tài khoản nhận tiền");
        }
        giuGioiHanNhac(ch.getChuShop().getMaND());
        thongBaoService.nhacKhaiTaiKhoanCuaHang(ch);
        return new KetQuaNhac(LocalDateTime.now());
    }

    @Transactional
    public KetQuaNhac nhacKhachHoanTien(Integer maYeuCau) {
        YeuCauChuyenTien y = yeuCauRepository.findById(maYeuCau)
                .filter(r -> LoaiChuyenTien.HOAN_TIEN_KHACH.name().equals(r.getLoai()))
                .orElseThrow(() -> new BusinessException("Yêu cầu hoàn tiền không tồn tại"));
        if (!TrangThaiChuyenTien.CHO_THONG_TIN.name().equals(y.getTrangThai())) {
            throw new BusinessException("Khách đã khai tài khoản nhận hoàn tiền");
        }
        giuGioiHanNhac(y.getNguoiNhan().getMaND());
        thongBaoService.nhacKhaiTaiKhoanHoanTien(y.getNguoiNhan(), y.getSoTien());
        return new KetQuaNhac(LocalDateTime.now());
    }

    // ---------------------------------------------------------------

    private void giuGioiHanNhac(Integer maND) {
        thongBaoRepository
                .findFirstByLoaiAndTaiKhoanNhan_NguoiDung_MaNDOrderByNgayTaoDesc(
                        NghiepVuThongBaoService.LOAI_NHAC_KHAI_TAI_KHOAN, maND)
                .filter(t -> t.getNgayTao().isAfter(LocalDateTime.now().minusHours(GIO_GIOI_HAN_NHAC)))
                .ifPresent(t -> {
                    throw new BusinessException("Đã nhắc lúc " + String.format("%02d:%02d",
                            t.getNgayTao().getHour(), t.getNgayTao().getMinute())
                            + ", chỉ nhắc lại được sau " + GIO_GIOI_HAN_NHAC + " giờ");
                });
    }

    /** Mọi khoản phải là khoản chi, đang CHO_XU_LY, và thuộc cùng một cửa hàng. */
    private List<YeuCauChuyenTien> layKhoanCuaMotCuaHang(List<Integer> maYeuCau) {
        if (maYeuCau == null || maYeuCau.isEmpty()) {
            throw new BusinessException("Chưa chọn đơn nào");
        }
        List<Integer> ids = maYeuCau.stream().distinct().sorted().toList();
        List<YeuCauChuyenTien> ds = yeuCauRepository.timChiTheoMa(ids);
        if (ds.size() != ids.size()) {
            throw new BusinessException("Có khoản chi không tồn tại");
        }
        for (YeuCauChuyenTien y : ds) {
            if (!CHI_CUA_HANG.equals(y.getLoai()) || y.getDonHangCuaHang() == null) {
                throw new BusinessException("Chỉ gộp được các khoản chi cho cửa hàng");
            }
            if (!CHO_XU_LY.equals(y.getTrangThai())) {
                throw new BusinessException("Khoản #" + y.getMaYeuCau() + " đã được chuyển");
            }
        }
        Integer maCH = ds.get(0).getDonHangCuaHang().getCuaHang().getMaCH();
        if (ds.stream().anyMatch(y -> !maCH.equals(y.getDonHangCuaHang().getCuaHang().getMaCH()))) {
            throw new BusinessException("Mỗi đợt chi chỉ gồm các đơn của một cửa hàng");
        }
        return ds;
    }

    private static BigDecimal tongTien(List<YeuCauChuyenTien> ds) {
        return ds.stream().map(YeuCauChuyenTien::getSoTien).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String maDotChi(List<YeuCauChuyenTien> ds, BigDecimal tong) {
        String goc = ds.stream().map(y -> String.valueOf(y.getMaYeuCau())).sorted().collect(Collectors.joining(","))
                + "|" + tong.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(goc.getBytes(StandardCharsets.UTF_8));
            StringBuilder ma = new StringBuilder(TIEN_TO_DOT_CHI);
            String bang = PhienThanhToanService.BANG_KY_TU_MA;
            for (int i = 0; i < PhienThanhToanService.SO_KY_TU_MA; i++) {
                ma.append(bang.charAt((hash[i] & 0xff) % bang.length()));
            }
            return ma.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static DonChi toDon(YeuCauChuyenTien y) {
        DonHangCuaHang d = y.getDonHangCuaHang();
        return new DonChi(y.getMaYeuCau(), d.getMaDHCH(), d.getDonHang().getMaDH(), d.getNgayGiao(),
                nz(d.getTienHangCH()), nz(d.getGiamGiaShop()), nz(d.getCommissionTien()), nz(d.getDoanhThuShop()),
                y.getTrangThai(), y.getMaDotChi());
    }

    private static BigDecimal cong(List<DonChi> ds, java.util.function.Function<DonChi, BigDecimal> f) {
        return ds.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static boolean coNoiNhan(CuaHang ch) {
        return coTaiKhoan(ch) || (ch.getQrNganHangUrl() != null && !ch.getQrNganHangUrl().isBlank());
    }

    private static boolean coTaiKhoan(CuaHang ch) {
        return ch.getMaNganHang() != null && !ch.getMaNganHang().isBlank()
                && ch.getSoTaiKhoan() != null && !ch.getSoTaiKhoan().isBlank();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
