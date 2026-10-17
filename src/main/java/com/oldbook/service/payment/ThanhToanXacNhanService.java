package com.oldbook.service.payment;

import com.oldbook.constant.payment.KetQuaDoiSoat;
import com.oldbook.constant.payment.TrangThaiPhienThanhToan;
import com.oldbook.dto.order.OrderResponse;
import com.oldbook.entity.payment.GiaoDichNganHang;
import com.oldbook.entity.payment.PhienThanhToan;
import com.oldbook.entity.payment.ThanhToan;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.repository.order.DonHangRepository;
import com.oldbook.repository.payment.GiaoDichNganHangRepository;
import com.oldbook.repository.payment.PhienThanhToanRepository;
import com.oldbook.repository.payment.ThanhToanRepository;
import com.oldbook.service.notification.NghiepVuThongBaoService;
import com.oldbook.service.order.DonHangService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Xử lý một giao dịch ngân hàng do Casso báo về: ghi sổ, tìm phiên thanh toán, tạo đơn hàng.
 *
 *   Tình huống                                          | Kết quả
 *   Casso gửi lại giao dịch đã xử lý xong               | bỏ qua
 *   không phải tiền vào, hoặc nội dung không có mã phiên | KHONG_KHOP, chỉ ghi sổ
 *   đủ tiền, tạo được đơn                               | KHOP, đơn CHUYEN_KHOAN đã thanh toán
 *   thiếu tiền, hết hàng, giá hoặc giỏ đã đổi            | CAN_HOAN_TIEN, lập khoản hoàn đúng số đã nhận
 *   phiên đã được trả bằng một giao dịch khác            | CAN_HOAN_TIEN, lập khoản hoàn
 *
 * Phiên đã hết hạn hoặc khách đã hủy mà tiền vẫn về thì vẫn thử tạo đơn: còn hàng là khách có đơn.
 *
 * Lớp này không tự mở giao dịch database bao ngoài: việc tạo đơn chạy trong một giao dịch riêng để khi
 * nó thất bại và bị hủy, bước chuyển sang hoàn tiền vẫn ghi được ở giao dịch kế tiếp.
 */
@Slf4j
@Service
public class ThanhToanXacNhanService {

    public static final String CONG_CASSO = "CASSO";
    public static final String LOAI_GD_KHACH_CHUYEN_KHOAN = "KHACH_CHUYEN_KHOAN";

    private static final String GD_THANH_CONG = "THANH_CONG";
    private static final Pattern MAU_MA_THANH_TOAN = Pattern.compile(
            PhienThanhToanService.TIEN_TO_MA
                    + "[" + PhienThanhToanService.BANG_KY_TU_MA + "]{" + PhienThanhToanService.SO_KY_TU_MA + "}"
    );

    private final GiaoDichNganHangRepository giaoDichRepository;
    private final PhienThanhToanRepository phienRepository;
    private final ThanhToanRepository thanhToanRepository;
    private final DonHangRepository donHangRepository;
    private final PhienThanhToanService phienService;
    private final DonHangService donHangService;
    private final ChuyenTienService chuyenTienService;
    private final NghiepVuThongBaoService thongBaoService;
    // Luôn mở giao dịch database mới, kể cả khi nơi gọi đang có sẵn một giao dịch.
    private final TransactionTemplate transactionTemplate;

