package com.oldbook.service.catalog;

import com.oldbook.dto.catalog.SachResponse;
import com.oldbook.entity.catalog.Sach;
import com.oldbook.entity.catalog.SachHinhAnh;

import java.util.List;

public final class SachMapper {
    private SachMapper() {
    }

    public static SachResponse toResponse(Sach sach) {
        return toResponse(sach, true);
    }

    public static SachResponse toPublicResponse(Sach sach) {
        return toResponse(sach, false);
    }

    private static SachResponse toResponse(Sach sach, boolean includeRejectionReason) {
        return new SachResponse(
                sach.getMaSach(), sach.getCuaHang().getMaCH(), sach.getCuaHang().getTenCuaHang(),
                sach.getDanhMuc().getMaDM(), sach.getDanhMuc().getTenDanhMuc(),
                sach.getTenSach(), sach.getTacGia(), sach.getNhaXuatBan(), sach.getNamXuatBan(),
                sach.getGiaBia(), sach.getGiaBanCu(), sach.getDoMoiPercent(),
                sach.getTinhTrangVatLy(), sach.getMoTaChiTiet(), sach.getSoLuongTon() == null ? 0 : sach.getSoLuongTon(),
                sach.getHinhAnhUrl(), imageUrls(sach), sach.getTrangThaiDuyet(), sach.getTrangThaiBan(),
                includeRejectionReason ? sach.getLyDoTuChoi() : null, sach.getNgayTao()
        );
    }

    private static List<String> imageUrls(Sach sach) {
        List<SachHinhAnh> images = sach.getHinhAnhs();
        if (images == null || images.isEmpty()) {
            return sach.getHinhAnhUrl() == null ? List.of() : List.of(sach.getHinhAnhUrl());
        }
        return images.stream().map(SachHinhAnh::getUrl).toList();
    }
}
