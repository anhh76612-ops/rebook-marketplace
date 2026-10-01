package com.oldbook.dto.order;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreOrderResponse {
    private Integer maDHCH;
    private Integer maCH;
    private String tenCuaHang;
    private BigDecimal tienHangCH;
    private BigDecimal phiShipCH;
    private BigDecimal tongTienShopOrder;
    private String trangThaiDonHang;
    private String trangThaiThanhToanShop;
    private List<OrderItemResponse> items;
}
