package com.nhom9.oldbook_marketplace.shipping.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "don_vi_van_chuyen")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DonViVanChuyen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_dvvc")
    private Integer maDVVC;

    @Column(name = "ten_don_vi", length = 255)
    private String tenDonVi;

    @Column(name = "so_dien_thoai", length = 20)
    private String soDienThoai;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "trang_thai", length = 50)
    private String trangThai;

    @Column(name = "thong_tin_tich_hop", length = 500)
    private String thongTinTichHop;

    @Column(name = "ngay_tao", updatable = false)
    private LocalDate ngayTao;

    @PrePersist
    protected void onCreate() {
        this.ngayTao = LocalDate.now();
    }
}