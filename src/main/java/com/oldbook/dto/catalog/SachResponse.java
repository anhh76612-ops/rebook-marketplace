package com.oldbook.dto.catalog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@AllArgsConstructor
public class SachResponse {

    private Integer maSach;

    private String tenSach;

    private String tacGia;

    private String nhaXuatBan;

    private Integer namXuatBan;

    private BigDecimal giaBia;

    private BigDecimal giaBanCu;

    private BigDecimal doMoiPercent;

    private String tinhTrangVatLy;

    private Integer soLuongTon;

    private String hinhAnhUrl;

    private Integer maCH;

    private String tenCuaHang;

    private Integer maDM;

    private String tenDanhMuc;
}
