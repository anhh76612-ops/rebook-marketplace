package com.oldbook.dto.notification;

import com.oldbook.entity.notification.ThongBao;

import java.time.LocalDateTime;

public record ThongBaoResponse(
        Integer maThongBao,
        String loai,
        String mucDo,
        String tieuDe,
        String noiDung,
        String duongDan,
        boolean daDoc,
        LocalDateTime ngayTao
) {

    public static ThongBaoResponse from(ThongBao thongBao) {
        return new ThongBaoResponse(
                thongBao.getMaThongBao(),
                thongBao.getLoai(),
                thongBao.getMucDo(),
                thongBao.getTieuDe(),
                thongBao.getNoiDung(),
                thongBao.getDuongDan(),
                thongBao.isDaDoc(),
                thongBao.getNgayTao()
        );
    }
}
