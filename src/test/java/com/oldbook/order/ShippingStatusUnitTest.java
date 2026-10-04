package com.oldbook.order;

import com.oldbook.config.shipping.ShippingProperties;
import com.oldbook.constant.order.TrangThaiDonHang;
import com.oldbook.constant.shipping.TrangThaiVanChuyen;
import com.oldbook.service.shipping.CarrierStatusMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShippingStatusUnitTest {

    private final ShippingProperties properties = new ShippingProperties();
    private final CarrierStatusMapper mapper = new CarrierStatusMapper(properties);

    @Test
    void mapsCommonCarrierCodesToInternalStatuses() {
        assertThat(mapper.map("GHN", "ready_to_pick")).contains(TrangThaiVanChuyen.CHO_LAY_HANG);
        assertThat(mapper.map("GHN", "Picking")).contains(TrangThaiVanChuyen.DANG_LAY_HANG);
        assertThat(mapper.map("GHN", "picked")).contains(TrangThaiVanChuyen.DA_LAY_HANG);
        assertThat(mapper.map("GHN", "transporting")).contains(TrangThaiVanChuyen.DA_LAY_HANG);
        assertThat(mapper.map("GHN", "delivering")).contains(TrangThaiVanChuyen.DANG_GIAO);
        assertThat(mapper.map("GHN", "delivered")).contains(TrangThaiVanChuyen.DA_GIAO);
        assertThat(mapper.map("GHN", "delivery_fail")).contains(TrangThaiVanChuyen.GIAO_THAT_BAI);
        assertThat(mapper.map("GHN", "waiting_to_return")).contains(TrangThaiVanChuyen.DA_HOAN_TRA);
        assertThat(mapper.map("GHN", "returned")).contains(TrangThaiVanChuyen.DA_HOAN_TRA);
        assertThat(mapper.map("GHN", "DA_GIAO")).contains(TrangThaiVanChuyen.DA_GIAO);
        assertThat(mapper.map("GHN", "out for delivery")).contains(TrangThaiVanChuyen.DANG_GIAO);
        assertThat(mapper.map("GHN", "something_new")).isEmpty();
        assertThat(mapper.map("GHN", null)).isEmpty();
    }

    @Test
    void carrierSpecificMappingOverridesDefaults() {
        ShippingProperties.Carrier carrier = new ShippingProperties.Carrier();
        carrier.getStatusMap().put("Shipping-Now", "DANG_GIAO");
        carrier.getStatusMap().put("delivered", "DANG_GIAO");
        properties.getCarriers().put("jt", carrier);

        assertThat(mapper.map("JT", "shipping now")).contains(TrangThaiVanChuyen.DANG_GIAO);
        assertThat(mapper.map("JT", "delivered")).contains(TrangThaiVanChuyen.DANG_GIAO);
        assertThat(mapper.map("GHN", "delivered")).contains(TrangThaiVanChuyen.DA_GIAO);
    }

    @Test
    void shipmentStatusMovesForwardOnlyAndFinalStatesAreTerminal() {
        assertThat(TrangThaiVanChuyen.CHO_LAY_HANG.canMoveTo(TrangThaiVanChuyen.DANG_LAY_HANG)).isTrue();
        assertThat(TrangThaiVanChuyen.CHO_LAY_HANG.canMoveTo(TrangThaiVanChuyen.DANG_GIAO)).isTrue();
        assertThat(TrangThaiVanChuyen.DANG_GIAO.canMoveTo(TrangThaiVanChuyen.DA_LAY_HANG)).isFalse();
        assertThat(TrangThaiVanChuyen.DANG_GIAO.canMoveTo(TrangThaiVanChuyen.GIAO_THAT_BAI)).isTrue();
        assertThat(TrangThaiVanChuyen.GIAO_THAT_BAI.canMoveTo(TrangThaiVanChuyen.DA_HOAN_TRA)).isTrue();
        assertThat(TrangThaiVanChuyen.CHO_LAY_HANG.canMoveTo(TrangThaiVanChuyen.DA_GIAO)).isFalse();
        for (TrangThaiVanChuyen next : TrangThaiVanChuyen.values()) {
            assertThat(TrangThaiVanChuyen.DA_GIAO.canMoveTo(next)).isFalse();
            assertThat(TrangThaiVanChuyen.DA_HOAN_TRA.canMoveTo(next)).isFalse();
        }
    }

    @Test
    void shipmentStatusDrivesOrderStatus() {
        assertThat(TrangThaiVanChuyen.CHO_LAY_HANG.toTrangThaiDonHang()).isEqualTo(TrangThaiDonHang.DA_XAC_NHAN);
        assertThat(TrangThaiVanChuyen.DANG_LAY_HANG.toTrangThaiDonHang()).isEqualTo(TrangThaiDonHang.DANG_LAY_HANG);
        assertThat(TrangThaiVanChuyen.DA_LAY_HANG.toTrangThaiDonHang()).isEqualTo(TrangThaiDonHang.DA_LAY_HANG);
        assertThat(TrangThaiVanChuyen.DANG_GIAO.toTrangThaiDonHang()).isEqualTo(TrangThaiDonHang.DANG_GIAO);
        assertThat(TrangThaiVanChuyen.GIAO_THAT_BAI.toTrangThaiDonHang()).isEqualTo(TrangThaiDonHang.DANG_GIAO);
        assertThat(TrangThaiVanChuyen.DA_GIAO.toTrangThaiDonHang()).isEqualTo(TrangThaiDonHang.DA_GIAO);
        assertThat(TrangThaiVanChuyen.DA_HOAN_TRA.toTrangThaiDonHang()).isEqualTo(TrangThaiDonHang.DA_HOAN_TRA);
    }
}
