package com.nhom9.oldbook_marketplace.entity.catalog;

import com.nhom9.oldbook_marketplace.entity.identity.NguoiDung;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "yeu_thich")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class YeuThich {

    @EmbeddedId
    private UserBookKey id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("maND") // Ánh xạ tới maND trong UserBookKey
    @JoinColumn(name = "ma_nd")
    private NguoiDung nguoiDung;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("maSach") // Ánh xạ tới maSach trong UserBookKey
    @JoinColumn(name = "ma_sach")
    private Sach sach;

    @Column(name = "ngay_them", updatable = false)
    private LocalDateTime ngayThem;

    @PrePersist
    protected void onCreate() {
        this.ngayThem = LocalDateTime.now();
    }
}