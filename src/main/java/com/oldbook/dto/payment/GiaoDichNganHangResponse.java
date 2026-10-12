package com.oldbook.dto.payment;

import com.oldbook.entity.payment.GiaoDichNganHang;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GiaoDichNganHangResponse(
        Integer maGiaoDich,
        String maGiaoDichCong,
        BigDecimal soTien,
        String noiDung,
        LocalDateTime thoiGianGiaoDich,
        String tenNguoiChuyen,
        String soTaiKhoanChuyen,
        String maThanhToan,
        String ketQua,
        String ghiChu,
        LocalDateTime ngayNhan
) {

    public static GiaoDichNganHangResponse from(GiaoDichNganHang giaoDich) {
        return new GiaoDichNganHangResponse(
                giaoDich.getMaGiaoDich(),
                giaoDich.getMaGiaoDichCong(),
                giaoDich.getSoTien(),
                giaoDich.getNoiDung(),
                giaoDich.getThoiGianGiaoDich(),
                giaoDich.getTenNguoiChuyen(),
                giaoDich.getSoTaiKhoanChuyen(),
                giaoDich.getPhien() == null ? null : giaoDich.getPhien().getMaThanhToan(),
                giaoDich.getKetQua(),
                giaoDich.getGhiChu(),
                giaoDich.getNgayNhan()
        );
    }
}
