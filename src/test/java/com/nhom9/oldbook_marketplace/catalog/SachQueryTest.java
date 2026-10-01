package com.nhom9.oldbook_marketplace.catalog;

import com.nhom9.oldbook_marketplace.catalog.dto.SachSearchRequest;
import com.nhom9.oldbook_marketplace.catalog.entity.CuaHang;
import com.nhom9.oldbook_marketplace.catalog.entity.DanhMuc;
import com.nhom9.oldbook_marketplace.catalog.entity.Sach;
import com.nhom9.oldbook_marketplace.catalog.repository.SachRepository;
import com.nhom9.oldbook_marketplace.catalog.service.SachService;
import com.nhom9.oldbook_marketplace.common.exception.BusinessException;
import com.nhom9.oldbook_marketplace.identity.entity.NguoiDung;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
@Import(SachService.class)
@ActiveProfiles({"test", "catalog-test"})
class SachQueryTest {
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private SachService sachService;
    @Autowired
    private SachRepository sachRepository;

    private CuaHang approvedStore;
    private DanhMuc category;
    private int sequence;

    @BeforeEach
    void setUp() {
        approvedStore = store("DA_DUYET");
        category = category("Văn học");
    }

    @Test
    void listAndDetailRequireApprovedBookApprovedStoreAndSellingStatus() {
        var available = book(approvedStore, category, "Công khai", "DA_DUYET", "DANG_BAN", 2, "100", "80");
        var soldOut = book(approvedStore, category, "Hết hàng", "DA_DUYET", "DANG_BAN", 0, "100", "80");
        var hidden = List.of(
                book(approvedStore, category, "Chờ duyệt", "CHO_DUYET", "DANG_BAN", 2, "100", "80"),
                book(approvedStore, category, "Từ chối", "TU_CHOI", "DANG_BAN", 2, "100", "80"),
                book(approvedStore, category, "Ngừng bán", "DA_DUYET", "NGUNG_BAN", 2, "100", "80"),
                book(store("CHO_DUYET"), category, "Shop chờ duyệt", "DA_DUYET", "DANG_BAN", 2, "100", "80"),
                book(store("TU_CHOI"), category, "Shop từ chối", "DA_DUYET", "DANG_BAN", 2, "100", "80")
        );
        entityManager.flush();
        entityManager.clear();

        var page = sachService.search(new SachSearchRequest());

        assertThat(page.content()).extracting(response -> response.maSach())
                .containsExactlyInAnyOrder(available.getMaSach(), soldOut.getMaSach());
        assertThat(sachService.getDetail(soldOut.getMaSach()).soLuongTon()).isZero();
        assertThat(sachService.getDetail(available.getMaSach()).tenCuaHang()).isEqualTo(approvedStore.getTenCuaHang());
        hidden.forEach(sach -> assertThatThrownBy(() -> sachService.getDetail(sach.getMaSach()))
                .isInstanceOf(BusinessException.class));
        assertThatThrownBy(() -> sachService.getDetail(Integer.MAX_VALUE))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void filtersCombineWithInclusivePriceAndConditionBounds() {
        var matching = book(approvedStore, category, "Sách phù hợp", "DA_DUYET", "DANG_BAN", 3, "120", "75");
        book(approvedStore, category, "Giá thấp", "DA_DUYET", "DANG_BAN", 3, "119", "75");
        book(approvedStore, category, "Độ mới thấp", "DA_DUYET", "DANG_BAN", 3, "120", "74");
        book(approvedStore, category, "Hết hàng", "DA_DUYET", "DANG_BAN", 0, "120", "75");
        book(approvedStore, category("Khoa học"), "Sai danh mục", "DA_DUYET", "DANG_BAN", 3, "120", "75");
        book(store("DA_DUYET"), category, "Sai shop", "DA_DUYET", "DANG_BAN", 3, "120", "75");
        var request = new SachSearchRequest();
        request.setMaCH(approvedStore.getMaCH());
        request.setMaDM(category.getMaDM());
        request.setGiaTu(new BigDecimal("120"));
        request.setGiaDen(new BigDecimal("120"));
        request.setDoMoiTu(new BigDecimal("75"));
        request.setDoMoiDen(new BigDecimal("75"));
        request.setConHang(true);

        assertThat(sachService.search(request).content()).extracting(response -> response.maSach())
                .containsExactly(matching.getMaSach());
    }

    @Test
    void outOfStockFilterIncludesZeroAndLegacyNullStock() {
        var zero = book(approvedStore, category, "Không còn hàng", "DA_DUYET", "DANG_BAN", 0, "100", "80");
        var unknown = book(approvedStore, category, "Chưa nhập tồn kho", "DA_DUYET", "DANG_BAN", null, "100", "80");
        book(approvedStore, category, "Còn hàng", "DA_DUYET", "DANG_BAN", 1, "100", "80");
        var request = new SachSearchRequest();
        request.setConHang(false);

        assertThat(sachService.search(request).content()).extracting(response -> response.maSach())
                .containsExactlyInAnyOrder(zero.getMaSach(), unknown.getMaSach());
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "[", "\\"})
    void wildcardCharactersAreSearchedLiterally(String keyword) {
        var matching = book(approvedStore, category, "Sách " + keyword + " cụ thể", "DA_DUYET", "DANG_BAN", 1, "100", "80");
        book(approvedStore, category, "Sách thông thường", "DA_DUYET", "DANG_BAN", 1, "100", "80");
        var request = new SachSearchRequest();
        request.setTuKhoa(keyword);

        assertThat(sachService.search(request).content()).extracting(response -> response.maSach())
                .containsExactly(matching.getMaSach());
    }

