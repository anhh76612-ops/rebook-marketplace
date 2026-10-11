package com.oldbook.entity.system;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.entity.order.ChiTietDonHang;

@Entity
@Table(name = "danh_gia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DanhGia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_danh_gia")
    private Integer maDanhGia;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ma_ctdh", referencedColumnName = "ma_ctdh", unique = true, nullable = false)
    private ChiTietDonHang chiTietDonHang;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ma_nd", referencedColumnName = "ma_nd", nullable = false)
    private NguoiDung nguoiDung;

    @Column(name = "so_sao")
    private Integer soSao;

    @Column(name = "binh_luan", columnDefinition = "TEXT")
    private String binhLuan;

    @Column(name = "ngay_danh_gia", updatable = false)
    private LocalDateTime ngayDanhGia;

    @OneToMany(mappedBy = "danhGia", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("maMedia ASC")
    @Builder.Default
    private List<DanhGiaMedia> media = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.ngayDanhGia = LocalDateTime.now();
    }
}
