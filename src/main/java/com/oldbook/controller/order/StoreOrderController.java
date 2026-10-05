package com.oldbook.controller.order;

import com.oldbook.dto.common.ApiResponse;
import com.oldbook.dto.order.SellerOrderResponse;
import com.oldbook.security.store.StoreAccess;
import com.oldbook.service.order.StoreOrderService;
import com.oldbook.service.shipping.ShipmentTrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/store/orders")
@RequiredArgsConstructor
public class StoreOrderController {

    private final StoreOrderService storeOrderService;
    private final ShipmentTrackingService shipmentTrackingService;
    private final StoreAccess storeAccess;

    @GetMapping
    public ApiResponse<List<SellerOrderResponse>> getMyStoreOrders(
            @RequestParam(required = false) String trangThai
    ) {
        Integer maND = storeAccess.requireOwner();
        return ApiResponse.success(storeOrderService.getMyStoreOrders(maND, trangThai));
    }

    @PostMapping("/{maDHCH}/confirm")
    public ApiResponse<SellerOrderResponse> confirm(@PathVariable Integer maDHCH) {
        Integer maND = storeAccess.requireOwner();
        return ApiResponse.success(storeOrderService.confirmOrder(maND, maDHCH));
    }

    /** Kéo trạng thái mới nhất từ đơn vị vận chuyển (không cần chờ chu kỳ đồng bộ). */
    @PostMapping("/{maDHCH}/sync-shipping")
    public ApiResponse<SellerOrderResponse> syncShipping(@PathVariable Integer maDHCH) {
        Integer maND = storeAccess.requireOwner();
        storeOrderService.getMyStoreOrder(maND, maDHCH); // kiểm tra quyền sở hữu
        shipmentTrackingService.pullLatest(maDHCH);
        return ApiResponse.success(storeOrderService.getMyStoreOrder(maND, maDHCH));
    }
}