    @ParameterizedTest
    @ValueSource(strings = {"alpha", "beta", "gamma"})
    void searchIsCaseInsensitiveAcrossTitleAuthorAndPublisher(String keyword) {
        var matching = book(approvedStore, category, "ALPHA", "DA_DUYET", "DANG_BAN", 1, "100", "80");
        matching.setTacGia("BETA");
        matching.setNhaXuatBan("GAMMA");
        book(approvedStore, category, "Không phù hợp", "DA_DUYET", "DANG_BAN", 1, "100", "80");
        var request = new SachSearchRequest();
        request.setTuKhoa("  " + keyword + "  ");

        assertThat(sachService.search(request).content()).extracting(response -> response.maSach())
                .containsExactly(matching.getMaSach());
    }

    @Test
    void paginationAndPriceSortHaveStableIdentifierTieBreaker() {
        var first = book(approvedStore, category, "Một", "DA_DUYET", "DANG_BAN", 1, "100", "80");
        var second = book(approvedStore, category, "Hai", "DA_DUYET", "DANG_BAN", 1, "100", "80");
        book(approvedStore, category, "Ba", "DA_DUYET", "DANG_BAN", 1, "200", "80");
        var request = new SachSearchRequest();
        request.setSort("gia-tang");
        request.setSize(1);

        var page = sachService.search(request);
        assertThat(page.content()).extracting(response -> response.maSach()).containsExactly(first.getMaSach());
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(1);
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(3);
        request.setPage(1);
        assertThat(sachService.search(request).content()).extracting(response -> response.maSach())
                .containsExactly(second.getMaSach());
    }

    @Test
    void categoriesAreReturnedAsDtosSortedByName() {
        category("A category");
        var result = sachService.getDanhMuc();
        assertThat(result).hasSize(2);
        assertThat(result.getFirst().tenDanhMuc()).isEqualTo("A category");
    }

    @Test
    void inventoryRepositoryCanLockTheBookWithinTransaction() {
        var sach = book(approvedStore, category, "Sách", "DA_DUYET", "DANG_BAN", 1, "100", "80");
        entityManager.flush();
        entityManager.clear();
        assertThat(sachRepository.findByIdForUpdate(sach.getMaSach())).isPresent();
    }

    private CuaHang store(String status) {
        int id = ++sequence;
        var owner = NguoiDung.builder().email("catalog-" + id + "@test.local")
                .matKhau("test-hash").hoTen("Chủ shop " + id).build();
        entityManager.persist(owner);
        var store = CuaHang.builder().chuShop(owner).tenCuaHang("Shop " + id)
                .trangThaiDuyet(status).build();
        entityManager.persist(store);
        return store;
    }

    private DanhMuc category(String name) {
        var category = DanhMuc.builder().tenDanhMuc(name).build();
        entityManager.persist(category);
        return category;
    }

    private Sach book(CuaHang store, DanhMuc category, String title, String approval, String selling,
                      Integer stock, String price, String condition) {
        var book = Sach.builder().cuaHang(store).danhMuc(category).tenSach(title)
                .giaBanCu(new BigDecimal(price)).doMoiPercent(new BigDecimal(condition))
                .soLuongTon(stock).trangThaiDuyet(approval).trangThaiBan(selling).build();
        entityManager.persist(book);
        return book;
    }
}
