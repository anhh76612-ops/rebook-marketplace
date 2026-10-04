package com.oldbook.dto.shipping;

import com.oldbook.entity.shipping.VanChuyen;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class VanChuyenResponse {
    private Integer maVanChuyen;
    private Integer maDHCH;
    private Integer maDH;
    private Integer maDVVC;
    private String maCode;
    private String tenDonVi;
    private String maVanDon;
    private String trangThaiVanChuyen;
    private Integer soLanGiaoThatBai;
    private String ghiChuGiaoHang;
    private LocalDateTime ngayCapNhat;

    public static VanChuyenResponse from(VanChuyen e) {
        return VanChuyenResponse.builder()
                .maVanChuyen(e.getMaVanChuyen())
                .maDHCH(e.getDonHangCuaHang().getMaDHCH())
                .maDH(e.getDonHangCuaHang().getDonHang().getMaDH())
                .maDVVC(e.getDonViVanChuyen().getMaDVVC())
                .maCode(e.getDonViVanChuyen().getMaCode())
                .tenDonVi(e.getDonViVanChuyen().getTenDonVi())
                .maVanDon(e.getMaVanDon())
                .trangThaiVanChuyen(e.getTrangThaiVanChuyen())
                .soLanGiaoThatBai(e.getSoLanGiaoThatBai())
                .ghiChuGiaoHang(e.getGhiChuGiaoHang())
                .ngayCapNhat(e.getNgayCapNhat())
                .build();
    }
}
