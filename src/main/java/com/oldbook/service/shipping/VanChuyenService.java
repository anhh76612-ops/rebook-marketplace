package com.oldbook.service.shipping;

import com.oldbook.constant.order.TrangThaiDonHang;
import com.oldbook.constant.shipping.TrangThaiVanChuyen;
import com.oldbook.dto.shipping.*;
import com.oldbook.entity.order.DonHangCuaHang;
import com.oldbook.entity.shipping.DonViVanChuyen;
import com.oldbook.entity.shipping.VanChuyen;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.repository.order.DonHangCuaHangRepository;
import com.oldbook.repository.shipping.VanChuyenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Nghiệp vụ phía đơn vị vận chuyển. Mọi hàm đều nhận maCode (lấy từ URL /api/shipping/{maCode}/...)
 * và chỉ làm việc trên vận đơn có ma_dvvc trùng với hãng đó: hãng A không xem/cập nhật được đơn của hãng B.
 *
 * Luồng: khách chọn hãng khi đặt hàng -> cửa hàng xác nhận (StoreOrderService.confirmOrder tạo vận đơn
 * CHO_LAY_HANG gắn ma_dvvc) -> đơn tự xuất hiện ở /shipping/{maCode} của đúng hãng -> hãng tiếp nhận, cập nhật.
 */
@Service
@RequiredArgsConstructor
public class VanChuyenService {
    private final VanChuyenRepository vanChuyenRepository;
    private final DonHangCuaHangRepository donHangCuaHangRepository;
    private final VanChuyenTrangThaiHandler trangThaiHandler;
    private final DonViVanChuyenResolver donViResolver;

    /** Thông tin hãng để trang /shipping/{maCode} hiển thị tiêu đề. */
    @Transactional(readOnly = true)
    public DonViVanChuyenResponse getDonVi(String maCode) {
        return DonViVanChuyenResponse.from(donViResolver.require(maCode));
    }

    /**
     * Đơn vị vận chuyển tiếp nhận (lấy hàng) một đơn đã được cửa hàng xác nhận.
     * Vận đơn do luồng cửa hàng tạo sẵn khi confirm (trạng thái CHO_LAY_HANG, hãng do khách chọn),
     * nên ở đây KHÔNG tạo vận đơn mới mà chuyển CHO_LAY_HANG -> DANG_GIAO và đồng bộ đơn hàng con.
     */
    @Transactional
    public VanChuyenResponse tiepNhan(String maCode, TiepNhanVanChuyenRequest req) {
        DonViVanChuyen dvvc = donViResolver.requireActive(maCode);

        // Khóa đơn con như StoreOrderService để không đụng độ với cửa hàng cập nhật cùng lúc.
        DonHangCuaHang dhch = donHangCuaHangRepository.findByIdForUpdate(req.getMaDHCH())
                .orElseThrow(() -> new BusinessException("Đơn hàng không tồn tại"));

        VanChuyen vc = vanChuyenRepository.findByDonHangCuaHang_MaDHCH(req.getMaDHCH())
                .orElseThrow(() -> new BusinessException("Đơn hàng không tồn tại"));

        // Đơn của hãng khác: trả cùng thông báo để không lộ đơn ngoài phạm vi của hãng.
        if (!dvvc.getMaDVVC().equals(vc.getDonViVanChuyen().getMaDVVC())) {
            throw new BusinessException("Đơn hàng không tồn tại");
        }

        if (!TrangThaiDonHang.DA_XAC_NHAN.name().equals(dhch.getTrangThaiDonHang())) {
            throw new BusinessException("Chỉ tiếp nhận đơn hàng đã được cửa hàng xác nhận");
        }

        if (TrangThaiVanChuyen.from(vc.getTrangThaiVanChuyen()) != TrangThaiVanChuyen.CHO_LAY_HANG) {
            throw new BusinessException("Yêu cầu vận chuyển đã được tiếp nhận hoặc không ở trạng thái chờ lấy hàng");
        }

        capNhatMaVanDon(vc, req.getMaVanDon());

        // Trạng thái do hệ thống quyết định.
        trangThaiHandler.apply(dhch, vc, TrangThaiVanChuyen.DANG_GIAO, req.getGhiChuGiaoHang(), null);

        return VanChuyenResponse.from(vc);
    }

