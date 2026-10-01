package com.nhom9.oldbook_marketplace.store.service;

import com.nhom9.oldbook_marketplace.catalog.entity.CuaHang;
import com.nhom9.oldbook_marketplace.common.exception.BusinessException;
import com.nhom9.oldbook_marketplace.identity.entity.NguoiDung;
import com.nhom9.oldbook_marketplace.identity.repository.NguoiDungRepository;
import com.nhom9.oldbook_marketplace.store.dto.CreateCuaHangRequest;
import com.nhom9.oldbook_marketplace.store.dto.UpdateCuaHangRequest;
import com.nhom9.oldbook_marketplace.store.repository.CuaHangRepository;
import com.nhom9.oldbook_marketplace.store.security.StoreAccess;
import com.nhom9.oldbook_marketplace.system.entity.NhatKyHeThong;
import com.nhom9.oldbook_marketplace.system.repository.NhatKyHeThongRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreServiceTest {
    @Mock CuaHangRepository cuaHangRepository;
    @Mock NguoiDungRepository nguoiDungRepository;
    @Mock StoreAccess storeAccess;
    @Mock NhatKyHeThongRepository nhatKyHeThongRepository;
    @InjectMocks StoreService service;

    @Test
    void newStoreUsesAuthenticatedOwnerAndWaitsForApproval() {
        NguoiDung owner = NguoiDung.builder().maND(7).build();
        when(storeAccess.requireOwner()).thenReturn(7);
        when(cuaHangRepository.findByChuShop_MaND(7)).thenReturn(Optional.empty());
        when(nguoiDungRepository.findById(7)).thenReturn(Optional.of(owner));
        when(cuaHangRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(new CreateCuaHangRequest("  Shop  ", "  mô tả ", " Địa chỉ ", " 0900000000 "));

        ArgumentCaptor<CuaHang> saved = ArgumentCaptor.forClass(CuaHang.class);
        verify(cuaHangRepository).save(saved.capture());
        assertThat(saved.getValue().getChuShop()).isSameAs(owner);
        assertThat(response.tenCuaHang()).isEqualTo("Shop");
        assertThat(response.trangThaiDuyet()).isEqualTo("CHO_DUYET");
    }

    @Test
    void userCannotRegisterASecondStore() {
        when(storeAccess.requireOwner()).thenReturn(7);
        when(cuaHangRepository.findByChuShop_MaND(7)).thenReturn(Optional.of(store("DA_DUYET")));

        assertThatThrownBy(() -> service.create(createRequest())).isInstanceOf(BusinessException.class)
                .hasMessage("Bạn đã có cửa hàng");
        verify(cuaHangRepository, never()).save(any());
    }

    @Test
    void duplicateStoreNameIsRejectedAfterTrimming() {
        when(storeAccess.requireOwner()).thenReturn(7);
        when(cuaHangRepository.findByChuShop_MaND(7)).thenReturn(Optional.empty());
        when(cuaHangRepository.existsByTenCuaHangIgnoreCase("Shop")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateCuaHangRequest(" Shop ", null, "Địa chỉ", "0900000000")))
                .isInstanceOf(BusinessException.class).hasMessage("Tên cửa hàng đã được sử dụng");
        verify(cuaHangRepository, never()).save(any());
    }

    @Test
    void rejectedStoreCanBeEditedAndResubmitted() {
        CuaHang store = store("TU_CHOI");
        when(storeAccess.requireOwner()).thenReturn(7);
        when(cuaHangRepository.findByChuShopMaNDForUpdate(7)).thenReturn(Optional.of(store));
        when(cuaHangRepository.save(store)).thenReturn(store);

        var response = service.updateMyStore(new UpdateCuaHangRequest("Shop sửa", null, "Địa chỉ mới", "0900000001"));

        assertThat(response.trangThaiDuyet()).isEqualTo("CHO_DUYET");
        assertThat(response.tenCuaHang()).isEqualTo("Shop sửa");
        assertThat(store.getChuShop().getMaND()).isEqualTo(7);
        assertThat(response.lyDoTuChoi()).isNull();
        verifyNoInteractions(nhatKyHeThongRepository);
    }

    @Test
    void approvedStoreContentChangeAlsoRequiresReapproval() {
        CuaHang store = store("DA_DUYET");
        when(storeAccess.requireOwner()).thenReturn(7);
        when(cuaHangRepository.findByChuShopMaNDForUpdate(7)).thenReturn(Optional.of(store));
        when(cuaHangRepository.save(store)).thenReturn(store);

        assertThat(service.updateMyStore(new UpdateCuaHangRequest("Shop sửa", null, "Địa chỉ", "0900000000"))
                .trangThaiDuyet()).isEqualTo("CHO_DUYET");
    }

    @Test
    void ownerCanViewPendingStore() {
        when(storeAccess.requireOwner()).thenReturn(7);
        when(cuaHangRepository.findByChuShop_MaND(7)).thenReturn(Optional.of(store("CHO_DUYET")));

        assertThat(service.getMyStore().trangThaiDuyet()).isEqualTo("CHO_DUYET");
        verify(cuaHangRepository).findByChuShop_MaND(7);
        verifyNoInteractions(nhatKyHeThongRepository);
    }

    @Test
    void ownerSeesLatestRejectionFeedbackForTheirOwnStore() {
        when(storeAccess.requireOwner()).thenReturn(7);
        when(cuaHangRepository.findByChuShop_MaND(7)).thenReturn(Optional.of(store("TU_CHOI")));
        when(nhatKyHeThongRepository
                .findFirstByLoaiDoiTuongAndMaDoiTuongAndHanhDongOrderByThoiGianDescMaNhatKyDesc(
                        "CUA_HANG", 3, "TU_CHOI_CUA_HANG"))
                .thenReturn(Optional.of(NhatKyHeThong.builder().moTa("Bổ sung địa chỉ cửa hàng").build()));

        var response = service.getMyStore();

        assertThat(response.maCH()).isEqualTo(3);
        assertThat(response.lyDoTuChoi()).isEqualTo("Bổ sung địa chỉ cửa hàng");
        verify(nhatKyHeThongRepository)
                .findFirstByLoaiDoiTuongAndMaDoiTuongAndHanhDongOrderByThoiGianDescMaNhatKyDesc(
                        "CUA_HANG", 3, "TU_CHOI_CUA_HANG");
    }

    @Test
    void rejectedLegacyStoreWithoutJournalCanStillBeViewed() {
        when(storeAccess.requireOwner()).thenReturn(7);
        when(cuaHangRepository.findByChuShop_MaND(7)).thenReturn(Optional.of(store("TU_CHOI")));
        when(nhatKyHeThongRepository
                .findFirstByLoaiDoiTuongAndMaDoiTuongAndHanhDongOrderByThoiGianDescMaNhatKyDesc(
                        "CUA_HANG", 3, "TU_CHOI_CUA_HANG"))
                .thenReturn(Optional.empty());

        assertThat(service.getMyStore().lyDoTuChoi()).isNull();
    }

    @Test
    void unauthorizedCallerCannotReadStoreData() {
        when(storeAccess.requireOwner()).thenThrow(new AccessDeniedException("Không có quyền"));

        assertThatThrownBy(service::getMyStore).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(cuaHangRepository, nguoiDungRepository, nhatKyHeThongRepository);
    }

    @Test
    void directServiceCallAlsoRejectsBlankStoreName() {
        when(storeAccess.requireOwner()).thenReturn(7);
        when(cuaHangRepository.findByChuShop_MaND(7)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateCuaHangRequest(" ", null, "Địa chỉ", "0900000000")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Tên cửa hàng");
        verify(cuaHangRepository, never()).save(any());
    }

    private CreateCuaHangRequest createRequest() {
        return new CreateCuaHangRequest("Shop", null, "Địa chỉ", "0900000000");
    }

    private CuaHang store(String status) {
        return CuaHang.builder().maCH(3).chuShop(NguoiDung.builder().maND(7).build())
                .tenCuaHang("Shop").trangThaiDuyet(status).build();
    }
}
