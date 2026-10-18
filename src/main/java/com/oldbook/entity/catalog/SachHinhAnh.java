package com.oldbook.entity.catalog;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sach_hinh_anh")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SachHinhAnh {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_hinh_anh")
    private Integer maHinhAnh;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ma_sach", referencedColumnName = "ma_sach", nullable = false)
    private Sach sach;

    @Column(name = "url", nullable = false, length = 500)
    private String url;

    @Column(name = "thu_tu", nullable = false)
    private Integer thuTu;
}