    /** Cập nhật trạng thái vận chuyển theo đúng các bước chuyển hợp lệ (dùng chung state machine với cửa hàng). */
    @Transactional
    public VanChuyenResponse capNhat(String maCode, Integer id, CapNhatVanChuyenRequest req) {
        DonViVanChuyen dvvc = donViResolver.requireActive(maCode);

        // Khóa đơn con trước rồi mới đọc vận đơn, giống StoreOrderService.updateShippingStatus.
        Integer maDHCH = vanChuyenRepository.findMaDHCHByIdAndDonVi(id, dvvc.getMaDVVC())
                .orElseThrow(() -> new BusinessException("Vận chuyển không tồn tại"));
        DonHangCuaHang dhch = donHangCuaHangRepository.findByIdForUpdate(maDHCH)
                .orElseThrow(() -> new BusinessException("Đơn hàng cửa hàng không tồn tại"));
        VanChuyen vc = vanChuyenRepository.findByDonHangCuaHang_MaDHCH(maDHCH)
                .orElseThrow(() -> new BusinessException("Vận chuyển không tồn tại"));

        TrangThaiVanChuyen next = trangThaiHandler.parse(req.getTrangThai());
        trangThaiHandler.apply(dhch, vc, next, req.getGhiChu(), req.getTienHoanTra());
        capNhatMaVanDon(vc, req.getMaVanDon());

        return VanChuyenResponse.from(vanChuyenRepository.save(vc));
    }

    /** Báo kết quả giao hàng: chỉ nhận DA_GIAO, GIAO_THAT_BAI hoặc HOAN_TRA. */
    @Transactional
    public VanChuyenResponse capNhatKetQua(String maCode, Integer id, CapNhatVanChuyenRequest req) {
        TrangThaiVanChuyen next = trangThaiHandler.parse(req.getTrangThai());
        if (!next.laKetQua()) {
            throw new BusinessException("Kết quả giao hàng chỉ gồm: DA_GIAO, GIAO_THAT_BAI hoặc HOAN_TRA");
        }
        return capNhat(maCode, id, req);
    }

    // Mã vận đơn do hệ thống sinh khi cửa hàng xác nhận; chỉ đổi khi hãng gửi mã mới và không trùng.
    private void capNhatMaVanDon(VanChuyen vc, String maVanDon) {
        String code = maVanDon == null ? null : maVanDon.trim();
        if (code == null || code.isEmpty() || code.equals(vc.getMaVanDon())) {
            return;
        }
        if (vanChuyenRepository.existsByMaVanDon(code)) {
            throw new BusinessException("Mã vận đơn đã tồn tại");
        }
        vc.setMaVanDon(code);
    }

    /** Các đơn của hãng này đã được cửa hàng xác nhận và đang chờ đến lấy hàng. */
    @Transactional(readOnly = true)
    public List<YeuCauVanChuyenResponse> getYeuCauChuaTiepNhan(String maCode) {
        DonViVanChuyen dvvc = donViResolver.require(maCode);
        return vanChuyenRepository.findAllByDonViVanChuyen_MaDVVCOrderByMaVanChuyenDesc(dvvc.getMaDVVC()).stream()
                .filter(v -> TrangThaiVanChuyen.from(v.getTrangThaiVanChuyen()) == TrangThaiVanChuyen.CHO_LAY_HANG)
                .filter(v -> TrangThaiDonHang.DA_XAC_NHAN.name().equals(v.getDonHangCuaHang().getTrangThaiDonHang()))
                .map(v -> {
                    DonHangCuaHang d = v.getDonHangCuaHang();
                    return YeuCauVanChuyenResponse.builder()
                            .maDHCH(d.getMaDHCH())
                            .maDH(d.getDonHang().getMaDH())
                            .maDVVC(dvvc.getMaDVVC())
                            .maCode(dvvc.getMaCode())
                            .tenDonVi(dvvc.getTenDonVi())
                            .trangThaiDonHang(d.getTrangThaiDonHang())
                            .trangThaiThanhToan(d.getTrangThaiThanhToanShop())
                            .tongTien(d.getTongTienShopOrder())
                            .phiShip(d.getPhiShipCH())
                            .ngayCapNhat(d.getNgayCapNhat())
                            .ghiChu(d.getGhiChu())
                            .build();
                })
                .toList();
    }

    /** Toàn bộ vận đơn của hãng này. */
    @Transactional(readOnly = true)
    public List<VanChuyenResponse> getAll(String maCode) {
        DonViVanChuyen dvvc = donViResolver.require(maCode);
        return vanChuyenRepository.findAllByDonViVanChuyen_MaDVVCOrderByMaVanChuyenDesc(dvvc.getMaDVVC()).stream()
                .map(VanChuyenResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public VanChuyenResponse getById(String maCode, Integer id) {
        DonViVanChuyen dvvc = donViResolver.require(maCode);
        return vanChuyenRepository.findByMaVanChuyenAndDonViVanChuyen_MaDVVC(id, dvvc.getMaDVVC())
                .map(VanChuyenResponse::from)
                .orElseThrow(() -> new BusinessException("Vận chuyển không tồn tại"));
    }
}
