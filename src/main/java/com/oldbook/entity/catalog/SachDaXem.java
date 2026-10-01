package com.oldbook.entity.catalog;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

import com.oldbook.entity.identity.NguoiDung;

@Entity
@Table(name = "sach_da_xem")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SachDaXem {

    @EmbeddedId
    private UserBookKey id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("maND")
    @JoinColumn(name = "ma_nd")
    private NguoiDung nguoiDung;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("maSach")
    @JoinColumn(name = "ma_sach")
    private Sach sach;

    @Column(name = "lan_xem_gan_nhat")
    private LocalDateTime lanXemGanNhat;

    // Tự động cập nhật thời gian mỗi khi xem lại sách
    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.lanXemGanNhat = LocalDateTime.now();
    }
}