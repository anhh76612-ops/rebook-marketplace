package com.oldbook.service.catalog;

import com.oldbook.constant.catalog.TrangThaiBan;
import com.oldbook.constant.catalog.TrangThaiDuyet;
import com.oldbook.dto.catalog.SachResponse;
import com.oldbook.entity.catalog.Sach;
import com.oldbook.repository.catalog.SachRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Service
public class SachService {

    private final SachRepository sachRepository;

    public SachService(SachRepository sachRepository) {
        this.sachRepository = sachRepository;
    }

    @Transactional(readOnly = true)
    public List<SachResponse> timSachCongKhai(String keyword, String category) {

        String tuKhoa = chuanHoa(keyword);
        String danhMuc = chuanHoa(category);

        return sachRepository
                .findCongKhai(
                        TrangThaiDuyet.DA_DUYET.name(),
                        TrangThaiBan.DANG_BAN.name()
                )
                .stream()
                .filter(s -> danhMuc.isEmpty()
                        || chuanHoa(s.getDanhMuc().getTenDanhMuc()).equals(danhMuc))
                .filter(s -> tuKhoa.isEmpty()
                        || chuanHoa(s.getTenSach()).contains(tuKhoa)
                        || chuanHoa(s.getTacGia()).contains(tuKhoa)
                        || chuanHoa(s.getCuaHang().getTenCuaHang()).contains(tuKhoa))
                .map(this::toResponse)
                .toList();
    }

    private SachResponse toResponse(Sach s) {
        return SachResponse.builder()
                .maSach(s.getMaSach())
                .tenSach(s.getTenSach())
                .tacGia(s.getTacGia())
                .nhaXuatBan(s.getNhaXuatBan())
                .namXuatBan(s.getNamXuatBan())
                .giaBia(s.getGiaBia())
                .giaBanCu(s.getGiaBanCu())
                .doMoiPercent(s.getDoMoiPercent())
                .tinhTrangVatLy(s.getTinhTrangVatLy())
                .soLuongTon(s.getSoLuongTon() == null ? 0 : s.getSoLuongTon())
                .hinhAnhUrl(s.getHinhAnhUrl())
                .maCH(s.getCuaHang().getMaCH())
                .tenCuaHang(s.getCuaHang().getTenCuaHang())
                .maDM(s.getDanhMuc().getMaDM())
                .tenDanhMuc(s.getDanhMuc().getTenDanhMuc())
                .build();
    }

    static String chuanHoa(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
