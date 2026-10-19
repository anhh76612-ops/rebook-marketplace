package com.oldbook.service.management;

import com.oldbook.constant.catalog.TrangThaiBan;
import com.oldbook.dto.management.QuanLyDto.HoaHongDanhMuc;
import com.oldbook.dto.management.QuanLyDto.HoaHongLichSu;
import com.oldbook.entity.catalog.DanhMuc;
import com.oldbook.entity.payment.CauHinhChietKhau;
import com.oldbook.entity.system.NhatKyHeThong;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.repository.catalog.DanhMucRepository;
import com.oldbook.repository.identity.TaiKhoanRepository;
import com.oldbook.repository.payment.CauHinhChietKhauRepository;
import com.oldbook.repository.system.NhatKyHeThongRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Quản lý tỷ lệ hoa hồng theo danh mục (bảng cau_hinh_chiet_khau).
 *
 * Tỷ lệ được lấy lúc đơn GIAO THÀNH CÔNG (OrderSettlementService), không phải lúc đặt, nên tỷ lệ mới áp cho
 * các đơn giao thành công sau thời điểm đổi. Mỗi lần đổi đóng bản ghi đang hiệu lực rồi thêm bản ghi mới, giữ nguyên lịch sử.
 */
@Service
@RequiredArgsConstructor
public class QuanLyHoaHongService {

    public static final String HOAT_DONG = "HOAT_DONG";
    public static final String NGUNG = "NGUNG_AP_DUNG";

    private static final BigDecimal TRAM = BigDecimal.valueOf(100);

    private final DanhMucRepository danhMucRepository;
    private final CauHinhChietKhauRepository cauHinhRepository;
    private final TaiKhoanRepository taiKhoanRepository;
    private final NhatKyHeThongRepository nhatKyRepository;
    private final ApplicationEventPublisher eventPublisher;

    @PersistenceContext
    private EntityManager em;

    @Value("${marketplace.commission.default-rate:5.00}")
    private BigDecimal tyLeMacDinh;

