package com.nhom9.oldbook_marketplace.entity.identity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tai_khoan")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaiKhoan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_tk")
    private Integer maTK;

    @Column(name = "email", unique = true, nullable = false, length = 255)
    private String email;

    @Column(name = "mat_khau", nullable = false, length = 255)
    private String matKhau;

    @Column(name = "vai_tro", length = 50)
    private String vaiTro;

    @Column(name = "trang_thai", length = 50)
    private String trangThai;

    @Column(name = "ngay_tao", updatable = false)
    private LocalDateTime ngayTao;

    // Tự động gán thời gian hiện tại khi tạo mới tài khoản
    @PrePersist
    protected void onCreate() {
        this.ngayTao = LocalDateTime.now();
    }
}