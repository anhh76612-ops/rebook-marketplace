package com.oldbook.order;

import com.oldbook.dto.order.StoreOrderResponse;
import com.oldbook.entity.catalog.CuaHang;
import com.oldbook.entity.catalog.Sach;
import com.oldbook.entity.order.ChiTietDonHang;
import com.oldbook.entity.order.DonHangCuaHang;
import com.oldbook.repository.order.ChiTietDonHangRepository;
import com.oldbook.repository.shipping.LichSuVanChuyenRepository;
import com.oldbook.repository.shipping.VanChuyenRepository;
import com.oldbook.service.order.OrderMapper;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderMapperTest {
    @Mock ChiTietDonHangRepository items;
    @Mock VanChuyenRepository shipments;
    @Mock LichSuVanChuyenRepository history;
    @InjectMocks OrderMapper mapper;

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"https://example.com/book.jpg", "/images/book.jpg"})
    void includesBookCoverInCustomerAndSellerOrderItems(String imageUrl) {
        Sach book = Sach.builder().maSach(3).tenSach("Book").hinhAnhUrl(imageUrl).build();
        DonHangCuaHang store = DonHangCuaHang.builder().maDHCH(2)
                .cuaHang(CuaHang.builder().maCH(1).tenCuaHang("Store").build()).build();
        when(items.findAllByDonHangCuaHang_MaDHCHOrderByMaCTDHAsc(2))
                .thenReturn(List.of(ChiTietDonHang.builder().maCTDH(4).sach(book).soLuong(1).build()));
        when(shipments.findAllByDonHangCuaHang_MaDHCHIn(List.of(2))).thenReturn(List.of());

        StoreOrderResponse customer = mapper.toStoreOrderResponses(List.of(store)).getFirst();
        StoreOrderResponse seller = mapper.toStoreOrderResponse(store, null);

        for (StoreOrderResponse response : List.of(customer, seller)) {
            assertThat(response.getItems()).hasSize(1);
            assertThat(response.getItems().getFirst().getHinhAnhUrl()).isEqualTo(imageUrl);
            assertThat(response.getItems().getFirst().getMaSach()).isEqualTo(3);
        }
    }
}
