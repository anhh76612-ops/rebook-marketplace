package com.oldbook.entity.payment;

import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.entity.identity.TaiKhoan;
import com.oldbook.entity.order.DonHangCuaHang;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Một khoản tiền sàn phải chuyển đi: hoàn cho khách hoặc chi cho cửa hàng.
 * Hệ thống không tự chuyển được tiền; Quản lý chuyển khoản bằng tài khoản của sàn rồi đánh dấu ở đây.
 */
@Entity
@Table(
        name = "yeu_cau_chuyen_tien",
        indexes = {
                @Index(name = "ix_yc_chuyen_tien_loai_trang_thai", columnList = "loai, trang_thai"),
                @Index(name = "ix_yc_chuyen_tien_nguoi_nhan", columnList = "ma_nd_nhan")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class YeuCauChuyenTien {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_yeu_cau")
    private Integer maYeuCau;

    // Tên của LoaiChuyenTien
    @Column(name = "loai", nullable = false, length = 30)
    private String loai;

    // Đơn hàng con phát sinh khoản này. Trống khi hoàn tiền cho một phiên không tạo được đơn.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ma_dhch", referencedColumnName = "ma_dhch")
    private DonHangCuaHang donHangCuaHang;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ma_phien", referencedColumnName = "ma_phien")
    private PhienThanhToan phien;

    // Giao dịch tiền vào phải hoàn lại. Dùng để không lập hai khoản hoàn cho cùng một giao dịch.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ma_giao_dich", referencedColumnName = "ma_giao_dich")
    private GiaoDichNganHang giaoDichNganHang;

    // Khách được hoàn tiền, hoặc chủ của cửa hàng được chi tiền.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ma_nd_nhan", referencedColumnName = "ma_nd", nullable = false)
    private NguoiDung nguoiNhan;

    @Column(name = "so_tien", nullable = false, precision = 15, scale = 2)
    private BigDecimal soTien;

    // Tên của TrangThaiChuyenTien
    @Column(name = "trang_thai", nullable = false, length = 30)
    private String trangThai;

    // Tài khoản nhận của khách (chỉ dùng cho hoàn tiền). Tài khoản của cửa hàng đọc từ bảng cua_hang.
    @Column(name = "ma_ngan_hang", length = 20)
    private String maNganHang;

    @Column(name = "so_tai_khoan", length = 50)
    private String soTaiKhoan;

    @Nationalized
    @Column(name = "ten_chu_tai_khoan", length = 255)
    private String tenChuTaiKhoan;

    @Nationalized
    @Column(name = "ly_do", length = 500)
    private String lyDo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ma_tk_xu_ly", referencedColumnName = "ma_tk")
    private TaiKhoan nguoiXuLy;

    @Column(name = "ngay_xu_ly")
    private LocalDateTime ngayXuLy;

    @Column(name = "ngay_tao", nullable = false, updatable = false)
    private LocalDateTime ngayTao;

    @PrePersist
    protected void onCreate() {
        if (this.ngayTao == null) {
            this.ngayTao = LocalDateTime.now();
        }
    }
}
