package com.oldbook.service.payment;

import com.oldbook.constant.order.PhuongThucThanhToan;
import com.oldbook.constant.payment.KetQuaDoiSoat;
import com.oldbook.dto.payment.TaiKhoanNhanRequest;
import com.oldbook.entity.order.DonHang;
import com.oldbook.entity.payment.PhienThanhToan;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.repository.order.DonHangRepository;
import com.oldbook.repository.payment.GiaoDichNganHangRepository;
import com.oldbook.repository.payment.PhienThanhToanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tài khoản nhận hoàn tiền khách chọn lúc hủy đơn chuyển khoản.
 *
 * Lưu ở phiên thanh toán của đơn (không đụng tới yêu cầu hủy đơn), nên dùng được cho cả "Hủy đơn" (hủy ngay) lẫn
 * "Gửi yêu cầu hủy" (lúc đó chưa có yêu cầu hoàn tiền). Khi lập khoản hoàn, ChuyenTienService đọc lại các cột này.
 */
@Service
@RequiredArgsConstructor
public class TaiKhoanHoanDonService {

    private final DonHangRepository donHangRepository;
    private final PhienThanhToanRepository phienRepository;
    private final GiaoDichNganHangRepository giaoDichRepository;

    /** Thông tin cho hộp thoại hủy đơn: tài khoản hệ thống đã biết và tài khoản hoàn khách đã chọn (nếu có). */
    public record ThongTin(
            boolean chuyenKhoan,
            // Tài khoản người chuyển trong giao dịch ngân hàng, số tài khoản đã che.
            String maNganHangDaChuyen,
            String soTaiKhoanDaChuyenRutGon,
            String tenNguoiChuyen,
            // Tài khoản hoàn khách đã chọn lúc hủy đơn.
            String maNganHangUuTien,
            String soTaiKhoanUuTien,
            String tenChuTaiKhoanUuTien,
            String qrUuTien
    ) {
    }

    @Transactional(readOnly = true)
    public ThongTin thongTin(Integer maND, Integer maDH) {
        DonHang donHang = donCuaKhach(maND, maDH);
        if (!PhuongThucThanhToan.laTraTruoc(donHang.getPhuongThucTT())) {
            return new ThongTin(false, null, null, null, null, null, null, null);
        }
        PhienThanhToan phien = phienRepository.findFirstByDonHang_MaDH(maDH).orElse(null);
        if (phien == null) {
            return new ThongTin(true, null, null, null, null, null, null, null);
        }
        var giaoDich = giaoDichRepository
                .findFirstByPhien_MaPhienAndKetQuaOrderByMaGiaoDichDesc(phien.getMaPhien(), KetQuaDoiSoat.KHOP.name())
                .filter(g -> g.getSoTaiKhoanChuyen() != null && !g.getSoTaiKhoanChuyen().isBlank())
                .orElse(null);
        return new ThongTin(true,
                giaoDich == null ? null : giaoDich.getMaNganHangChuyen(),
                giaoDich == null ? null : rutGon(giaoDich.getSoTaiKhoanChuyen()),
                giaoDich == null ? null : giaoDich.getTenNguoiChuyen(),
                phien.getHoanMaNganHang(), phien.getHoanSoTaiKhoan(), phien.getHoanTenChuTaiKhoan(), phien.getHoanQrUrl());
    }

    @Transactional
    public ThongTin luu(Integer maND, Integer maDH, TaiKhoanNhanRequest request) {
        DonHang donHang = donCuaKhach(maND, maDH);
        if (!PhuongThucThanhToan.laTraTruoc(donHang.getPhuongThucTT())) {
            throw new BusinessException("Chỉ đơn chuyển khoản mới có khoản hoàn tiền");
        }
        request.kiemTra();
        PhienThanhToan phien = phienRepository.findFirstByDonHang_MaDH(maDH)
                .flatMap(p -> phienRepository.findByIdForUpdate(p.getMaPhien()))
                .orElseThrow(() -> new BusinessException("Không tìm thấy lần thanh toán của đơn này"));
        phien.setHoanMaNganHang(request.maNganHangGon());
        phien.setHoanSoTaiKhoan(request.soTaiKhoanGon());
        phien.setHoanTenChuTaiKhoan(request.tenChuTaiKhoanGon());
        phien.setHoanQrUrl(request.qrGon());
        phienRepository.save(phien);
        return thongTin(maND, maDH);
    }

    private DonHang donCuaKhach(Integer maND, Integer maDH) {
        return donHangRepository.findById(maDH)
                .filter(d -> d.getNguoiMua() != null && maND.equals(d.getNguoiMua().getMaND()))
                .orElseThrow(() -> new BusinessException("Đơn hàng không tồn tại"));
    }

    /** 1234567890 -> ***7890 */
    static String rutGon(String soTaiKhoan) {
        String so = soTaiKhoan.trim();
        return "***" + (so.length() <= 4 ? so : so.substring(so.length() - 4));
    }
}
