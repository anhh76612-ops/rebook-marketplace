package com.oldbook.service.order;

import com.oldbook.constant.order.PhuongThucThanhToan;
import com.oldbook.constant.order.TrangThaiDonHang;
import com.oldbook.constant.order.TrangThaiThanhToan;
import com.oldbook.entity.catalog.Sach;
import com.oldbook.entity.order.ChiTietDonHang;
import com.oldbook.entity.order.DonHang;
import com.oldbook.entity.order.DonHangCuaHang;
import com.oldbook.entity.payment.CauHinhChietKhau;
import com.oldbook.entity.payment.ThanhToan;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.repository.catalog.SachRepository;
import com.oldbook.repository.order.ChiTietDonHangRepository;
import com.oldbook.repository.order.DonHangCuaHangRepository;
import com.oldbook.repository.order.DonHangRepository;
import com.oldbook.repository.payment.CauHinhChietKhauRepository;
import com.oldbook.repository.payment.ThanhToanRepository;
import com.oldbook.service.payment.ChuyenTienService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderSettlementService {

    public static final String LOAI_THU_HO_COD = "THU_HO_COD";
    public static final String LOAI_HOA_HONG = "HOA_HONG";
    public static final String LOAI_DOANH_THU_SHOP = "DOANH_THU_SHOP";

    private static final String CONG_COD = "COD";
    private static final String GD_THANH_CONG = "THANH_CONG";
    private static final String CHIET_KHAU_HOAT_DONG = "HOAT_DONG";
    private static final BigDecimal TRAM = BigDecimal.valueOf(100);

    private final ChiTietDonHangRepository chiTietDonHangRepository;
    private final CauHinhChietKhauRepository cauHinhChietKhauRepository;
    private final ThanhToanRepository thanhToanRepository;
    private final SachRepository sachRepository;
    private final DonHangRepository donHangRepository;
    private final DonHangCuaHangRepository donHangCuaHangRepository;
    private final ChuyenTienService chuyenTienService;

    /** Tỷ lệ hoa hồng (%) áp dụng khi danh mục chưa có cấu hình chiết khấu đang hiệu lực. */
    @Value("${marketplace.commission.default-rate:5.00}")
    private BigDecimal defaultCommissionRate;

    @Transactional(propagation = Propagation.MANDATORY)
    public void settleDelivered(DonHangCuaHang order, String maVanDon) {
        if (order.getNgayGiao() != null) {
            return; // đã chốt, tránh ghi nhận doanh thu hai lần
        }

        LocalDateTime now = LocalDateTime.now();
        List<ChiTietDonHang> lines = chiTietDonHangRepository
                .findAllByDonHangCuaHang_MaDHCHOrderByMaCTDHAsc(order.getMaDHCH());

        Map<Integer, BigDecimal> rateByCategory = new HashMap<>();
        BigDecimal commission = BigDecimal.ZERO;

        for (ChiTietDonHang line : lines) {
            Integer maDM = line.getSach().getDanhMuc().getMaDM();
            BigDecimal rate = rateByCategory.computeIfAbsent(maDM, id -> commissionRate(id, now));
            BigDecimal tien = nz(line.getThanhTien()).multiply(rate).divide(TRAM, 2, RoundingMode.HALF_UP);

            line.setTyLeChietKhau(rate);
            line.setTienChietKhau(tien);
            commission = commission.add(tien);
        }
        chiTietDonHangRepository.saveAll(lines);

        BigDecimal doanhThu = nz(order.getTienHangCH())
                .subtract(nz(order.getGiamGiaShop()))
                .subtract(commission)
                .max(BigDecimal.ZERO);

        order.setCommissionTien(commission);
        order.setDoanhThuShop(doanhThu);
        order.setTienHoanTra(BigDecimal.ZERO);
        order.setTrangThaiThanhToanShop(TrangThaiThanhToan.DA_THANH_TOAN.name());
        order.setNgayGiao(now);
        donHangCuaHangRepository.save(order);

        boolean traTruoc = laTraTruoc(order);
        if (!traTruoc) {
            // COD: đơn vị vận chuyển thu đủ tiền hàng + phí ship từ khách khi giao thành công.
            recordTransaction(order, LOAI_THU_HO_COD, order.getTongTienShopOrder(), maVanDon, now);
        }
        recordTransaction(order, LOAI_HOA_HONG, commission, maVanDon, now);
        recordTransaction(order, LOAI_DOANH_THU_SHOP, doanhThu, maVanDon, now);
        if (traTruoc) {
            // Chuyển khoản: sàn đang giữ tiền của khách, nên phải chi phần doanh thu cho cửa hàng.
            chuyenTienService.taoChiChoCuaHang(order);
        }

        refreshParentPaymentStatus(order.getDonHang().getMaDH());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void settleReturned(DonHangCuaHang order) {
        if (order.getNgayHoanTra() != null) {
            return; // đã hoàn kho, tránh cộng tồn hai lần
        }

        List<ChiTietDonHang> lines = chiTietDonHangRepository
                .findAllByDonHangCuaHang_MaDHCHOrderByMaCTDHAsc(order.getMaDHCH());

        for (ChiTietDonHang line : lines) {
            Sach sach = sachRepository.findByIdForUpdate(line.getSach().getMaSach())
                    .orElseThrow(() -> new BusinessException("Sách không tồn tại khi hoàn kho"));
            sach.setSoLuongTon(nz(sach.getSoLuongTon()) + line.getSoLuong());
            sachRepository.save(sach);

            line.setTyLeChietKhau(BigDecimal.ZERO);
            line.setTienChietKhau(BigDecimal.ZERO);
        }
        chiTietDonHangRepository.saveAll(lines);

        order.setCommissionTien(BigDecimal.ZERO);
        order.setDoanhThuShop(BigDecimal.ZERO);
        order.setTienHoanTra(tienPhaiHoan(order));
        order.setTrangThaiThanhToanShop(TrangThaiThanhToan.KHONG_THANH_TOAN.name());
        order.setNgayHoanTra(LocalDateTime.now());
        donHangCuaHangRepository.save(order);
        if (laTraTruoc(order)) {
            chuyenTienService.taoHoanTienChoDon(order, "Giao hàng không thành công");
        }

        refreshParentPaymentStatus(order.getDonHang().getMaDH());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void settleCancelled(DonHangCuaHang order, String huyBoi, String lyDo) {
        if (TrangThaiDonHang.DA_HUY.name().equals(order.getTrangThaiDonHang())) {
            return; // đã hủy, tránh cộng tồn hai lần
        }

        List<ChiTietDonHang> lines = chiTietDonHangRepository
                .findAllByDonHangCuaHang_MaDHCHOrderByMaCTDHAsc(order.getMaDHCH());

        for (ChiTietDonHang line : lines) {
            Sach sach = sachRepository.findByIdForUpdate(line.getSach().getMaSach())
                    .orElseThrow(() -> new BusinessException("Sách không tồn tại khi hoàn kho"));
            sach.setSoLuongTon(nz(sach.getSoLuongTon()) + line.getSoLuong());
            sachRepository.save(sach);
        }

        order.setTrangThaiDonHang(TrangThaiDonHang.DA_HUY.name());
        order.setCommissionTien(BigDecimal.ZERO);
        order.setDoanhThuShop(BigDecimal.ZERO);
        order.setTienHoanTra(tienPhaiHoan(order));
        order.setTrangThaiThanhToanShop(TrangThaiThanhToan.KHONG_THANH_TOAN.name());
        order.setNgayHuy(LocalDateTime.now());
        order.setHuyBoi(huyBoi);
        order.setLyDoHuy(lyDo);
        donHangCuaHangRepository.save(order);
        if (laTraTruoc(order)) {
            chuyenTienService.taoHoanTienChoDon(order, "Đơn hàng bị hủy");
        }

        refreshParentPaymentStatus(order.getDonHang().getMaDH());
    }

    private BigDecimal commissionRate(Integer maDM, LocalDateTime now) {
        Optional<CauHinhChietKhau> cfg = cauHinhChietKhauRepository
                .findEffective(maDM, CHIET_KHAU_HOAT_DONG, now)
                .stream()
                .filter(c -> c.getTyLeChietKhau() != null)
                .findFirst();
        return cfg.map(CauHinhChietKhau::getTyLeChietKhau).orElse(defaultCommissionRate);
    }

    private void recordTransaction(
            DonHangCuaHang order, String loai, BigDecimal soTien, String maVanDon, LocalDateTime now
    ) {
        if (thanhToanRepository.existsByDonHangCuaHang_MaDHCHAndLoaiGiaoDich(order.getMaDHCH(), loai)) {
            return;
        }
        thanhToanRepository.save(ThanhToan.builder()
                .donHang(order.getDonHang())
                .donHangCuaHang(order)
                .congThanhToan(CONG_COD)
                .loaiGiaoDich(loai)
                .maGiaoDichCong(maVanDon)
                .soTien(nz(soTien))
                .trangThaiGiaoDich(GD_THANH_CONG)
                .thoiGianTT(now)
                .build());
    }

    /**
     * Tính lại trạng thái thanh toán của đơn tổng từ các đơn con.
     * Khóa đơn tổng trước để hai đơn con của hai cửa hàng chốt cùng lúc không ghi đè kết quả của nhau.
     */
    private void refreshParentPaymentStatus(Integer maDH) {
        DonHang parent = donHangRepository.findByIdForUpdate(maDH)
                .orElseThrow(() -> new BusinessException("Đơn hàng không tồn tại"));

        List<DonHangCuaHang> children = donHangCuaHangRepository.findAllByDonHang_MaDHOrderByMaDHCHAsc(maDH);

        long delivered = children.stream()
                .filter(c -> TrangThaiDonHang.DA_GIAO.name().equals(c.getTrangThaiDonHang()))
                .count();
        boolean allFinal = children.stream().allMatch(c -> {
            TrangThaiDonHang st = TrangThaiDonHang.fromName(c.getTrangThaiDonHang());
            return st != null && st.isFinal();
        });

        TrangThaiThanhToan status;
        if (PhuongThucThanhToan.laTraTruoc(parent.getPhuongThucTT())) {
            // Chuyển khoản: khách đã trả đủ từ đầu. Trạng thái chỉ đổi theo phần phải hoàn lại.
            long hoanLai = children.stream()
                    .filter(c -> TrangThaiDonHang.DA_HUY.name().equals(c.getTrangThaiDonHang())
                            || TrangThaiDonHang.DA_HOAN_TRA.name().equals(c.getTrangThaiDonHang()))
                    .count();
            if (hoanLai == 0) {
                status = TrangThaiThanhToan.DA_THANH_TOAN;
            } else {
                status = hoanLai == children.size()
                        ? TrangThaiThanhToan.KHONG_THANH_TOAN
                        : TrangThaiThanhToan.THANH_TOAN_MOT_PHAN;
            }
        } else if (delivered == children.size()) {
            status = TrangThaiThanhToan.DA_THANH_TOAN;
        } else if (delivered > 0) {
            status = TrangThaiThanhToan.THANH_TOAN_MOT_PHAN;
        } else {
            status = allFinal ? TrangThaiThanhToan.KHONG_THANH_TOAN : TrangThaiThanhToan.CHO_THANH_TOAN;
        }

        parent.setTrangThaiThanhToan(status.name());
        donHangRepository.save(parent);
    }

    private static boolean laTraTruoc(DonHangCuaHang order) {
        return PhuongThucThanhToan.laTraTruoc(order.getDonHang().getPhuongThucTT());
    }

    /** COD chưa thu tiền của khách nên không phải hoàn; chuyển khoản thì hoàn cả tiền hàng lẫn phí vận chuyển. */
    private static BigDecimal tienPhaiHoan(DonHangCuaHang order) {
        return laTraTruoc(order) ? nz(order.getTongTienShopOrder()) : BigDecimal.ZERO;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
