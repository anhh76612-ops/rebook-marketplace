package com.oldbook.service.shipping;

import com.oldbook.config.shipping.ShippingProperties;
import com.oldbook.constant.shipping.TrangThaiVanChuyen;
import com.oldbook.entity.order.DonHangCuaHang;
import com.oldbook.entity.shipping.LichSuVanChuyen;
import com.oldbook.entity.shipping.VanChuyen;
import com.oldbook.repository.order.DonHangCuaHangRepository;
import com.oldbook.repository.shipping.LichSuVanChuyenRepository;
import com.oldbook.repository.shipping.VanChuyenRepository;
import com.oldbook.service.order.OrderSettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Điểm duy nhất áp dụng trạng thái lấy từ API của đơn vị vận chuyển.
 * Xử lý idempotent: kết quả tra cứu lặp hoặc lạc hậu không làm đơn lùi trạng thái hay chốt tiền hai lần.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentSyncService {

    public static final String NGUON_POLLING = "POLLING";
    public static final String NGUON_HE_THONG = "HE_THONG";

    public enum SyncResult {
        APPLIED, NO_CHANGE, IGNORED_UNKNOWN_STATUS, IGNORED_INVALID_TRANSITION, NOT_FOUND
    }

    public record CarrierUpdate(
            String maVanDon, String trangThaiGoc, String ghiChu, LocalDateTime thoiGian, String nguon
    ) {}

    private final VanChuyenRepository vanChuyenRepository;
    private final DonHangCuaHangRepository donHangCuaHangRepository;
    private final LichSuVanChuyenRepository lichSuVanChuyenRepository;
    private final CarrierStatusMapper statusMapper;
    private final OrderSettlementService settlementService;
    private final ShippingProperties properties;

    /**
     * @param expectedMaCode mã đơn vị vận chuyển phải sở hữu vận đơn; null = không kiểm tra
     */
    @Transactional
    public SyncResult applyCarrierUpdate(String expectedMaCode, CarrierUpdate update) {
        if (update.maVanDon() == null || update.maVanDon().isBlank()) {
            return SyncResult.NOT_FOUND;
        }

        Optional<Integer> maDHCH = vanChuyenRepository.findMaDHCHByMaVanDon(update.maVanDon().trim());
        if (maDHCH.isEmpty()) {
            return SyncResult.NOT_FOUND;
        }

        // Luôn khóa đơn hàng con trước, rồi mới đọc vận đơn, để các luồng cập nhật song song tuần tự hóa.
        DonHangCuaHang order = donHangCuaHangRepository.findByIdForUpdate(maDHCH.get()).orElse(null);
        VanChuyen vanChuyen = order == null ? null
                : vanChuyenRepository.findByDonHangCuaHang_MaDHCH(order.getMaDHCH()).orElse(null);
        if (vanChuyen == null) {
            return SyncResult.NOT_FOUND;
        }

        String ownerCode = vanChuyen.getDonViVanChuyen().getMaCode();
        if (expectedMaCode != null && !expectedMaCode.trim().equalsIgnoreCase(ownerCode)) {
            return SyncResult.NOT_FOUND;
        }

        Optional<TrangThaiVanChuyen> mapped = statusMapper.map(ownerCode, update.trangThaiGoc());
        if (mapped.isEmpty()) {
            log.warn("Bỏ qua trạng thái không nhận diện '{}' của vận đơn {} ({})",
                    update.trangThaiGoc(), update.maVanDon(), ownerCode);
            return SyncResult.IGNORED_UNKNOWN_STATUS;
        }

        TrangThaiVanChuyen current = TrangThaiVanChuyen.fromName(vanChuyen.getTrangThaiVanChuyen());
        TrangThaiVanChuyen next = mapped.get();

        if (current == next) {
            return SyncResult.NO_CHANGE;
        }
        if (current == null || !current.canMoveTo(next)) {
            log.info("Bỏ qua chuyển trạng thái không hợp lệ {} -> {} của vận đơn {}",
                    current, next, update.maVanDon());
            return SyncResult.IGNORED_INVALID_TRANSITION;
        }

        moveTo(order, vanChuyen, next, update.trangThaiGoc(), update.ghiChu(), update.thoiGian(), update.nguon());

        // Giao thất bại đủ số lần cho phép thì chuyển hoàn hàng.
        if (next == TrangThaiVanChuyen.GIAO_THAT_BAI
                && nz(vanChuyen.getSoLanGiaoThatBai()) >= Math.max(1, properties.getMaxDeliveryAttempts())) {
            moveTo(order, vanChuyen, TrangThaiVanChuyen.DA_HOAN_TRA, null,
                    "Giao thất bại " + vanChuyen.getSoLanGiaoThatBai() + " lần, chuyển hoàn hàng về cửa hàng",
                    null, NGUON_HE_THONG);
        }

        return SyncResult.APPLIED;
    }

    private void moveTo(
            DonHangCuaHang order, VanChuyen vanChuyen, TrangThaiVanChuyen next,
            String trangThaiGoc, String ghiChu, LocalDateTime thoiGian, String nguon
    ) {
        String note = trim(ghiChu, 1000);

        vanChuyen.setTrangThaiVanChuyen(next.name());
        if (note != null) {
            vanChuyen.setGhiChuGiaoHang(note);
        }
        if (next == TrangThaiVanChuyen.GIAO_THAT_BAI) {
            vanChuyen.setSoLanGiaoThatBai(nz(vanChuyen.getSoLanGiaoThatBai()) + 1);
        }
        vanChuyenRepository.save(vanChuyen);

        order.setTrangThaiDonHang(next.toTrangThaiDonHang().name());
        donHangCuaHangRepository.save(order);

        lichSuVanChuyenRepository.save(LichSuVanChuyen.builder()
                .vanChuyen(vanChuyen)
                .trangThai(next.name())
                .trangThaiGoc(trim(trangThaiGoc, 100))
                .ghiChu(note)
                .nguon(nguon == null ? NGUON_HE_THONG : nguon)
                .thoiGian(thoiGian)
                .build());

        if (next == TrangThaiVanChuyen.DA_GIAO) {
            settlementService.settleDelivered(order, vanChuyen.getMaVanDon());
        } else if (next == TrangThaiVanChuyen.DA_HOAN_TRA) {
            settlementService.settleReturned(order);
        }
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
