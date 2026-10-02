package com.oldbook.service.order;

import com.oldbook.dto.order.OrderItemResponse;
import com.oldbook.dto.order.ShipmentResponse;
import com.oldbook.dto.order.StoreOrderResponse;
import com.oldbook.entity.order.DonHangCuaHang;
import com.oldbook.entity.shipping.DonViVanChuyen;
import com.oldbook.entity.shipping.VanChuyen;
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

        return stores.stream()
                .map(store -> toStoreOrderResponse(store, shipments.get(store.getMaDHCH())))
                .toList();
    }

    public StoreOrderResponse toStoreOrderResponse(DonHangCuaHang store, VanChuyen vanChuyen) {
        List<OrderItemResponse> items = chiTietDonHangRepository
                .findAllByDonHangCuaHang_MaDHCHOrderByMaCTDHAsc(store.getMaDHCH())
                .stream()
                .map(ct -> OrderItemResponse.builder()
                        .maCTDH(ct.getMaCTDH())
                        .maSach(ct.getSach().getMaSach())
                        .tenSach(ct.getSach().getTenSach())
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
                .trangThaiThanhToanShop(store.getTrangThaiThanhToanShop())
                .ghiChu(store.getGhiChu())
                .maDVVC(carrier == null ? null : carrier.getMaDVVC())
                .tenDonViVanChuyen(carrier == null ? null : carrier.getTenDonVi())
                .ngayXacNhan(store.getNgayXacNhan())
                .vanChuyen(vanChuyen == null ? null : toShipmentResponse(vanChuyen))
                .items(items)
                .build();
    }

    private ShipmentResponse toShipmentResponse(VanChuyen vc) {
        return ShipmentResponse.builder()
                .maVanDon(vc.getMaVanDon())
                .trangThaiVanChuyen(vc.getTrangThaiVanChuyen())
                .soLanGiaoThatBai(vc.getSoLanGiaoThatBai())
                .ghiChuGiaoHang(vc.getGhiChuGiaoHang())
                .ngayCapNhat(vc.getNgayCapNhat())
                .build();
    }
}
