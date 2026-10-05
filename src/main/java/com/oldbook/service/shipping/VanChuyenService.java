package com.oldbook.service.shipping;

import com.oldbook.constant.order.TrangThaiDonHang;
import com.oldbook.constant.shipping.TrangThaiVanChuyen;
import com.oldbook.dto.shipping.*;
import com.oldbook.entity.order.DonHangCuaHang;
import com.oldbook.entity.shipping.DonViVanChuyen;
import com.oldbook.entity.shipping.VanChuyen;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.repository.shipping.VanChuyenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Cổng đối tác: đơn vị vận chuyển xem và cập nhật đơn của mình tại /shipping/{maCode}.
 * Mọi hàm nhận maCode từ URL và chỉ làm việc trên vận đơn của đúng hãng đó.
 *
 * Luồng (khớp với luồng đơn hàng):
 *   khách chọn hãng -> cửa hàng xác nhận (StoreOrderService.confirmOrder bàn giao đơn, tạo vận đơn CHO_LAY_HANG)
 *   -> hãng "Tiếp nhận" trên cổng (CHO_LAY_HANG -> DANG_LAY_HANG)
 *   -> DA_LAY_HANG -> DANG_GIAO -> DA_GIAO | GIAO_THAT_BAI -> (DANG_GIAO | DA_HOAN_TRA).
 * Việc đổi trạng thái đi qua ShipmentSyncService.applyPartnerUpdate, dùng chung với đồng bộ API
 * (lịch sử vận chuyển, tự hoàn hàng khi hết lượt giao, chốt doanh thu COD / hoàn kho).
 */
@Service
@RequiredArgsConstructor
public class VanChuyenService {

    /** Trạng thái được phép báo qua endpoint /result. */
    private static final Set<TrangThaiVanChuyen> KET_QUA = EnumSet.of(
            TrangThaiVanChuyen.DA_GIAO, TrangThaiVanChuyen.GIAO_THAT_BAI, TrangThaiVanChuyen.DA_HOAN_TRA);

    private final VanChuyenRepository vanChuyenRepository;
    private final ShipmentSyncService syncService;
    private final DonViVanChuyenResolver donViResolver;

    /** Thông tin hãng để trang /shipping/{maCode} hiển thị tiêu đề. */
    @Transactional(readOnly = true)
    public DonViVanChuyenResponse getDonVi(String maCode) {
        return DonViVanChuyenResponse.from(donViResolver.require(maCode));
    }

    /**
     * Hãng tiếp nhận một đơn cửa hàng đã xác nhận và phân công đi lấy hàng: CHO_LAY_HANG -> DANG_LAY_HANG.
     * Mã vận đơn đã được cấp lúc cửa hàng xác nhận (dùng để đồng bộ, đối soát COD) nên không đổi ở đây.
     */
    @Transactional
    public VanChuyenResponse tiepNhan(String maCode, TiepNhanVanChuyenRequest req) {
        DonViVanChuyen dvvc = donViResolver.requireActive(maCode);

        VanChuyen vc = vanChuyenRepository.findByDonHangCuaHang_MaDHCH(req.getMaDHCH())
                // Đơn của hãng khác: trả cùng thông báo để không lộ đơn ngoài phạm vi của hãng.
                .filter(v -> dvvc.getMaDVVC().equals(v.getDonViVanChuyen().getMaDVVC()))
                .orElseThrow(() -> new BusinessException("Đơn hàng không tồn tại"));

        if (TrangThaiVanChuyen.fromName(vc.getTrangThaiVanChuyen()) != TrangThaiVanChuyen.CHO_LAY_HANG) {
            throw new BusinessException("Đơn đã được tiếp nhận hoặc không còn ở trạng thái chờ lấy hàng");
        }

        VanChuyen updated = syncService.applyPartnerUpdate(
                req.getMaDHCH(), TrangThaiVanChuyen.DANG_LAY_HANG, req.getGhiChuGiaoHang());
        return VanChuyenResponse.from(updated);
    }

    /** Cập nhật trạng thái theo đúng bước chuyển hợp lệ của TrangThaiVanChuyen. */
    @Transactional
    public VanChuyenResponse capNhat(String maCode, Integer id, CapNhatVanChuyenRequest req) {
        DonViVanChuyen dvvc = donViResolver.requireActive(maCode);
        TrangThaiVanChuyen next = parse(req.getTrangThai());

        // Chỉ lấy mã đơn con, kèm điều kiện đúng hãng; khóa đơn và đọc lại vận đơn nằm trong applyPartnerUpdate.
        Integer maDHCH = vanChuyenRepository.findMaDHCHByIdAndDonVi(id, dvvc.getMaDVVC())
                .orElseThrow(() -> new BusinessException("Vận chuyển không tồn tại"));

        return VanChuyenResponse.from(syncService.applyPartnerUpdate(maDHCH, next, req.getGhiChu()));
    }

    /** Báo kết quả giao hàng: chỉ nhận DA_GIAO, GIAO_THAT_BAI hoặc DA_HOAN_TRA. */
    @Transactional
    public VanChuyenResponse capNhatKetQua(String maCode, Integer id, CapNhatVanChuyenRequest req) {
        if (!KET_QUA.contains(parse(req.getTrangThai()))) {
            throw new BusinessException("Kết quả giao hàng chỉ gồm: DA_GIAO, GIAO_THAT_BAI hoặc DA_HOAN_TRA");
        }
        return capNhat(maCode, id, req);
    }

    /** Đơn cửa hàng đã xác nhận, đang chờ hãng tiếp nhận. */
    @Transactional(readOnly = true)
    public List<YeuCauVanChuyenResponse> getYeuCauChuaTiepNhan(String maCode) {
        DonViVanChuyen dvvc = donViResolver.require(maCode);
        return vanChuyenRepository.findAllByDonViVanChuyen_MaDVVCOrderByMaVanChuyenDesc(dvvc.getMaDVVC()).stream()
                .filter(v -> TrangThaiVanChuyen.fromName(v.getTrangThaiVanChuyen()) == TrangThaiVanChuyen.CHO_LAY_HANG)
                .filter(v -> TrangThaiDonHang.DA_XAC_NHAN.name().equals(v.getDonHangCuaHang().getTrangThaiDonHang()))
                .map(v -> {
                    DonHangCuaHang d = v.getDonHangCuaHang();
                    return YeuCauVanChuyenResponse.builder()
                            .maDHCH(d.getMaDHCH())
                            .maDH(d.getDonHang().getMaDH())
                            .maDVVC(dvvc.getMaDVVC())
                            .maCode(dvvc.getMaCode())
                            .tenDonVi(dvvc.getTenDonVi())
                            .maVanDon(v.getMaVanDon())
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

    private static TrangThaiVanChuyen parse(String value) {
        TrangThaiVanChuyen status = TrangThaiVanChuyen.fromName(value);
        if (status == null) {
            throw new BusinessException("Trạng thái giao hàng không hợp lệ: " + value);
        }
        return status;
    }
}
