package com.oldbook.dto.catalog;

import java.time.LocalDateTime;

public record DanhGiaResponse(
        Integer maDanhGia,
        String hoTen,
        Integer soSao,
        String binhLuan,
        LocalDateTime ngayDanhGia
) {
}
