package com.oldbook.dto.shipping;

import com.oldbook.constant.shipping.TrangThaiVanChuyen;
import com.oldbook.entity.shipping.VanChuyen;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

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
    private String tenTrangThai;
    private String trangThaiDonHang;
    private Integer soLanGiaoThatBai;
    private String ghiChuGiaoHang;
    private LocalDateTime ngayCapNhat;
    private String lyDoHuy;

    /** Các trạng thái được phép chuyển tới (lấy từ TrangThaiVanChuyen.canMoveTo); giao diện không tự giữ bảng riêng. */
    private List<String> trangThaiTiepTheo;

    public static VanChuyenResponse from(VanChuyen e) {
        TrangThaiVanChuyen st = TrangThaiVanChuyen.fromName(e.getTrangThaiVanChuyen());
        return VanChuyenResponse.builder()
                .maVanChuyen(e.getMaVanChuyen())
                .maDHCH(e.getDonHangCuaHang().getMaDHCH())
                .maDH(e.getDonHangCuaHang().getDonHang().getMaDH())
                .maDVVC(e.getDonViVanChuyen().getMaDVVC())
                .maCode(e.getDonViVanChuyen().getMaCode())
                .tenDonVi(e.getDonViVanChuyen().getTenDonVi())
                .maVanDon(e.getMaVanDon())
                .trangThaiVanChuyen(e.getTrangThaiVanChuyen())
                .tenTrangThai(st == null ? e.getTrangThaiVanChuyen() : st.getTenHienThi())
                .trangThaiDonHang(e.getDonHangCuaHang().getTrangThaiDonHang())
                .soLanGiaoThatBai(e.getSoLanGiaoThatBai())
                .ghiChuGiaoHang(e.getGhiChuGiaoHang())
                .ngayCapNhat(e.getNgayCapNhat())
                .lyDoHuy(e.getDonHangCuaHang().getLyDoHuy())
                .trangThaiTiepTheo(st == null ? List.of() : Arrays.stream(TrangThaiVanChuyen.values())
                        .filter(st::canMoveTo)
                        .map(Enum::name)
                        .toList())
                .build();
    }
}
