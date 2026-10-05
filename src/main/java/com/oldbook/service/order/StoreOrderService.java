package com.oldbook.service.order;

import com.oldbook.constant.order.TrangThaiDonHang;
import com.oldbook.constant.shipping.TrangThaiVanChuyen;
import com.oldbook.dto.order.SellerOrderResponse;
import com.oldbook.entity.catalog.CuaHang;
import com.oldbook.entity.order.DonHang;
import com.oldbook.entity.order.DonHangCuaHang;
import com.oldbook.entity.shipping.DonViVanChuyen;
import com.oldbook.entity.shipping.LichSuVanChuyen;
import com.oldbook.entity.shipping.VanChuyen;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.repository.order.DonHangCuaHangRepository;
import com.oldbook.repository.shipping.LichSuVanChuyenRepository;
import com.oldbook.repository.shipping.VanChuyenRepository;
import com.oldbook.service.shipping.ShipmentSyncService;
import com.oldbook.service.shipping.ShippingCarrierClient;
import com.oldbook.service.shipping.ShippingCarrierClient.CarrierShipment;
import com.oldbook.service.shipping.ShippingCarrierClient.CarrierShipmentRequest;
import com.oldbook.repository.store.CuaHangRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoreOrderService {

    private final CuaHangRepository cuaHangRepository;
    private final DonHangCuaHangRepository donHangCuaHangRepository;
    private final VanChuyenRepository vanChuyenRepository;
    private final LichSuVanChuyenRepository lichSuVanChuyenRepository;
    private final ShippingCarrierClient carrierClient;
    private final OrderMapper orderMapper;

    @Transactional(readOnly = true)
    public List<SellerOrderResponse> getMyStoreOrders(Integer maND, String trangThai) {
        CuaHang cuaHang = requireStore(maND);

        List<DonHangCuaHang> orders = donHangCuaHangRepository
                .findAllByCuaHang_MaCHOrderByMaDHCHDesc(cuaHang.getMaCH())
                .stream()
                .filter(o -> trangThai == null || trangThai.isBlank()
                        || trangThai.trim().equalsIgnoreCase(o.getTrangThaiDonHang()))
                .toList();

        if (orders.isEmpty()) {
            return List.of();
        }

        Map<Integer, VanChuyen> shipments = vanChuyenRepository
                .findAllByDonHangCuaHang_MaDHCHIn(
                        orders.stream().map(DonHangCuaHang::getMaDHCH).toList()
                ).stream()
                .collect(Collectors.toMap(
                        v -> v.getDonHangCuaHang().getMaDHCH(),
                        Function.identity(),
                        (a, b) -> a
                ));

        return orders.stream()
                .map(o -> toSellerResponse(o, shipments.get(o.getMaDHCH())))
                .toList();
    }

    @Transactional(readOnly = true)
    public SellerOrderResponse getMyStoreOrder(Integer maND, Integer maDHCH) {
        CuaHang cuaHang = requireStore(maND);

        DonHangCuaHang order = donHangCuaHangRepository.findById(maDHCH)
                .filter(o -> o.getCuaHang() != null && cuaHang.getMaCH().equals(o.getCuaHang().getMaCH()))
                .orElseThrow(() -> new BusinessException("Đơn hàng không tồn tại"));

        return toSellerResponse(order, vanChuyenRepository.findByDonHangCuaHang_MaDHCH(maDHCH).orElse(null));
    }

    /**
     * Chủ cửa hàng xác nhận đơn. Sau bước này đơn được chuyển cho đơn vị vận chuyển tiếp nhận;
     * mọi trạng thái giao hàng về sau do đơn vị vận chuyển cập nhật (đồng bộ qua API),
     * chủ cửa hàng không tự đổi được.
     */
    @Transactional
    public SellerOrderResponse confirmOrder(Integer maND, Integer maDHCH) {
        DonHangCuaHang order = loadOwnedOrderForUpdate(maND, maDHCH);

        if (!TrangThaiDonHang.CHO_XU_LY.name().equals(order.getTrangThaiDonHang())) {
            throw new BusinessException("Chỉ có thể xác nhận đơn hàng đang chờ xử lý");
        }

        DonViVanChuyen carrier = order.getDonViVanChuyen();
        if (carrier == null) {
            throw new BusinessException("Đơn hàng chưa có đơn vị vận chuyển");
        }

        if (vanChuyenRepository.findByDonHangCuaHang_MaDHCH(maDHCH).isPresent()) {
            throw new BusinessException("Đơn hàng đã được xác nhận trước đó");
        }

        DonHang donHang = order.getDonHang();

        // Bàn giao đơn cho đơn vị vận chuyển; lỗi ở đây làm hủy cả bước xác nhận để cửa hàng thử lại.
        CarrierShipment shipment = carrierClient.createShipment(new CarrierShipmentRequest(
                carrier.getMaCode(),
                order.getMaDHCH(),
                donHang.getTenNguoiNhan(),
                donHang.getSoDienThoaiNhan(),
                formatAddress(donHang),
                order.getTongTienShopOrder(),
                order.getGhiChu()
        ));

        order.setTrangThaiDonHang(TrangThaiDonHang.DA_XAC_NHAN.name());
        order.setNgayXacNhan(LocalDateTime.now());
        donHangCuaHangRepository.save(order);

        VanChuyen vanChuyen = vanChuyenRepository.save(
                VanChuyen.builder()
                        .donHangCuaHang(order)
                        .donViVanChuyen(carrier)
                        .maVanDon(shipment.maVanDon())
                        .trangThaiVanChuyen(TrangThaiVanChuyen.CHO_LAY_HANG.name())
                        .soLanGiaoThatBai(0)
                        .build()
        );

        lichSuVanChuyenRepository.save(LichSuVanChuyen.builder()
                .vanChuyen(vanChuyen)
                .trangThai(TrangThaiVanChuyen.CHO_LAY_HANG.name())
                .ghiChu("Cửa hàng đã xác nhận, " + carrier.getTenDonVi() + " đã tiếp nhận đơn")
                .nguon(ShipmentSyncService.NGUON_HE_THONG)
                .build());

        return toSellerResponse(order, vanChuyen);
    }

    private CuaHang requireStore(Integer maND) {
        return cuaHangRepository.findByChuShop_MaND(maND)
                .orElseThrow(() -> new BusinessException("Bạn chưa có cửa hàng"));
    }

    private DonHangCuaHang loadOwnedOrderForUpdate(Integer maND, Integer maDHCH) {
        CuaHang cuaHang = requireStore(maND);

        DonHangCuaHang order = donHangCuaHangRepository.findByIdForUpdate(maDHCH)
                .orElseThrow(() -> new BusinessException("Đơn hàng không tồn tại"));

        // Trả cùng một thông báo để không lộ đơn của cửa hàng khác.
        if (order.getCuaHang() == null
                || !cuaHang.getMaCH().equals(order.getCuaHang().getMaCH())) {
            throw new BusinessException("Đơn hàng không tồn tại");
        }

        return order;
    }

    private SellerOrderResponse toSellerResponse(DonHangCuaHang order, VanChuyen vanChuyen) {
        DonHang donHang = order.getDonHang();

        String diaChi = formatAddress(donHang);

        return SellerOrderResponse.builder()
                .maDH(donHang.getMaDH())
                .ngayTao(donHang.getNgayTao())
                .phuongThucTT(donHang.getPhuongThucTT())
                .tenNguoiNhan(donHang.getTenNguoiNhan())
                .soDienThoaiNhan(donHang.getSoDienThoaiNhan())
                .diaChiGiao(diaChi)
                .storeOrder(orderMapper.toStoreOrderResponse(order, vanChuyen))
                .build();
    }

    private static String formatAddress(DonHang donHang) {
        return java.util.stream.Stream.of(
                        donHang.getDiaChiGiaoChiTiet(),
                        donHang.getPhuongXaGiao(),
                        donHang.getQuanHuyenGiao(),
                        donHang.getTinhThanhGiao())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(", "));
    }
}