    public ThanhToanXacNhanService(
            GiaoDichNganHangRepository giaoDichRepository,
            PhienThanhToanRepository phienRepository,
            ThanhToanRepository thanhToanRepository,
            DonHangRepository donHangRepository,
            PhienThanhToanService phienService,
            DonHangService donHangService,
            ChuyenTienService chuyenTienService,
            NghiepVuThongBaoService thongBaoService,
            PlatformTransactionManager transactionManager
    ) {
        this.giaoDichRepository = giaoDichRepository;
        this.phienRepository = phienRepository;
        this.thanhToanRepository = thanhToanRepository;
        this.donHangRepository = donHangRepository;
        this.phienService = phienService;
        this.donHangService = donHangService;
        this.chuyenTienService = chuyenTienService;
        this.thongBaoService = thongBaoService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Một giao dịch trên tài khoản của sàn, đã tách khỏi định dạng riêng của Casso. */
    public record GiaoDichDen(
            String maGiaoDichCong,
            String maThamChieu,
            BigDecimal soTien,
            String noiDung,
            LocalDateTime thoiGian,
            String soTaiKhoanNhan,
            String tenNguoiChuyen,
            String soTaiKhoanChuyen,
            String maNganHangChuyen
    ) {
    }

    /** Trả về null khi giao dịch này đã được xử lý xong từ trước. */
    public KetQuaDoiSoat xuLy(GiaoDichDen giaoDich) {
        Integer maGiaoDich = ghiSo(giaoDich);
        if (maGiaoDich == null) {
            return null;
        }

        if (giaoDich.soTien() == null || giaoDich.soTien().signum() <= 0) {
            return ketThuc(maGiaoDich, null, KetQuaDoiSoat.KHONG_KHOP, "Không phải giao dịch tiền vào");
        }

        Integer maPhien = timPhien(giaoDich.noiDung()).orElse(null);
        if (maPhien == null) {
            return ketThuc(maGiaoDich, null, KetQuaDoiSoat.KHONG_KHOP, "Nội dung không chứa mã thanh toán nào");
        }

        try {
            String ghiChu = transactionTemplate.execute(status -> taoDon(maPhien, giaoDich));
            return ketThuc(maGiaoDich, maPhien, KetQuaDoiSoat.KHOP, ghiChu);
        } catch (BusinessException ex) {
            // Giao dịch tạo đơn đã bị hủy trọn vẹn; ghi nhận việc phải hoàn tiền ở một giao dịch mới.
            transactionTemplate.executeWithoutResult(status -> chuyenSangHoanTien(maPhien, maGiaoDich, ex.getMessage()));
            return ketThuc(maGiaoDich, maPhien, KetQuaDoiSoat.CAN_HOAN_TIEN, ex.getMessage());
        }
    }

    /**
     * Ghi giao dịch vào sổ. Mã Casso là duy nhất nên cùng một giao dịch chỉ được nhận một lần;
     * riêng dòng còn DANG_XU_LY (lần trước lỗi giữa chừng) thì cho xử lý tiếp khi Casso gửi lại.
     */
    private Integer ghiSo(GiaoDichDen giaoDich) {
        try {
            return transactionTemplate.execute(status -> {
                Optional<GiaoDichNganHang> daCo = giaoDichRepository.findByMaGiaoDichCong(giaoDich.maGiaoDichCong());
                if (daCo.isPresent()) {
                    return KetQuaDoiSoat.DANG_XU_LY.name().equals(daCo.get().getKetQua())
                            ? daCo.get().getMaGiaoDich()
                            : null;
                }
                return giaoDichRepository.save(GiaoDichNganHang.builder()
                        .maGiaoDichCong(giaoDich.maGiaoDichCong())
                        .maThamChieu(cut(giaoDich.maThamChieu(), 100))
                        .soTien(giaoDich.soTien() == null ? BigDecimal.ZERO : giaoDich.soTien())
                        .noiDung(cut(giaoDich.noiDung(), 1000))
                        .thoiGianGiaoDich(giaoDich.thoiGian())
                        .soTaiKhoanNhan(cut(giaoDich.soTaiKhoanNhan(), 50))
                        .tenNguoiChuyen(cut(giaoDich.tenNguoiChuyen(), 255))
                        .soTaiKhoanChuyen(cut(giaoDich.soTaiKhoanChuyen(), 50))
                        .maNganHangChuyen(cut(giaoDich.maNganHangChuyen(), 20))
                        .ketQua(KetQuaDoiSoat.DANG_XU_LY.name())
                        .build()).getMaGiaoDich();
            });
        } catch (DataIntegrityViolationException ex) {
            // Hai yêu cầu cùng một giao dịch đến đồng thời: yêu cầu kia đã ghi sổ và sẽ xử lý.
            return null;
        }
    }

    private String taoDon(Integer maPhien, GiaoDichDen giaoDich) {
        PhienThanhToan phien = phienRepository.findByIdForUpdate(maPhien)
                .orElseThrow(() -> new BusinessException("Phiên thanh toán không còn tồn tại"));

        if (TrangThaiPhienThanhToan.DA_THANH_TOAN.name().equals(phien.getTrangThai())) {
            // Chính giao dịch này đã tạo đơn ở lần gửi trước: không làm gì thêm.
            if (thanhToanRepository.existsByMaGiaoDichCongAndLoaiGiaoDich(
                    giaoDich.maGiaoDichCong(), LOAI_GD_KHACH_CHUYEN_KHOAN)) {
                return null;
            }
            throw new BusinessException("Mã thanh toán này đã được trả bằng một giao dịch khác");
        }
        if (TrangThaiPhienThanhToan.CAN_HOAN_TIEN.name().equals(phien.getTrangThai())) {
            throw new BusinessException("Phiên thanh toán đã chuyển sang hoàn tiền");
        }
        if (giaoDich.soTien().compareTo(phien.getSoTien()) < 0) {
            throw new BusinessException("Số tiền chuyển khoản ít hơn số tiền cần thanh toán");
        }

        // Nhả hàng đang giữ rồi đặt hàng theo đúng luồng thường. Cùng một giao dịch database và sách đang bị
        // khóa, nên không ai chen vào lấy được sách giữa hai bước.
        phienService.traKho(phien);
        Integer maND = phien.getNguoiMua().getMaND();
        OrderResponse donHang = donHangService.createPaidOrder(maND, phienService.yeuCauDatHang(phien));

        if (donHang.getTongThanhToan().compareTo(phien.getSoTien()) != 0) {
            throw new BusinessException("Giá sách hoặc phí vận chuyển đã thay đổi sau khi tạo mã thanh toán");
        }

        LocalDateTime now = LocalDateTime.now();
        thanhToanRepository.save(ThanhToan.builder()
                .donHang(donHangRepository.getReferenceById(donHang.getMaDH()))
                .congThanhToan(CONG_CASSO)
                .loaiGiaoDich(LOAI_GD_KHACH_CHUYEN_KHOAN)
                .maGiaoDichCong(giaoDich.maGiaoDichCong())
                .soTien(giaoDich.soTien())
                .trangThaiGiaoDich(GD_THANH_CONG)
                .thoiGianTT(now)
                .build());

        phien.setTrangThai(TrangThaiPhienThanhToan.DA_THANH_TOAN.name());
        phien.setDonHang(donHangRepository.getReferenceById(donHang.getMaDH()));
        phien.setSoTienNhan(giaoDich.soTien());
        phienRepository.save(phien);

        thongBaoService.thanhToanThanhCong(phien.getNguoiMua(), donHang.getMaDH(), giaoDich.soTien());

        BigDecimal thua = giaoDich.soTien().subtract(phien.getSoTien());
        return thua.signum() > 0 ? "Khách chuyển thừa " + thua.toPlainString() + " đ" : null;
    }

    private void chuyenSangHoanTien(Integer maPhien, Integer maGiaoDich, String lyDo) {
        PhienThanhToan phien = phienRepository.findByIdForUpdate(maPhien)
                .orElseThrow(() -> new BusinessException("Phiên thanh toán không còn tồn tại"));
        GiaoDichNganHang giaoDich = giaoDichRepository.findById(maGiaoDich)
                .orElseThrow(() -> new BusinessException("Giao dịch không còn tồn tại"));

        // Phiên đã có đơn (khách trả hai lần) thì giữ nguyên đơn, chỉ hoàn giao dịch thừa.
        if (!TrangThaiPhienThanhToan.DA_THANH_TOAN.name().equals(phien.getTrangThai())) {
            phienService.traKho(phien);
            phien.setTrangThai(TrangThaiPhienThanhToan.CAN_HOAN_TIEN.name());
            phien.setSoTienNhan(giaoDich.getSoTien());
            phienRepository.save(phien);
        }

        chuyenTienService.taoHoanTienChoPhien(phien, giaoDich, lyDo);
    }

    private KetQuaDoiSoat ketThuc(Integer maGiaoDich, Integer maPhien, KetQuaDoiSoat ketQua, String ghiChu) {
        transactionTemplate.executeWithoutResult(status -> {
            GiaoDichNganHang giaoDich = giaoDichRepository.findById(maGiaoDich).orElseThrow();
            giaoDich.setKetQua(ketQua.name());
            giaoDich.setGhiChu(cut(ghiChu, 500));
            if (maPhien != null) {
                giaoDich.setPhien(phienRepository.getReferenceById(maPhien));
            }
            giaoDichRepository.save(giaoDich);
        });
        log.info("Giao dịch ngân hàng {}: {}{}", maGiaoDich, ketQua, ghiChu == null ? "" : " (" + ghiChu + ")");
        return ketQua;
    }

    // Ngân hàng hay chèn thêm chữ, dấu chấm, khoảng trắng quanh nội dung, nên bỏ hết ký tự lạ rồi mới dò mã.
    private Optional<Integer> timPhien(String noiDung) {
        if (noiDung == null) {
            return Optional.empty();
        }
        String chuanHoa = noiDung.toUpperCase().replaceAll("[^A-Z0-9]", "");
        Matcher matcher = MAU_MA_THANH_TOAN.matcher(chuanHoa);

        int from = 0;
        while (matcher.find(from)) {
            Optional<PhienThanhToan> phien = phienRepository.findByMaThanhToan(matcher.group());
            if (phien.isPresent()) {
                return phien.map(PhienThanhToan::getMaPhien);
            }
            // Dò tiếp từ ký tự kế bên: mã thật có thể bắt đầu ngay trong một chuỗi trông giống mã.
            from = matcher.start() + 1;
        }
        return Optional.empty();
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
