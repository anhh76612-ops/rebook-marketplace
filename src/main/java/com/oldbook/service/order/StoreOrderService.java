package com.oldbook.service.order;

import com.oldbook.constant.auth.VaiTro;
import com.oldbook.constant.order.PhuongThucThanhToan;
import com.oldbook.constant.order.TrangThaiDonHang;
import com.oldbook.constant.order.TrangThaiYeuCauHuy;
import com.oldbook.constant.shipping.TrangThaiVanChuyen;
import com.oldbook.dto.order.CancelOrderRequest;
import com.oldbook.dto.order.OrderItemResponse;
import com.oldbook.dto.order.SellerOrderResponse;
import com.oldbook.dto.order.StoreOrderProductOption;
import com.oldbook.entity.catalog.CuaHang;
import com.oldbook.entity.order.DonHang;
import com.oldbook.entity.order.DonHangCuaHang;
import com.oldbook.entity.order.YeuCauHuyDon;
import com.oldbook.entity.shipping.DonViVanChuyen;
import com.oldbook.entity.shipping.LichSuVanChuyen;
import com.oldbook.entity.shipping.VanChuyen;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.repository.order.ChiTietDonHangRepository;
import com.oldbook.repository.order.DonHangCuaHangRepository;
import com.oldbook.repository.order.YeuCauHuyDonRepository;
import com.oldbook.repository.shipping.LichSuVanChuyenRepository;
import com.oldbook.repository.shipping.VanChuyenRepository;
import com.oldbook.service.shipping.ShipmentSyncService;
import com.oldbook.service.shipping.ShippingCarrierClient;
import com.oldbook.service.shipping.ShippingCarrierClient.CarrierShipment;
import com.oldbook.service.shipping.ShippingCarrierClient.CarrierShipmentRequest;
import com.oldbook.repository.store.CuaHangRepository;
import com.oldbook.service.notification.NghiepVuThongBaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final ChiTietDonHangRepository chiTietDonHangRepository;
    private final VanChuyenRepository vanChuyenRepository;
    private final LichSuVanChuyenRepository lichSuVanChuyenRepository;
    private final ShippingCarrierClient carrierClient;
    private final OrderMapper orderMapper;
    private final NghiepVuThongBaoService nghiepVuThongBaoService;
    private final YeuCauHuyDonRepository yeuCauHuyDonRepository;
    private final OrderSettlementService settlementService;

    private static final String CHUA_DANH_GIA = "CHUA_DANH_GIA";

    @Transactional(readOnly = true)
    public List<SellerOrderResponse> getMyStoreOrders(Integer maND, String trangThai) {
        return getMyStoreOrders(maND, trangThai, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<SellerOrderResponse> getMyStoreOrders(
            Integer maND, String trangThai, String maDonHang, Integer maSach, String danhGia
    ) {
        CuaHang cuaHang = requireStore(maND);

        String keyword = normalizeOrderCode(maDonHang);
        boolean chuaDanhGia = danhGia != null && CHUA_DANH_GIA.equalsIgnoreCase(danhGia.trim());
        Integer soSao = chuaDanhGia ? null : parseStars(danhGia);

        List<DonHangCuaHang> orders = donHangCuaHangRepository
                .findAllByCuaHang_MaCHOrderByMaDHCHDesc(cuaHang.getMaCH())
                .stream()
                .filter(o -> trangThai == null || trangThai.isBlank()
                        || trangThai.trim().equalsIgnoreCase(o.getTrangThaiDonHang()))
                .filter(o -> keyword.isEmpty() || String.valueOf(o.getMaDHCH()).contains(keyword))
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
                .filter(r -> matchesItemFilters(r, maSach, soSao, chuaDanhGia))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StoreOrderProductOption> getMyStoreOrderProducts(Integer maND) {
        CuaHang cuaHang = requireStore(maND);
        return chiTietDonHangRepository.findDistinctProductsByStore(cuaHang.getMaCH());
    }

    private static String normalizeOrderCode(String value) {
        if (value == null) {
            return "";
        }
        String code = value.trim();
        if (code.startsWith("#")) {
            code = code.substring(1).trim();
        }
        return code;
    }

    private static Integer parseStars(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            int stars = Integer.parseInt(value.trim());
            if (stars >= 1 && stars <= 5) {
                return stars;
            }
        } catch (NumberFormatException ignored) {
            // rơi xuống lỗi bên dưới
        }
        throw new BusinessException("Bộ lọc đánh giá không hợp lệ");
    }

    private static boolean matchesItemFilters(
            SellerOrderResponse response, Integer maSach, Integer soSao, boolean chuaDanhGia
    ) {
        if (maSach == null && soSao == null && !chuaDanhGia) {
            return true;
        }
        List<OrderItemResponse> items = response.getStoreOrder().getItems() == null
                ? List.of() : response.getStoreOrder().getItems();
        List<OrderItemResponse> scope = maSach == null ? items : items.stream()
                .filter(i -> maSach.equals(i.getMaSach()))
                .toList();
        if (scope.isEmpty()) {
            return false;
        }
        if (soSao != null) {
            return scope.stream().anyMatch(i -> i.getDanhGia() != null && soSao.equals(i.getDanhGia().soSao()));
        }
        if (chuaDanhGia) {
            return TrangThaiDonHang.DA_GIAO.name().equals(response.getStoreOrder().getTrangThaiDonHang())
                    && scope.stream().anyMatch(i -> i.getDanhGia() == null);
        }
        return true;
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
                // Đơn đã chuyển khoản trước thì hãng không thu thêm gì của khách khi giao.
                PhuongThucThanhToan.laTraTruoc(donHang.getPhuongThucTT())
                        ? BigDecimal.ZERO
                        : order.getTongTienShopOrder(),
                order.getGhiChu()
        ));

        order.setTrangThaiDonHang(TrangThaiDonHang.DA_XAC_NHAN.name());
        order.setNgayXacNhan(LocalDateTime.now());
        donHangCuaHangRepository.save(order);
        nghiepVuThongBaoService.donHangDuocXacNhan(order);

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
                .ghiChu("Chủ cửa hàng đã xác nhận, chờ " + carrier.getTenDonVi() + " tiếp nhận")
                .nguon(ShipmentSyncService.NGUON_HE_THONG)
                .build());

        return toSellerResponse(order, vanChuyen);
    }

    /** Chủ cửa hàng hủy đơn chưa xác nhận, hoặc đã xác nhận nhưng đơn vị vận chuyển chưa tiếp nhận. */
    @Transactional
    public SellerOrderResponse cancelOrder(Integer maND, Integer maDHCH, String lyDo) {
        String reason = CancelOrderRequest.normalize(lyDo);
        DonHangCuaHang order = loadOwnedOrderForUpdate(maND, maDHCH);
        String status = order.getTrangThaiDonHang();

        if (TrangThaiDonHang.CHO_XU_LY.name().equals(status)) {
            settlementService.settleCancelled(order, VaiTro.CHU_CUA_HANG.name(), reason);
            return toSellerResponse(order, null);
        }

        VanChuyen vanChuyen = vanChuyenRepository.findByDonHangCuaHang_MaDHCH(maDHCH).orElse(null);
        boolean carrierNotAccepted = vanChuyen == null
                || TrangThaiVanChuyen.CHO_LAY_HANG.name().equals(vanChuyen.getTrangThaiVanChuyen());

        if (!TrangThaiDonHang.DA_XAC_NHAN.name().equals(status) || !carrierNotAccepted) {
            throw new BusinessException("Đơn vị vận chuyển đã tiếp nhận đơn, không thể hủy");
        }

        settlementService.settleCancelled(order, VaiTro.CHU_CUA_HANG.name(), reason);
        markShipmentCancelled(vanChuyen, "Đơn bị cửa hàng hủy");

        yeuCauHuyDonRepository.findFirstByDonHangCuaHang_MaDHCHAndTrangThaiYeuCau(
                        maDHCH, TrangThaiYeuCauHuy.CHO_DUYET.name())
                .ifPresent(request -> resolveRequest(request, TrangThaiYeuCauHuy.DA_DUYET, null));

        return toSellerResponse(order, vanChuyen);
    }

    private void markShipmentCancelled(VanChuyen vanChuyen, String note) {
        if (vanChuyen == null) {
            return;
        }
        vanChuyen.setTrangThaiVanChuyen(TrangThaiVanChuyen.DA_HUY.name());
        vanChuyen.setGhiChuGiaoHang(note);
        vanChuyenRepository.save(vanChuyen);

        lichSuVanChuyenRepository.save(LichSuVanChuyen.builder()
                .vanChuyen(vanChuyen)
                .trangThai(TrangThaiVanChuyen.DA_HUY.name())
                .ghiChu(note)
                .nguon(ShipmentSyncService.NGUON_HE_THONG)
                .build());
    }

    /** Đồng ý yêu cầu hủy của khách (đơn đã xác nhận, chưa được lấy hàng): hủy đơn và chuyển vận đơn sang "Đã hủy". */
    @Transactional
    public SellerOrderResponse approveCancelRequest(Integer maND, Integer maDHCH) {
        DonHangCuaHang order = loadOwnedOrderForUpdate(maND, maDHCH);
        YeuCauHuyDon request = requirePendingCancelRequest(maDHCH);

        VanChuyen vanChuyen = vanChuyenRepository.findByDonHangCuaHang_MaDHCH(maDHCH).orElse(null);
        String shipStatus = vanChuyen == null ? null : vanChuyen.getTrangThaiVanChuyen();
        boolean notPickedUp = shipStatus == null
                || TrangThaiVanChuyen.CHO_LAY_HANG.name().equals(shipStatus)
                || TrangThaiVanChuyen.DANG_LAY_HANG.name().equals(shipStatus);
        boolean orderCancellable = TrangThaiDonHang.DA_XAC_NHAN.name().equals(order.getTrangThaiDonHang())
                || TrangThaiDonHang.DANG_LAY_HANG.name().equals(order.getTrangThaiDonHang());
        if (!orderCancellable || !notPickedUp) {
            throw new BusinessException("Đơn vị vận chuyển đã lấy hàng, không thể hủy. Vui lòng từ chối yêu cầu hủy");
        }

        settlementService.settleCancelled(order, VaiTro.KHACH_HANG.name(), request.getLyDo());

        markShipmentCancelled(vanChuyen, "Đơn bị hủy theo yêu cầu của khách");

        resolveRequest(request, TrangThaiYeuCauHuy.DA_DUYET, null);
        return toSellerResponse(order, vanChuyen);
    }

    @Transactional
    public SellerOrderResponse rejectCancelRequest(Integer maND, Integer maDHCH, String lyDo) {
        String reason = CancelOrderRequest.normalize(lyDo);
        DonHangCuaHang order = loadOwnedOrderForUpdate(maND, maDHCH);
        YeuCauHuyDon request = requirePendingCancelRequest(maDHCH);

        resolveRequest(request, TrangThaiYeuCauHuy.TU_CHOI, reason);
        return toSellerResponse(order, vanChuyenRepository.findByDonHangCuaHang_MaDHCH(maDHCH).orElse(null));
    }

    private YeuCauHuyDon requirePendingCancelRequest(Integer maDHCH) {
        return yeuCauHuyDonRepository.findFirstByDonHangCuaHang_MaDHCHAndTrangThaiYeuCau(
                        maDHCH, TrangThaiYeuCauHuy.CHO_DUYET.name())
                .orElseThrow(() -> new BusinessException("Đơn hàng không có yêu cầu hủy đang chờ xử lý"));
    }

    private void resolveRequest(YeuCauHuyDon request, TrangThaiYeuCauHuy status, String phanHoi) {
        request.setTrangThaiYeuCau(status.name());
        request.setLyDoPhanHoi(phanHoi);
        request.setNgayXuLy(LocalDateTime.now());
        yeuCauHuyDonRepository.save(request);
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
