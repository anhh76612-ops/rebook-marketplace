package com.oldbook.service.order;

import com.oldbook.constant.order.TrangThaiDonHang;
import com.oldbook.constant.shipping.TrangThaiVanChuyen;
import com.oldbook.dto.order.SellerOrderResponse;
import com.oldbook.dto.order.UpdateShippingStatusRequest;
import com.oldbook.entity.catalog.CuaHang;
import com.oldbook.entity.order.DonHang;
import com.oldbook.entity.order.DonHangCuaHang;
import com.oldbook.entity.shipping.DonViVanChuyen;
import com.oldbook.entity.shipping.VanChuyen;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.repository.order.DonHangCuaHangRepository;
import com.oldbook.repository.shipping.VanChuyenRepository;
import com.oldbook.repository.store.CuaHangRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoreOrderService {

    private final CuaHangRepository cuaHangRepository;
    private final DonHangCuaHangRepository donHangCuaHangRepository;
    private final VanChuyenRepository vanChuyenRepository;
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

    /** Chủ cửa hàng xác nhận đơn: từ lúc này đơn mới có thông tin giao hàng. */
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

        order.setTrangThaiDonHang(TrangThaiDonHang.DA_XAC_NHAN.name());
        order.setNgayXacNhan(LocalDateTime.now());
        donHangCuaHangRepository.save(order);

        VanChuyen vanChuyen = vanChuyenRepository.save(
                VanChuyen.builder()
                        .donHangCuaHang(order)
                        .donViVanChuyen(carrier)
                        .maVanDon(generateTrackingCode(carrier, order))
                        .trangThaiVanChuyen(TrangThaiVanChuyen.CHO_LAY_HANG.name())
                        .soLanGiaoThatBai(0)
                        .build()
        );

        return toSellerResponse(order, vanChuyen);
    }

    /** Cập nhật trạng thái giao hàng (CHO_LAY_HANG -> DANG_GIAO -> DA_GIAO / GIAO_THAT_BAI). */
    @Transactional
    public SellerOrderResponse updateShippingStatus(
            Integer maND,
            Integer maDHCH,
            UpdateShippingStatusRequest request
    ) {
        DonHangCuaHang order = loadOwnedOrderForUpdate(maND, maDHCH);

        VanChuyen vanChuyen = vanChuyenRepository.findByDonHangCuaHang_MaDHCH(maDHCH)
                .orElseThrow(() -> new BusinessException(
                        "Đơn hàng chưa được xác nhận nên chưa có thông tin giao hàng"
                ));

        TrangThaiVanChuyen current = parseStatus(vanChuyen.getTrangThaiVanChuyen());
        TrangThaiVanChuyen next = parseStatus(request.getTrangThaiVanChuyen());

        if (!current.canMoveTo(next)) {
            throw new BusinessException(
                    "Không thể chuyển trạng thái giao hàng từ \""
                            + current.getTenHienThi() + "\" sang \"" + next.getTenHienThi() + "\""
            );
        }

        vanChuyen.setTrangThaiVanChuyen(next.name());

        if (next == TrangThaiVanChuyen.GIAO_THAT_BAI) {
            int soLan = vanChuyen.getSoLanGiaoThatBai() == null
                    ? 0 : vanChuyen.getSoLanGiaoThatBai();
            vanChuyen.setSoLanGiaoThatBai(soLan + 1);
        }

        if (request.getGhiChuGiaoHang() != null) {
            String note = request.getGhiChuGiaoHang().trim();
            vanChuyen.setGhiChuGiaoHang(note.isEmpty() ? null : note);
        }

        vanChuyenRepository.save(vanChuyen);

        // Đồng bộ trạng thái đơn hàng con với trạng thái giao hàng.
        if (next == TrangThaiVanChuyen.DANG_GIAO) {
            order.setTrangThaiDonHang(TrangThaiDonHang.DANG_GIAO.name());
        } else if (next == TrangThaiVanChuyen.DA_GIAO) {
            order.setTrangThaiDonHang(TrangThaiDonHang.DA_GIAO.name());
        }
        donHangCuaHangRepository.save(order);

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

    private TrangThaiVanChuyen parseStatus(String value) {
        try {
            return TrangThaiVanChuyen.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BusinessException("Trạng thái giao hàng không hợp lệ: " + value);
        }
    }

    private String generateTrackingCode(DonViVanChuyen carrier, DonHangCuaHang order) {
        String prefix = carrier.getMaCode() == null || carrier.getMaCode().isBlank()
                ? "VC" : carrier.getMaCode().trim().toUpperCase();

        String code;
        do {
            String random = UUID.randomUUID().toString()
                    .replace("-", "").substring(0, 6).toUpperCase();
            code = prefix + String.format("%08d", order.getMaDHCH()) + random;
        } while (vanChuyenRepository.existsByMaVanDon(code));

        return code;
    }

    private SellerOrderResponse toSellerResponse(DonHangCuaHang order, VanChuyen vanChuyen) {
        DonHang donHang = order.getDonHang();

        String diaChi = java.util.stream.Stream.of(
                        donHang.getDiaChiGiaoChiTiet(),
                        donHang.getPhuongXaGiao(),
                        donHang.getQuanHuyenGiao(),
                        donHang.getTinhThanhGiao())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(", "));

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
}
