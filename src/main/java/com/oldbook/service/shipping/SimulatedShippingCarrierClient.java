package com.oldbook.service.shipping;

import com.oldbook.constant.shipping.TrangThaiVanChuyen;
import com.oldbook.entity.shipping.VanChuyen;
import com.oldbook.repository.shipping.VanChuyenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Đơn vị vận chuyển giả lập cho môi trường dev/demo ({@code shipping.sync.provider=simulator}).
 * Mỗi lần tra cứu, vận đơn tiến thêm một bước theo luồng giao thành công.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "shipping.sync.provider", havingValue = "simulator", matchIfMissing = true)
public class SimulatedShippingCarrierClient implements ShippingCarrierClient {

    private final VanChuyenRepository vanChuyenRepository;

    @Override
    public CarrierShipment createShipment(CarrierShipmentRequest request) {
        String prefix = request.maCode() == null || request.maCode().isBlank()
                ? "VC" : request.maCode().trim().toUpperCase();

        String code;
        do {
            String random = UUID.randomUUID().toString()
                    .replace("-", "").substring(0, 6).toUpperCase();
            code = prefix + String.format("%08d", request.maDHCH()) + random;
        } while (vanChuyenRepository.existsByMaVanDon(code));

        return new CarrierShipment(code);
    }

    @Override
    public Optional<CarrierTracking> track(VanChuyen shipment) {
        TrangThaiVanChuyen current = TrangThaiVanChuyen.fromName(shipment.getTrangThaiVanChuyen());
        if (current == null || current.isFinal()) {
            return Optional.empty();
        }

        String raw = switch (current) {
            case CHO_LAY_HANG -> "picking";
            case DANG_LAY_HANG -> "picked";
            case DA_LAY_HANG -> "delivering";
            case DANG_GIAO -> "delivered";
            case GIAO_THAT_BAI -> "returned";
            default -> null;
        };

        return raw == null
                ? Optional.empty()
                : Optional.of(new CarrierTracking(raw, "Cập nhật từ đơn vị vận chuyển giả lập", LocalDateTime.now()));
    }
}
