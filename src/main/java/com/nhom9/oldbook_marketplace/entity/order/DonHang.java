package com.nhom9.oldbook_marketplace.entity.order;

import com.nhom9.oldbook_marketplace.entity.identity.DiaChi;
import com.nhom9.oldbook_marketplace.entity.identity.NguoiDung;
import com.nhom9.oldbook_marketplace.entity.payment.KhuyenMai;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "don_hang")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DonHang {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_dh")
    private Integer maDH;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ma_nd_nguoi_mua", referencedColumnName = "ma_nd", nullable = false)
    private NguoiDung nguoiMua;

    // Giữ kiểu Integer cho mã Khuyến Mãi sàn (Sẽ liên kết lỏng với module Payment sau)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "ma_km_san",
            referencedColumnName = "ma_km"
    )
    private KhuyenMai khuyenMaiSan;

    @Column(name = "giam_gia_san", precision = 15, scale = 2)
    private BigDecimal giamGiaSan;

    @Column(name = "tong_tien_hang", precision = 15, scale = 2)
    private BigDecimal tongTienHang;

    @Column(name = "tong_phi_ship", precision = 15, scale = 2)
    private BigDecimal tongPhiShip;

    @Column(name = "tong_giam_gia", precision = 15, scale = 2)
    private BigDecimal tongGiamGia;

    @Column(name = "tong_thanh_toan", precision = 15, scale = 2)
    private BigDecimal tongThanhToan;

    @Column(name = "phuong_thuc_tt", length = 50)
    private String phuongThucTT;

    @Column(name = "trang_thai_thanh_toan", length = 50)
    private String trangThaiThanhToan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ma_dia_chi_giao", referencedColumnName = "ma_dia_chi")
    private DiaChi diaChiGiao;

    // Snapshot thông tin giao hàng
    @Column(name = "ten_nguoi_nhan", length = 255)
    private String tenNguoiNhan;

    @Column(name = "so_dien_thoai_nhan", length = 20)
    private String soDienThoaiNhan;

    @Column(name = "tinh_thanh_giao", length = 100)
    private String tinhThanhGiao;

    @Column(name = "quan_huyen_giao", length = 100)
    private String quanHuyenGiao;

    @Column(name = "phuong_xa_giao", length = 100)
    private String phuongXaGiao;

    @Column(name = "dia_chi_giao_chi_tiet", length = 500)
    private String diaChiGiaoChiTiet;

    @Column(name = "ngay_tao", updatable = false)
    private LocalDateTime ngayTao;

    @PrePersist
    protected void onCreate() {
        this.ngayTao = LocalDateTime.now();
    }
}