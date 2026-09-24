package com.nhom9.oldbook_marketplace.entity.payment;

import com.nhom9.oldbook_marketplace.entity.identity.NguoiDung;
import com.nhom9.oldbook_marketplace.entity.order.DonHang;
import com.nhom9.oldbook_marketplace.entity.order.DonHangCuaHang;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "su_dung_voucher")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuDungVoucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_sdv")
    private Integer maSDV;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ma_km", referencedColumnName = "ma_km", nullable = false)
    private KhuyenMai khuyenMai;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ma_dh", referencedColumnName = "ma_dh", nullable = false)
    private DonHang donHang;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ma_dhch", referencedColumnName = "ma_dhch", nullable = true)
    private DonHangCuaHang donHangCuaHang;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ma_nd", referencedColumnName = "ma_nd", nullable = false)
    private NguoiDung nguoiDung;

    @Column(name = "gia_tri_giam_thuc_te", precision = 15, scale = 2)
    private BigDecimal giaTriGiamThucTe;

    @Column(name = "ngay_su_dung", updatable = false)
    private LocalDateTime ngaySuDung;

    @PrePersist
    protected void onCreate() {
        this.ngaySuDung = LocalDateTime.now();
    }
}