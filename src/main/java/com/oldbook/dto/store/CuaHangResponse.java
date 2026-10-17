package com.oldbook.dto.store;

import com.oldbook.entity.catalog.CuaHang;

import java.time.LocalDateTime;

public record CuaHangResponse(
        Integer maCH,
        String tenCuaHang,
        String moTa,
        String diaChiCH,
        String soDienThoaiCH,
        String trangThaiDuyet,
        LocalDateTime ngayTao,
        String lyDoTuChoi,
        String maNganHang,
        String soTaiKhoan,
        String tenChuTaiKhoan
) {
    public static CuaHangResponse from(CuaHang cuaHang) {
        return new CuaHangResponse(cuaHang.getMaCH(), cuaHang.getTenCuaHang(), cuaHang.getMoTa(),
                cuaHang.getDiaChiCH(), cuaHang.getSoDienThoaiCH(), cuaHang.getTrangThaiDuyet(),
                cuaHang.getNgayTao(), null,
                cuaHang.getMaNganHang(), cuaHang.getSoTaiKhoan(), cuaHang.getTenChuTaiKhoan());
    }

    public CuaHangResponse withLyDoTuChoi(String lyDo) {
        return new CuaHangResponse(maCH, tenCuaHang, moTa, diaChiCH, soDienThoaiCH,
                trangThaiDuyet, ngayTao, lyDo, maNganHang, soTaiKhoan, tenChuTaiKhoan);
    }
}
