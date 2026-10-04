package com.oldbook.service.shipping;

import com.oldbook.entity.shipping.VanChuyen;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Đơn vị vận chuyển làm việc qua cổng đối tác /shipping/{maCode} ({@code shipping.sync.provider=portal}).
 * Khi cửa hàng xác nhận: cấp mã vận đơn nội bộ. Trạng thái về sau chỉ đổi khi hãng cập nhật trên cổng
 * (ShipmentSyncService.applyPartnerUpdate), nên tra cứu định kỳ không có gì để kéo về.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "shipping.sync.provider", havingValue = "portal")
public class PortalShippingCarrierClient implements ShippingCarrierClient {

    private final TrackingCodeGenerator codeGenerator;

    @Override
    public CarrierShipment createShipment(CarrierShipmentRequest request) {
        return new CarrierShipment(codeGenerator.generate(request.maCode(), request.maDHCH()));
    }

    @Override
    public Optional<CarrierTracking> track(VanChuyen shipment) {
        return Optional.empty();
    }
}