    @Transactional(readOnly = true)
    public List<HoaHongDanhMuc> danhSach() {
        LocalDateTime now = LocalDateTime.now();
        Map<Integer, Long> soCuaHang = soCuaHangTheoDanhMuc();

        return danhMucRepository.findAll().stream()
                .sorted((a, b) -> a.getTenDanhMuc().compareToIgnoreCase(b.getTenDanhMuc()))
                .map(dm -> {
                    CauHinhChietKhau cfg = hieuLuc(dm.getMaDM(), now);
                    long ch = soCuaHang.getOrDefault(dm.getMaDM(), 0L);
                    if (cfg == null) {
                        return new HoaHongDanhMuc(dm.getMaDM(), dm.getTenDanhMuc(), tyLeMacDinh, true, ch,
                                null, null, null, null);
                    }
                    return new HoaHongDanhMuc(dm.getMaDM(), dm.getTenDanhMuc(), cfg.getTyLeChietKhau(), false, ch,
                            cfg.getNgayBatDau(), cfg.getNgayBatDau(), tenNguoi(cfg.getMaTKCapNhat()), cfg.getGhiChu());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HoaHongLichSu> lichSu(Integer maDM) {
        danhMuc(maDM);
        return em.createQuery("""
                        select c from CauHinhChietKhau c where c.danhMuc.maDM = :maDM
                        order by c.ngayBatDau desc, c.maCauHinhChietKhau desc
                        """, CauHinhChietKhau.class)
                .setParameter("maDM", maDM)
                .getResultList().stream()
                .map(c -> new HoaHongLichSu(c.getMaCauHinhChietKhau(), c.getTyLeChietKhau(), c.getTrangThai(),
                        c.getNgayBatDau(), c.getNgayKetThuc(), tenNguoi(c.getMaTKCapNhat()), c.getGhiChu()))
                .toList();
    }

    @Transactional
    public HoaHongDanhMuc doiTyLe(Integer maTK, Integer maDM, BigDecimal tyLe, String ghiChu) {
        DanhMuc dm = danhMuc(maDM);
        if (tyLe == null || tyLe.signum() < 0 || tyLe.compareTo(TRAM) > 0) {
            throw new BusinessException("Tỷ lệ hoa hồng phải từ 0 đến 100%");
        }
        BigDecimal moi = tyLe.setScale(2, RoundingMode.HALF_UP);
        String note = ghiChu == null || ghiChu.isBlank() ? null : ghiChu.trim();
        if (note != null && note.length() > 500) {
            throw new BusinessException("Ghi chú tối đa 500 ký tự");
        }

        LocalDateTime now = LocalDateTime.now();
        CauHinhChietKhau cu = hieuLuc(maDM, now);
        BigDecimal tyLeCu = cu == null ? tyLeMacDinh : cu.getTyLeChietKhau();
        if (cu != null && tyLeCu.compareTo(moi) == 0) {
            throw new BusinessException("Tỷ lệ mới trùng với tỷ lệ đang áp dụng");
        }

        // Đóng mọi bản ghi đang hiệu lực (thường chỉ một) rồi thêm bản ghi mới.
        for (CauHinhChietKhau c : cauHinhRepository.findEffective(maDM, HOAT_DONG, now)) {
            c.setNgayKetThuc(now);
            c.setTrangThai(NGUNG);
            cauHinhRepository.save(c);
        }
        cauHinhRepository.save(CauHinhChietKhau.builder()
                .danhMuc(dm).tyLeChietKhau(moi).trangThai(HOAT_DONG).ngayBatDau(now)
                .maTKCapNhat(maTK).ghiChu(note).build());

        nhatKyRepository.save(NhatKyHeThong.builder()
                .taiKhoan(taiKhoanRepository.getReferenceById(maTK))
                .hanhDong("DOI_HOA_HONG").loaiDoiTuong("DANH_MUC").maDoiTuong(maDM)
                .moTa("Danh mục \"" + dm.getTenDanhMuc() + "\": " + tyLeCu + "% -> " + moi + "%"
                        + (note == null ? "" : ". Ghi chú: " + note))
                .build());

        eventPublisher.publishEvent(new VanHanhThayDoiEvent(VanHanhThayDoiEvent.COMMISSIONS));

        return danhSach().stream().filter(h -> h.maDM().equals(maDM)).findFirst().orElseThrow();
    }

    // ---------------------------------------------------------------

    private DanhMuc danhMuc(Integer maDM) {
        return danhMucRepository.findById(maDM).orElseThrow(() -> new BusinessException("Danh mục không tồn tại"));
    }

    private CauHinhChietKhau hieuLuc(Integer maDM, LocalDateTime now) {
        return cauHinhRepository.findEffective(maDM, HOAT_DONG, now).stream()
                .filter(c -> c.getTyLeChietKhau() != null).findFirst().orElse(null);
    }

    private String tenNguoi(Integer maTK) {
        if (maTK == null) {
            return null;
        }
        return taiKhoanRepository.findById(maTK).map(t -> t.getNguoiDung().getHoTen()).orElse(null);
    }

    /** Số cửa hàng có sách đang bán trong từng danh mục. */
    private Map<Integer, Long> soCuaHangTheoDanhMuc() {
        List<Object[]> rows = em.createQuery("""
                        select s.danhMuc.maDM, count(distinct s.cuaHang.maCH) from Sach s
                        where s.trangThaiBan = :ban group by s.danhMuc.maDM
                        """, Object[].class)
                .setParameter("ban", TrangThaiBan.DANG_BAN.name())
                .getResultList();
        return rows.stream().collect(Collectors.toMap(r -> (Integer) r[0], r -> ((Number) r[1]).longValue(), (a, b) -> a));
    }
}
