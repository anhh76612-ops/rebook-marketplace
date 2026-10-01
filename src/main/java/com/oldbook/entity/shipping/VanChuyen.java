package com.oldbook.entity.shipping;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

import com.oldbook.entity.order.DonHangCuaHang;

@Entity
@Table(name = "van_chuyen")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VanChuyen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_van_chuyen")
    private Integer maVanChuyen;

    // Quan hệ 1-1: Mỗi đơn hàng của shop chỉ có 1 mã vận đơn tương ứng
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ma_dhch", referencedColumnName = "ma_dhch", unique = true, nullable = false)
    private DonHangCuaHang donHangCuaHang;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ma_dvvc", referencedColumnName = "ma_dvvc", nullable = false)
    private DonViVanChuyen donViVanChuyen;

    @Column(name = "ma_van_don", unique = true, length = 255)
    private String maVanDon;

    @Column(name = "trang_thai_van_chuyen", length = 50)
    private String trangThaiVanChuyen;

    @Column(name = "so_lan_giao_that_bai")
    private Integer soLanGiaoThatBai;

    @Column(name = "ghi_chu_giao_hang", columnDefinition = "TEXT")
    private String ghiChuGiaoHang;

    @Column(name = "ngay_cap_nhat")
    private LocalDateTime ngayCapNhat;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.ngayCapNhat = LocalDateTime.now();
    }
}