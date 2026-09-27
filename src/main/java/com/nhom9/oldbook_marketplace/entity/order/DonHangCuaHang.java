package com.nhom9.oldbook_marketplace.entity.order;

import com.nhom9.oldbook_marketplace.entity.catalog.CuaHang;
import com.nhom9.oldbook_marketplace.entity.payment.KhuyenMai;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "don_hang_cua_hang",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_don_hang_cua_hang",
                        columnNames = {"ma_dh", "ma_ch"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DonHangCuaHang {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_dhch")
    private Integer maDHCH;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ma_dh", referencedColumnName = "ma_dh", nullable = false)
    private DonHang donHang;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ma_ch", referencedColumnName = "ma_ch", nullable = false)
    private CuaHang cuaHang;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "ma_km_shop",
            referencedColumnName = "ma_km"
    )
    private KhuyenMai khuyenMaiShop;

    @Column(name = "giam_gia_shop", precision = 15, scale = 2)
    private BigDecimal giamGiaShop;

    @Column(name = "giam_gia_san_phan_bo", precision = 15, scale = 2)
    private BigDecimal giamGiaSanPhanBo;

    @Column(name = "tien_hang_ch", precision = 15, scale = 2)
    private BigDecimal tienHangCH;

    @Column(name = "phi_ship_ch", precision = 15, scale = 2)
    private BigDecimal phiShipCH;

    @Column(name = "commission_tien", precision = 15, scale = 2)
    private BigDecimal commissionTien;

    @Column(name = "tong_tien_shop_order", precision = 15, scale = 2)
    private BigDecimal tongTienShopOrder;

    @Column(name = "trang_thai_don_hang", length = 50)
    private String trangThaiDonHang;

    @Column(name = "trang_thai_thanh_toan_shop", length = 50)
    private String trangThaiThanhToanShop;

    @Column(name = "tien_hoan_tra", precision = 15, scale = 2)
    private BigDecimal tienHoanTra;

    @Column(name = "ngay_cap_nhat")
    private LocalDateTime ngayCapNhat;

    @Column(name = "ghi_chu", columnDefinition = "TEXT")
    private String ghiChu;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.ngayCapNhat = LocalDateTime.now();
    }
}