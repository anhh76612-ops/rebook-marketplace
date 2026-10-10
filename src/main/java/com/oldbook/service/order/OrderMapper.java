package com.oldbook.service.order;

import com.oldbook.dto.order.OrderItemResponse;
import com.oldbook.dto.order.ShipmentEventResponse;
import com.oldbook.dto.order.ShipmentResponse;
import com.oldbook.dto.order.StoreOrderResponse;
import com.oldbook.entity.order.DonHangCuaHang;
import com.oldbook.constant.order.TrangThaiDonHang;
import com.oldbook.constant.shipping.TrangThaiVanChuyen;
import com.oldbook.entity.shipping.DonViVanChuyen;
import com.oldbook.entity.shipping.LichSuVanChuyen;
import com.oldbook.entity.shipping.VanChuyen;
import com.oldbook.repository.shipping.LichSuVanChuyenRepository;
import com.oldbook.repository.order.ChiTietDonHangRepository;
import com.oldbook.repository.shipping.VanChuyenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OrderMapper {

    private final ChiTietDonHangRepository chiTietDonHangRepository;
    private final VanChuyenRepository vanChuyenRepository;
    private final LichSuVanChuyenRepository lichSuVanChuyenRepository;

    public List<StoreOrderResponse> toStoreOrderResponses(List<DonHangCuaHang> stores) {
        if (stores.isEmpty()) {
            return List.of();
        }

        List<Integer> ids = stores.stream().map(DonHangCuaHang::getMaDHCH).toList();

        Map<Integer, VanChuyen> shipments = vanChuyenRepository
                .findAllByDonHangCuaHang_MaDHCHIn(ids).stream()
                .collect(Collectors.toMap(
                        v -> v.getDonHangCuaHang().getMaDHCH(),
                        Function.identity(),
                        (a, b) -> a
                ));

        Map<Integer, List<LichSuVanChuyen>> history = loadHistory(shipments.values());

        return stores.stream()
                .map(store -> toStoreOrderResponse(store, shipments.get(store.getMaDHCH()), history, false))
                .toList();
    }

    /** Dành cho chủ cửa hàng: kèm hoa hồng và doanh thu. Phản hồi cho người mua không có hai khoản này. */
    public StoreOrderResponse toStoreOrderResponse(DonHangCuaHang store, VanChuyen vanChuyen) {
        return toStoreOrderResponse(
                store, vanChuyen, vanChuyen == null ? Map.of() : loadHistory(List.of(vanChuyen)), true);
    }

    private Map<Integer, List<LichSuVanChuyen>> loadHistory(java.util.Collection<VanChuyen> shipments) {
        if (shipments.isEmpty()) {
            return Map.of();
        }
        return lichSuVanChuyenRepository
                .findAllByVanChuyen_MaVanChuyenInOrderByThoiGianAscMaLichSuAsc(
                        shipments.stream().map(VanChuyen::getMaVanChuyen).toList())
                .stream()
                .collect(Collectors.groupingBy(h -> h.getVanChuyen().getMaVanChuyen()));
    }

    private StoreOrderResponse toStoreOrderResponse(
            DonHangCuaHang store, VanChuyen vanChuyen, Map<Integer, List<LichSuVanChuyen>> history,
            boolean includeFinance
    ) {
        List<OrderItemResponse> items = chiTietDonHangRepository
                .findAllByDonHangCuaHang_MaDHCHOrderByMaCTDHAsc(store.getMaDHCH())
                .stream()
                .map(ct -> OrderItemResponse.builder()
                        .maCTDH(ct.getMaCTDH())
                        .maSach(ct.getSach().getMaSach())
                        .tenSach(ct.getSach().getTenSach())
                        .hinhAnhUrl(ct.getSach().getHinhAnhUrl())
                        .soLuong(ct.getSoLuong())
                        .donGia(ct.getDonGia())
                        .thanhTien(ct.getThanhTien())
                        .build())
                .toList();

        DonViVanChuyen carrier = store.getDonViVanChuyen();

        return StoreOrderResponse.builder()
                .maDHCH(store.getMaDHCH())
                .maCH(store.getCuaHang().getMaCH())
                .tenCuaHang(store.getCuaHang().getTenCuaHang())
                .tienHangCH(store.getTienHangCH())
                .phiShipCH(store.getPhiShipCH())
                .tongTienShopOrder(store.getTongTienShopOrder())
                .trangThaiDonHang(store.getTrangThaiDonHang())
                .tenTrangThaiDonHang(tenTrangThaiDonHang(store.getTrangThaiDonHang()))
                .trangThaiThanhToanShop(store.getTrangThaiThanhToanShop())
                .ghiChu(store.getGhiChu())
                .maDVVC(carrier == null ? null : carrier.getMaDVVC())
                .tenDonViVanChuyen(carrier == null ? null : carrier.getTenDonVi())
                .ngayTao(store.getDonHang() == null ? null : store.getDonHang().getNgayTao())
                .ngayXacNhan(store.getNgayXacNhan())
                .ngayGiao(store.getNgayGiao())
                .ngayHoanTra(store.getNgayHoanTra())
                .commissionTien(includeFinance && store.getNgayGiao() != null ? store.getCommissionTien() : null)
                .doanhThuShop(includeFinance && store.getNgayGiao() != null ? store.getDoanhThuShop() : null)
                .vanChuyen(vanChuyen == null ? null
                        : toShipmentResponse(vanChuyen, history.getOrDefault(vanChuyen.getMaVanChuyen(), List.of())))
                .items(items)
                .build();
    }

    private static String tenTrangThaiDonHang(String value) {
        TrangThaiDonHang st = TrangThaiDonHang.fromName(value);
        return st == null ? value : st.getTenHienThi();
    }

    private ShipmentResponse toShipmentResponse(VanChuyen vc, List<LichSuVanChuyen> history) {
        TrangThaiVanChuyen st = TrangThaiVanChuyen.fromName(vc.getTrangThaiVanChuyen());
        return ShipmentResponse.builder()
                .maVanDon(vc.getMaVanDon())
                .trangThaiVanChuyen(vc.getTrangThaiVanChuyen())
                .tenTrangThai(st == null ? vc.getTrangThaiVanChuyen() : st.getTenHienThi())
                .lichSu(history.stream().map(h -> {
                    TrangThaiVanChuyen hs = TrangThaiVanChuyen.fromName(h.getTrangThai());
                    return ShipmentEventResponse.builder()
                            .trangThai(h.getTrangThai())
                            .tenTrangThai(hs == null ? h.getTrangThai() : hs.getTenHienThi())
                            .ghiChu(h.getGhiChu())
                            .thoiGian(h.getThoiGian())
                            .build();
                }).toList())
                .soLanGiaoThatBai(vc.getSoLanGiaoThatBai())
                .ghiChuGiaoHang(vc.getGhiChuGiaoHang())
                .ngayCapNhat(vc.getNgayCapNhat())
                .build();
    }
}
