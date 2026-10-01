package com.oldbook.store.service;

import com.oldbook.service.store.StoreSachService;

import com.oldbook.entity.catalog.CuaHang;
import com.oldbook.entity.catalog.DanhMuc;
import com.oldbook.entity.catalog.Sach;
import com.oldbook.repository.catalog.DanhMucRepository;
import com.oldbook.repository.catalog.SachRepository;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.dto.store.CreateSachRequest;
import com.oldbook.dto.store.UpdateSachRequest;
import com.oldbook.dto.store.UpdateTonKhoRequest;
import com.oldbook.dto.store.UpdateTrangThaiBanRequest;
import com.oldbook.repository.store.CuaHangRepository;
import com.oldbook.security.store.StoreAccess;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoreSachServiceTest {
    @Mock CuaHangRepository cuaHangRepository;
    @Mock SachRepository sachRepository;
    @Mock DanhMucRepository danhMucRepository;
    @Mock StoreAccess storeAccess;
    @InjectMocks StoreSachService service;

    @Test
    void newBookBelongsToCurrentStoreAndNeedsApproval() {
        CuaHang store = store(3, "DA_DUYET");
        ownerWithLockedStore(store);
        when(danhMucRepository.findById(2)).thenReturn(Optional.of(category()));
        when(sachRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(createRequest(5));

        assertThat(response.maCH()).isEqualTo(3);
        assertThat(response.trangThaiDuyet()).isEqualTo("CHO_DUYET");
        assertThat(response.trangThaiBan()).isEqualTo("DANG_BAN");
        assertThat(response.soLuongTon()).isEqualTo(5);
    }

    @Test
    void pendingStoreCannotCreateBooks() {
        ownerWithLockedStore(store(3, "CHO_DUYET"));

        assertThatThrownBy(() -> service.create(createRequest(5))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("phê duyệt");
        verifyNoInteractions(sachRepository, danhMucRepository);
    }

    @Test
    void invalidCategoryIsRejected() {
        ownerWithLockedStore(store(3, "DA_DUYET"));
        when(danhMucRepository.findById(2)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(createRequest(5))).isInstanceOf(BusinessException.class)
                .hasMessage("Danh mục không tồn tại");
        verify(sachRepository, never()).save(any());
    }

    @Test
    void contentEditResubmitsBookButPreservesStockAndSaleStatus() {
        CuaHang store = store(3, "DA_DUYET");
        Sach book = book(store);
        book.setTrangThaiDuyet("TU_CHOI");
        book.setLyDoTuChoi("Thông tin thiếu");
        book.setTrangThaiBan("NGUNG_BAN");
        ownerWithLockedStore(store);
        when(sachRepository.findByIdForUpdate(10)).thenReturn(Optional.of(book));
        when(danhMucRepository.findById(2)).thenReturn(Optional.of(category()));
        when(sachRepository.save(book)).thenReturn(book);

        var response = service.update(10, updateRequest());

        assertThat(response.trangThaiDuyet()).isEqualTo("CHO_DUYET");
        assertThat(response.lyDoTuChoi()).isNull();
        assertThat(response.soLuongTon()).isEqualTo(5);
        assertThat(response.trangThaiBan()).isEqualTo("NGUNG_BAN");
    }

    @Test
    void inventoryUpdateLocksStoreBeforeBookAndPreservesApproval() {
        CuaHang store = store(3, "DA_DUYET");
        Sach book = book(store);
        ownerWithLockedStore(store);
        when(sachRepository.findByIdForUpdate(10)).thenReturn(Optional.of(book));
        when(sachRepository.save(book)).thenReturn(book);

        var response = service.updateStock(10, new UpdateTonKhoRequest(0));

        assertThat(response.soLuongTon()).isZero();
        assertThat(response.trangThaiDuyet()).isEqualTo("DA_DUYET");
        var order = inOrder(cuaHangRepository, sachRepository);
        order.verify(cuaHangRepository).findByChuShopMaNDForUpdate(7);
        order.verify(sachRepository).findByIdForUpdate(10);
        order.verify(sachRepository).save(book);
    }

    @Test
    void negativeInventoryCannotBeWrittenEvenThroughDirectServiceCall() {
        when(storeAccess.requireOwner()).thenReturn(7);

        assertThatThrownBy(() -> service.updateStock(10, new UpdateTonKhoRequest(-1)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("không âm");
        verifyNoInteractions(cuaHangRepository, sachRepository);
    }

    @Test
    void missingInventoryCannotBeWritten() {
        when(storeAccess.requireOwner()).thenReturn(7);

        assertThatThrownBy(() -> service.updateStock(10, new UpdateTonKhoRequest(null)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(cuaHangRepository, sachRepository);
    }

    @Test
    void anotherOwnersInventoryCannotBeChanged() {
        ownerWithLockedStore(store(3, "DA_DUYET"));
        Sach foreignBook = book(store(9, "DA_DUYET"));
        when(sachRepository.findByIdForUpdate(10)).thenReturn(Optional.of(foreignBook));

        assertThatThrownBy(() -> service.updateStock(10, new UpdateTonKhoRequest(99)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(foreignBook.getSoLuongTon()).isEqualTo(5);
        verify(sachRepository, never()).save(any());
    }

    @Test
    void anotherOwnersBookCannotBeViewed() {
        when(storeAccess.requireOwner()).thenReturn(7);
        when(cuaHangRepository.findByChuShop_MaND(7)).thenReturn(Optional.of(store(3, "DA_DUYET")));
        when(sachRepository.findById(10)).thenReturn(Optional.of(book(store(9, "DA_DUYET"))));

        assertThatThrownBy(() -> service.getMyBook(10)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void ownerCanInspectRejectedBook() {
        CuaHang store = store(3, "TU_CHOI");
        Sach book = book(store);
        book.setTrangThaiDuyet("TU_CHOI");
        when(storeAccess.requireOwner()).thenReturn(7);
        when(cuaHangRepository.findByChuShop_MaND(7)).thenReturn(Optional.of(store));
        when(sachRepository.findById(10)).thenReturn(Optional.of(book));

        assertThat(service.getMyBook(10).trangThaiDuyet()).isEqualTo("TU_CHOI");
    }

    @Test
    void saleToggleDoesNotResetApprovalOrInventory() {
        CuaHang store = store(3, "DA_DUYET");
        Sach book = book(store);
        ownerWithLockedStore(store);
        when(sachRepository.findByIdForUpdate(10)).thenReturn(Optional.of(book));
        when(sachRepository.save(book)).thenReturn(book);

        var response = service.updateSaleStatus(10, new UpdateTrangThaiBanRequest("NGUNG_BAN"));

        assertThat(response.trangThaiBan()).isEqualTo("NGUNG_BAN");
        assertThat(response.trangThaiDuyet()).isEqualTo("DA_DUYET");
        assertThat(response.soLuongTon()).isEqualTo(5);
    }

    @Test
    void approvalStatusCannotBePassedToSaleToggle() {
        when(storeAccess.requireOwner()).thenReturn(7);

        assertThatThrownBy(() -> service.updateSaleStatus(10, new UpdateTrangThaiBanRequest("DA_DUYET")))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(cuaHangRepository, sachRepository);
    }

    @Test
    void ownerListRejectsUnboundedPagination() {
        ownerWithReadableStore();

        assertThatThrownBy(() -> service.listMyBooks(0, 101, "ngayTao,desc", null))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(sachRepository);
    }

    @Test
    void ownerListRejectsUnknownSortProperty() {
        ownerWithReadableStore();

        assertThatThrownBy(() -> service.listMyBooks(0, 20, "cuaHang.chuShop.email,asc", null))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(sachRepository);
    }

    @Test
    void ownerListRejectsPaginationOffsetThatCannotFitJpa() {
        ownerWithReadableStore();

        assertThatThrownBy(() -> service.listMyBooks(Integer.MAX_VALUE, 20, "ngayTao,desc", null))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(sachRepository);
    }

    @Test
    void ownerListUsesStableBoundedPagination() {
        ownerWithReadableStore();
        when(sachRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(Page.empty());

        service.listMyBooks(1, 10, "giaBanCu,asc", "CHO_DUYET");

        ArgumentCaptor<PageRequest> pages = ArgumentCaptor.forClass(PageRequest.class);
        verify(sachRepository).findAll(any(Specification.class), pages.capture());
        assertThat(pages.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pages.getValue().getPageSize()).isEqualTo(10);
        assertThat(pages.getValue().getSort().getOrderFor("giaBanCu").isAscending()).isTrue();
        assertThat(pages.getValue().getSort().getOrderFor("maSach")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void ownerListAlwaysConstrainsQueryToAuthenticatedOwnersStore() {
        ownerWithReadableStore();
        when(sachRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(Page.empty());
        service.listMyBooks(0, 20, "ngayTao,desc", "CHO_DUYET");

        ArgumentCaptor<Specification<Sach>> specifications = ArgumentCaptor.forClass(Specification.class);
        verify(sachRepository).findAll(specifications.capture(), any(PageRequest.class));
        Root<Sach> bookRoot = mock(Root.class);
        Path<Object> storePath = mock(Path.class);
        Path<Object> storeIdPath = mock(Path.class);
        Path<Object> approvalPath = mock(Path.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Predicate owned = mock(Predicate.class);
        Predicate approved = mock(Predicate.class);
        Predicate combined = mock(Predicate.class);
        when(bookRoot.get("cuaHang")).thenReturn(storePath);
        when(storePath.get("maCH")).thenReturn(storeIdPath);
        when(bookRoot.get("trangThaiDuyet")).thenReturn(approvalPath);
        when(builder.equal(storeIdPath, 3)).thenReturn(owned);
        when(builder.equal(approvalPath, "CHO_DUYET")).thenReturn(approved);
        when(builder.and(owned, approved)).thenReturn(combined);

        assertThat(specifications.getValue().toPredicate(bookRoot, null, builder)).isSameAs(combined);
        verify(builder).equal(storeIdPath, 3);
    }

    @Test
    void invalidApprovalFilterIsRejected() {
        ownerWithReadableStore();

        assertThatThrownBy(() -> service.listMyBooks(0, 20, "ngayTao,desc", "PUBLIC"))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(sachRepository);
    }

    @Test
    void roleIsCheckedBeforeReadingBooks() {
        when(storeAccess.requireOwner()).thenThrow(new AccessDeniedException("Không có quyền"));

        assertThatThrownBy(() -> service.getMyBook(10)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(cuaHangRepository, sachRepository);
    }

    private void ownerWithLockedStore(CuaHang store) {
        when(storeAccess.requireOwner()).thenReturn(7);
        when(cuaHangRepository.findByChuShopMaNDForUpdate(7)).thenReturn(Optional.of(store));
    }

    private void ownerWithReadableStore() {
        when(storeAccess.requireOwner()).thenReturn(7);
        when(cuaHangRepository.findByChuShop_MaND(7)).thenReturn(Optional.of(store(3, "DA_DUYET")));
    }

    private CuaHang store(int id, String status) {
        return CuaHang.builder().maCH(id).tenCuaHang("Shop " + id).trangThaiDuyet(status)
                .chuShop(NguoiDung.builder().maND(id == 3 ? 7 : 8).build()).build();
    }

    private Sach book(CuaHang store) {
        return Sach.builder().maSach(10).cuaHang(store).danhMuc(category()).tenSach("Sách")
                .giaBanCu(new BigDecimal("25000.00")).soLuongTon(5)
                .trangThaiDuyet("DA_DUYET").trangThaiBan("DANG_BAN").build();
    }

    private DanhMuc category() {
        return DanhMuc.builder().maDM(2).tenDanhMuc("Văn học").build();
    }

    private CreateSachRequest createRequest(int stock) {
        return new CreateSachRequest(2, " Sách ", "Tác giả", null, 2020,
                new BigDecimal("50000.00"), new BigDecimal("25000.00"), new BigDecimal("80.00"),
                "Tốt", "Mô tả", stock, null);
    }

    private UpdateSachRequest updateRequest() {
        return new UpdateSachRequest(2, "Sách sửa", "Tác giả", null, 2020,
                new BigDecimal("50000.00"), new BigDecimal("30000.00"), new BigDecimal("80.00"),
                "Tốt", "Mô tả sửa", null);
    }
}
