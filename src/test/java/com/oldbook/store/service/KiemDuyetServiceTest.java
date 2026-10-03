package com.oldbook.store.service;

import com.oldbook.service.store.KiemDuyetService;

import com.oldbook.filter.auth.JwtAuthenticationFilter.AuthenticatedUserDetails;
import com.oldbook.entity.catalog.CuaHang;
import com.oldbook.entity.catalog.DanhMuc;
import com.oldbook.entity.catalog.Sach;
import com.oldbook.repository.catalog.SachRepository;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.entity.identity.TaiKhoan;
import com.oldbook.repository.identity.TaiKhoanRepository;
import com.oldbook.dto.store.KiemDuyetSearchRequest;
import com.oldbook.dto.store.TuChoiRequest;
import com.oldbook.repository.store.CuaHangRepository;
import com.oldbook.security.store.StoreAccess;
import com.oldbook.entity.system.NhatKyHeThong;
import com.oldbook.repository.system.NhatKyHeThongRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KiemDuyetServiceTest {

    @Mock private CuaHangRepository cuaHangRepository;
    @Mock private SachRepository sachRepository;
    @Mock private TaiKhoanRepository taiKhoanRepository;
    @Mock private NhatKyHeThongRepository nhatKyHeThongRepository;
    @Mock private StoreAccess storeAccess;
    @Mock private EntityManager entityManager;

    private KiemDuyetService service;
    private final AuthenticatedUserDetails actor = new AuthenticatedUserDetails(25, 40, "QUAN_LY");

    @BeforeEach
    void setUp() {
        service = new KiemDuyetService(cuaHangRepository, sachRepository, taiKhoanRepository,
                nhatKyHeThongRepository, storeAccess, entityManager);
        when(storeAccess.requireModerator()).thenReturn(actor);
    }

    @Test
    void deniedModeratorCannotReadOrChangeData() {
        when(storeAccess.requireModerator()).thenThrow(new AccessDeniedException("Không có quyền"));

        assertThatThrownBy(() -> service.duyetSach(7)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.timCuaHang(new KiemDuyetSearchRequest()))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(cuaHangRepository, sachRepository, taiKhoanRepository,
                nhatKyHeThongRepository, entityManager);
    }

    @Test
    void approvesBookAndAuditsTrustedActorAfterStoreBeforeBookLocks() {
        CuaHang store = store("DA_DUYET");
        Sach book = book(store, "CHO_DUYET");
        book.setLyDoTuChoi("Lý do từ lần trước");
        prepareBookLocks(store, book);
        TaiKhoan account = actorAccount();

        var response = service.duyetSach(7);

        assertThat(response.trangThaiDuyet()).isEqualTo("DA_DUYET");
        assertThat(book.getLyDoTuChoi()).isNull();
        NhatKyHeThong audit = captureAudit();
        assertThat(audit.getTaiKhoan()).isSameAs(account);
        assertThat(audit.getHanhDong()).isEqualTo("DUYET_SACH");
        assertThat(audit.getLoaiDoiTuong()).isEqualTo("SACH");
        assertThat(audit.getMaDoiTuong()).isEqualTo(7);
        InOrder order = inOrder(sachRepository, cuaHangRepository, entityManager, nhatKyHeThongRepository);
        order.verify(sachRepository).findById(7);
        order.verify(cuaHangRepository).findByIdForUpdate(3);
        order.verify(sachRepository).findByIdForUpdate(7);
        order.verify(entityManager).refresh(book);
        order.verify(sachRepository).save(book);
        order.verify(nhatKyHeThongRepository).save(any(NhatKyHeThong.class));
    }

    @Test
    void rejectsBookAndStoresTrimmedReasonInBookAndAudit() {
        CuaHang store = store("TU_CHOI");
        Sach book = book(store, "CHO_DUYET");
        prepareBookLocks(store, book);
        actorAccount();

        var response = service.tuChoiSach(7, new TuChoiRequest("  Thiếu ảnh bìa  "));

        assertThat(response.trangThaiDuyet()).isEqualTo("TU_CHOI");
        assertThat(book.getLyDoTuChoi()).isEqualTo("Thiếu ảnh bìa");
        NhatKyHeThong audit = captureAudit();
        assertThat(audit.getHanhDong()).isEqualTo("TU_CHOI_SACH");
        assertThat(audit.getMoTa()).isEqualTo("Thiếu ảnh bìa");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CHO_DUYET", "TU_CHOI"})
    void cannotApproveBookBeforeStoreIsApproved(String storeStatus) {
        CuaHang store = store(storeStatus);
        Sach book = book(store, "CHO_DUYET");
        prepareBookLocks(store, book);

        assertThatThrownBy(() -> service.duyetSach(7)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cửa hàng phải được phê duyệt");

        assertThat(book.getTrangThaiDuyet()).isEqualTo("CHO_DUYET");
        verify(sachRepository, never()).save(any());
        verifyNoInteractions(nhatKyHeThongRepository, taiKhoanRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DA_DUYET", "TU_CHOI", "UNKNOWN"})
    void bookApprovalOnlyTransitionsFromPending(String status) {
        CuaHang store = store("DA_DUYET");
        Sach book = book(store, status);
        prepareBookLocks(store, book);

        assertThatThrownBy(() -> service.duyetSach(7)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("chờ duyệt");

        verify(sachRepository, never()).save(any());
        verifyNoInteractions(nhatKyHeThongRepository, taiKhoanRepository);
    }

    @Test
    void rejectsRepeatBookRejectionWithoutAuditOrStateChange() {
        CuaHang store = store("DA_DUYET");
        Sach book = book(store, "TU_CHOI");
        book.setLyDoTuChoi("Lý do cũ");
        prepareBookLocks(store, book);

        assertThatThrownBy(() -> service.tuChoiSach(7, new TuChoiRequest("Lý do mới")))
                .isInstanceOf(BusinessException.class);

        assertThat(book.getLyDoTuChoi()).isEqualTo("Lý do cũ");
        verify(sachRepository, never()).save(any());
        verifyNoInteractions(nhatKyHeThongRepository, taiKhoanRepository);
    }

    @Test
    void refreshPreventsApprovingAStalePendingBookAfterWaitingForLock() {
        CuaHang store = store("DA_DUYET");
        Sach book = book(store, "CHO_DUYET");
        prepareBookLocks(store, book);
        doAnswer(invocation -> {
            book.setTrangThaiDuyet("TU_CHOI");
            return null;
        }).when(entityManager).refresh(book);

        assertThatThrownBy(() -> service.duyetSach(7)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("chờ duyệt");

        verify(sachRepository, never()).save(any());
        verifyNoInteractions(nhatKyHeThongRepository, taiKhoanRepository);
    }

    @Test
    void refusesBookWhoseStoreAssociationChangedWhileWaitingForLocks() {
        CuaHang store = store("DA_DUYET");
        Sach original = book(store, "CHO_DUYET");
        CuaHang differentStore = CuaHang.builder().maCH(99).trangThaiDuyet("DA_DUYET").build();
        Sach changed = book(differentStore, "CHO_DUYET");
        when(sachRepository.findById(7)).thenReturn(Optional.of(original));
        when(cuaHangRepository.findByIdForUpdate(3)).thenReturn(Optional.of(store));
        when(sachRepository.findByIdForUpdate(7)).thenReturn(Optional.of(changed));

        assertThatThrownBy(() -> service.duyetSach(7)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã thay đổi");

        verify(sachRepository, never()).save(any());
        verifyNoInteractions(nhatKyHeThongRepository, taiKhoanRepository);
    }

    @Test
    void approvesStoreAndAuditsTrustedActor() {
        CuaHang store = store("CHO_DUYET");
        when(cuaHangRepository.findByIdForUpdate(3)).thenReturn(Optional.of(store));
        TaiKhoan account = actorAccount();

        var response = service.duyetCuaHang(3);

        assertThat(response.trangThaiDuyet()).isEqualTo("DA_DUYET");
        NhatKyHeThong audit = captureAudit();
        assertThat(audit.getTaiKhoan()).isSameAs(account);
        assertThat(audit.getHanhDong()).isEqualTo("DUYET_CUA_HANG");
        assertThat(audit.getLoaiDoiTuong()).isEqualTo("CUA_HANG");
        assertThat(audit.getMaDoiTuong()).isEqualTo(3);
        verify(cuaHangRepository).save(store);
    }

    @Test
    void rejectionReasonForStoreIsPersistedInExistingAudit() {
        CuaHang store = store("CHO_DUYET");
        when(cuaHangRepository.findByIdForUpdate(3)).thenReturn(Optional.of(store));
        actorAccount();

        var response = service.tuChoiCuaHang(3, new TuChoiRequest(" Địa chỉ chưa đầy đủ "));

        assertThat(response.trangThaiDuyet()).isEqualTo("TU_CHOI");
        assertThat(response.lyDoTuChoi()).isEqualTo("Địa chỉ chưa đầy đủ");
        NhatKyHeThong audit = captureAudit();
        assertThat(audit.getHanhDong()).isEqualTo("TU_CHOI_CUA_HANG");
        assertThat(audit.getMoTa()).isEqualTo("Địa chỉ chưa đầy đủ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DA_DUYET", "TU_CHOI"})
    void cannotModerateStoreTwice(String status) {
        CuaHang store = store(status);
        when(cuaHangRepository.findByIdForUpdate(3)).thenReturn(Optional.of(store));

        assertThatThrownBy(() -> service.duyetCuaHang(3)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.tuChoiCuaHang(3, new TuChoiRequest("Lý do")))
                .isInstanceOf(BusinessException.class);

        verify(cuaHangRepository, never()).save(any());
        verifyNoInteractions(nhatKyHeThongRepository, taiKhoanRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void blankRejectionReasonsAreRefusedBeforeReadingData(String reason) {
        assertThatThrownBy(() -> service.tuChoiCuaHang(3, new TuChoiRequest(reason)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("để trống");
        assertThatThrownBy(() -> service.tuChoiSach(7, new TuChoiRequest(reason)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("để trống");
        verifyNoInteractions(cuaHangRepository, sachRepository, nhatKyHeThongRepository, taiKhoanRepository);
    }

    @Test
    void rejectionReasonLimitIsCheckedByService() {
        assertThatThrownBy(() -> service.tuChoiSach(7, new TuChoiRequest("a".repeat(501))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("500");
        verifyNoInteractions(cuaHangRepository, sachRepository, nhatKyHeThongRepository, taiKhoanRepository);
    }

    @Test
    void missingStoreOrBookDoesNotCreateAudit() {
        assertThatThrownBy(() -> service.duyetCuaHang(3)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Không tìm thấy cửa hàng");
        assertThatThrownBy(() -> service.duyetSach(7)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Không tìm thấy sách");
        verifyNoInteractions(nhatKyHeThongRepository, taiKhoanRepository);
    }

    @Test
    void reviewerCanReadRejectedStoreAndBookDetails() {
        CuaHang store = store("TU_CHOI");
        Sach book = book(store, "TU_CHOI");
        book.setLyDoTuChoi("Thiếu ảnh");
        when(cuaHangRepository.findById(3)).thenReturn(Optional.of(store));
        when(sachRepository.findById(7)).thenReturn(Optional.of(book));
        when(nhatKyHeThongRepository
                .findFirstByLoaiDoiTuongAndMaDoiTuongAndHanhDongOrderByThoiGianDescMaNhatKyDesc(
                        "CUA_HANG", 3, "TU_CHOI_CUA_HANG"))
                .thenReturn(Optional.of(NhatKyHeThong.builder().moTa("Cần bổ sung địa chỉ").build()));

        var storeResponse = service.chiTietCuaHang(3);
        assertThat(storeResponse.trangThaiDuyet()).isEqualTo("TU_CHOI");
        assertThat(storeResponse.lyDoTuChoi()).isEqualTo("Cần bổ sung địa chỉ");
        assertThat(service.chiTietSach(7).lyDoTuChoi()).isEqualTo("Thiếu ảnh");
        verifyNoInteractions(taiKhoanRepository);
        verify(nhatKyHeThongRepository, never()).save(any());
    }

    @Test
    void approvedStoreDoesNotExposeRejectionReasonFromAnEarlierReview() {
        CuaHang store = store("DA_DUYET");
        when(cuaHangRepository.findById(3)).thenReturn(Optional.of(store));

        assertThat(service.chiTietCuaHang(3).lyDoTuChoi()).isNull();

        verifyNoInteractions(nhatKyHeThongRepository);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 101})
    void rejectsOutOfRangePageSize(int size) {
        var request = new KiemDuyetSearchRequest();
        request.setSize(size);
        assertThatThrownBy(() -> service.timSach(request)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(cuaHangRepository, sachRepository);
    }

    @Test
    void rejectsUnknownStatusOrNegativePage() {
        var request = new KiemDuyetSearchRequest();
        request.setTrangThaiDuyet("ANY");
        assertThatThrownBy(() -> service.timCuaHang(request)).isInstanceOf(BusinessException.class);
        request.setTrangThaiDuyet("CHO_DUYET");
        request.setPage(-1);
        assertThatThrownBy(() -> service.timSach(request)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(cuaHangRepository, sachRepository);
    }

    @Test
    void rejectsPaginationOffsetBeyondJpaIntegerLimitBeforeQuerying() {
        var request = new KiemDuyetSearchRequest();
        request.setPage(Integer.MAX_VALUE);
        request.setSize(2);

        assertThatThrownBy(() -> service.timCuaHang(request)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("giới hạn phân trang");
        assertThatThrownBy(() -> service.timSach(request)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("giới hạn phân trang");
        verifyNoInteractions(cuaHangRepository, sachRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listsPendingStoresWithBoundedPaginationAndStableOldestFirstOrder() {
        var request = new KiemDuyetSearchRequest();
        when(cuaHangRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(store("CHO_DUYET"))));

        var response = service.timCuaHang(request);

        assertThat(request.getTrangThaiDuyet()).isEqualTo("CHO_DUYET");
        assertThat(response.content()).hasSize(1);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(cuaHangRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort().getOrderFor("ngayTao").isAscending()).isTrue();
        assertThat(pageable.getValue().getSort().getOrderFor("maCH").isAscending()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectedStoreListIncludesLatestRejectionFeedback() {
        var request = new KiemDuyetSearchRequest();
        request.setTrangThaiDuyet("TU_CHOI");
        when(cuaHangRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(store("TU_CHOI"))));
        when(nhatKyHeThongRepository
                .findFirstByLoaiDoiTuongAndMaDoiTuongAndHanhDongOrderByThoiGianDescMaNhatKyDesc(
                        "CUA_HANG", 3, "TU_CHOI_CUA_HANG"))
                .thenReturn(Optional.of(NhatKyHeThong.builder().moTa("Cần bổ sung số điện thoại").build()));

        var response = service.timCuaHang(request);

        assertThat(response.content().getFirst().lyDoTuChoi()).isEqualTo("Cần bổ sung số điện thoại");
    }

    private CuaHang store(String status) {
        return CuaHang.builder().maCH(3).tenCuaHang("Shop sách").trangThaiDuyet(status).build();
    }

    private Sach book(CuaHang store, String status) {
        return Sach.builder().maSach(7).cuaHang(store).tenSach("Java")
                .danhMuc(DanhMuc.builder().maDM(2).tenDanhMuc("Công nghệ").build())
                .trangThaiDuyet(status).trangThaiBan("DANG_BAN").soLuongTon(5).build();
    }

    private void prepareBookLocks(CuaHang store, Sach book) {
        when(sachRepository.findById(7)).thenReturn(Optional.of(book));
        when(cuaHangRepository.findByIdForUpdate(3)).thenReturn(Optional.of(store));
        when(sachRepository.findByIdForUpdate(7)).thenReturn(Optional.of(book));
    }

    private TaiKhoan actorAccount() {
        TaiKhoan account = TaiKhoan.builder().maTK(actor.maTK()).build();
        when(taiKhoanRepository.getReferenceById(actor.maTK())).thenReturn(account);
        return account;
    }

    private NhatKyHeThong captureAudit() {
        ArgumentCaptor<NhatKyHeThong> captor = ArgumentCaptor.forClass(NhatKyHeThong.class);
        verify(nhatKyHeThongRepository).save(captor.capture());
        return captor.getValue();
    }
}
