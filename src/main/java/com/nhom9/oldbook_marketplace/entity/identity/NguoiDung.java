package com.nhom9.oldbook_marketplace.entity.identity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "nguoi_dung")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NguoiDung {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_nd")
    private Integer maND;

    // Quan hệ 1-1: Một người dùng gắn với một tài khoản duy nhất
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ma_tk", referencedColumnName = "ma_tk", unique = true, nullable = false)
    private TaiKhoan taiKhoan;

    @Column(name = "ho_ten", length = 255)
    private String hoTen;

    @Column(name = "so_dien_thoai", unique = true, length = 20)
    private String soDienThoai;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "ngay_tao", updatable = false)
    private LocalDateTime ngayTao;

    @PrePersist
    protected void onCreate() {
        this.ngayTao = LocalDateTime.now();
    }
}