package com.oldbook.dto.catalog;

import java.time.LocalDateTime;
import java.util.List;

public record DanhGiaResponse(
        Integer maDanhGia,
        String hoTen,
        Integer soSao,
        String binhLuan,
        LocalDateTime ngayDanhGia,
        List<DanhGiaMediaResponse> media
) {
    public DanhGiaResponse {
        media = media == null ? List.of() : List.copyOf(media);
    }

    public DanhGiaResponse(Integer maDanhGia, String hoTen, Integer soSao,
                           String binhLuan, LocalDateTime ngayDanhGia) {
        this(maDanhGia, hoTen, soSao, binhLuan, ngayDanhGia, List.of());
    }
}
