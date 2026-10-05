package com.oldbook.catalog;

import com.oldbook.dto.catalog.SachSearchRequest;
import com.oldbook.entity.catalog.CuaHang;
import com.oldbook.entity.catalog.DanhMuc;
import com.oldbook.entity.catalog.Sach;
import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.service.catalog.SachService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false, properties = "spring.config.import=")
@Import(SachService.class)
@ActiveProfiles({"test", "catalog-test"})
class SachPresetFilterQueryTest {
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private SachService sachService;

    private CuaHang approvedStore;
    private DanhMuc category;
    private int sequence;

    @BeforeEach
    void setUp() {
        approvedStore = store();
        category = category("Literature");
    }

    @ParameterizedTest
    @CsvSource({
            "NHU_MOI,new-minimum|new-maximum",
            "TOT,good-minimum|below-new",
            "KHA,zero|below-good"
    })
    void conditionPresetsRespectDecimalBoundaries(String preset, String expectedTitles) {
        conditionBoundaryBooks();
        var request = new SachSearchRequest();
        request.setTinhTrang(List.of(preset));

        assertThat(sachService.search(request).content()).extracting(response -> response.tenSach())
                .containsExactlyInAnyOrder(expectedTitles.split("\\|"));
    }

    @Test
    void nonadjacentConditionSelectionsAreCombinedWithOr() {
        conditionBoundaryBooks();
        var request = new SachSearchRequest();
        request.setTinhTrang(List.of("NHU_MOI", "KHA"));

        assertThat(sachService.search(request).content()).extracting(response -> response.tenSach())
                .containsExactlyInAnyOrder("zero", "below-good", "new-minimum", "new-maximum");
    }

    @ParameterizedTest
    @CsvSource({
            "DUOI_50000,zero|below-middle",
            "TU_50000_DEN_150000,middle-minimum|above-middle-minimum|below-middle-maximum|middle-maximum",
            "TREN_150000,above-middle-maximum"
    })
    void pricePresetsRespectDecimalBoundaries(String preset, String expectedTitles) {
        book("zero", "0", "80");
        book("below-middle", "49999.99", "80");
        book("middle-minimum", "50000", "80");
        book("above-middle-minimum", "50000.01", "80");
        book("below-middle-maximum", "149999.99", "80");
        book("middle-maximum", "150000", "80");
        book("above-middle-maximum", "150000.01", "80");
        book("unknown-price", null, "80");
        flushAndClear();
        var request = new SachSearchRequest();
        request.setKhoangGia(preset);

        assertThat(sachService.search(request).content()).extracting(response -> response.tenSach())
                .containsExactlyInAnyOrder(expectedTitles.split("\\|"));
    }

    @Test
    void conditionAndPricePresetsAreCombinedUsingAnd() {
        var selectedNew = book("selected new", "50000", "90");
        var selectedFair = book("selected fair", "150000", "69.99");
        book("other condition", "75000", "80");
        book("below selected price", "49999.99", "95");
        book("above selected price", "150000.01", "60");
        flushAndClear();
        var request = new SachSearchRequest();
        request.setTinhTrang(List.of("NHU_MOI", "KHA"));
        request.setKhoangGia("TU_50000_DEN_150000");

        assertThat(sachService.search(request).content()).extracting(response -> response.maSach())
                .containsExactlyInAnyOrder(selectedNew.getMaSach(), selectedFair.getMaSach());
    }

    @Test
    void presetsCombineWithExistingFiltersAndPublicVisibilityUsingAnd() {
        var matching = book("target matching", "60000", "95");
        book("target below numeric price", "59999.99", "95");
        book("target above numeric price", "60000.01", "95");
        book("target below numeric condition", "60000", "94.99");
        book("target above numeric condition", "60000", "95.01");
        book("target other condition", "60000", "80");
        book("different keyword", "60000", "95");
        book("target out of stock", "60000", "95").setSoLuongTon(0);
        book("target wrong category", "60000", "95").setDanhMuc(category("Other"));
        book("target wrong store", "60000", "95").setCuaHang(store());
        book("target pending book", "60000", "95").setTrangThaiDuyet("CHO_DUYET");
        book("target stopped selling", "60000", "95").setTrangThaiBan("NGUNG_BAN");
        var pendingStore = store();
        pendingStore.setTrangThaiDuyet("CHO_DUYET");
        book("target pending store", "60000", "95").setCuaHang(pendingStore);
        flushAndClear();
        var request = new SachSearchRequest();
        request.setTinhTrang(List.of("NHU_MOI", "KHA"));
        request.setKhoangGia("TU_50000_DEN_150000");
        request.setTuKhoa("target");
        request.setMaDM(category.getMaDM());
        request.setMaCH(approvedStore.getMaCH());
        request.setConHang(true);
        request.setGiaTu(new BigDecimal("60000"));
        request.setGiaDen(new BigDecimal("60000"));
        request.setDoMoiTu(new BigDecimal("95"));
        request.setDoMoiDen(new BigDecimal("95"));

        assertThat(sachService.search(request).content()).extracting(response -> response.maSach())
                .containsExactly(matching.getMaSach());
    }

    @Test
    void nullAndEmptyPresetsKeepSearchUnfiltered() {
        var first = book("new", "50000", "90");
        var second = book("good", "150000", "70");
        var third = book("unknown", null, null);
        flushAndClear();
        var request = new SachSearchRequest();
        assertThat(request.getTinhTrang()).isEmpty();

        assertThat(sachService.search(request).content()).extracting(response -> response.maSach())
                .containsExactlyInAnyOrder(first.getMaSach(), second.getMaSach(), third.getMaSach());
        request.setTinhTrang(null);
        assertThat(sachService.search(request).totalElements()).isEqualTo(3);
        request.setTinhTrang(List.of());
        request.setKhoangGia("");
        assertThat(sachService.search(request).totalElements()).isEqualTo(3);
        request.setKhoangGia("  ");
        assertThat(sachService.search(request).totalElements()).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "nhu_moi", ""})
    void invalidConditionPresetIsRejectedBeforeQuery(String value) {
        var request = new SachSearchRequest();
        request.setTinhTrang(List.of("NHU_MOI", value));

        assertThatThrownBy(() -> sachService.search(request)).isInstanceOf(BusinessException.class)
                .hasMessage("Tình trạng sách không hợp lệ");
    }

    @Test
    void nullConditionEntryIsRejectedAsBusinessError() {
        var request = new SachSearchRequest();
        request.setTinhTrang(Arrays.asList("NHU_MOI", null));

        assertThatThrownBy(() -> sachService.search(request)).isInstanceOf(BusinessException.class)
                .hasMessage("Tình trạng sách không hợp lệ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "duoi_50000", "DUOI_50000,TREN_150000"})
    void invalidPricePresetIsRejectedBeforeQuery(String value) {
        var request = new SachSearchRequest();
        request.setKhoangGia(value);

        assertThatThrownBy(() -> sachService.search(request)).isInstanceOf(BusinessException.class)
                .hasMessage("Khoảng giá không hợp lệ");
    }

    private void conditionBoundaryBooks() {
        book("negative", "75000", "-0.01");
        book("zero", "75000", "0");
        book("below-good", "75000", "69.99");
        book("good-minimum", "75000", "70");
        book("below-new", "75000", "89.99");
        book("new-minimum", "75000", "90");
        book("new-maximum", "75000", "100");
        book("above-new", "75000", "100.01");
        book("unknown-condition", "75000", null);
        flushAndClear();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private CuaHang store() {
        int id = ++sequence;
        var owner = NguoiDung.builder().email("preset-" + id + "@test.local")
                .matKhau("test-hash").hoTen("Owner " + id).build();
        entityManager.persist(owner);
        var store = CuaHang.builder().chuShop(owner).tenCuaHang("Store " + id)
                .trangThaiDuyet("DA_DUYET").build();
        entityManager.persist(store);
        return store;
    }

    private DanhMuc category(String name) {
        var category = DanhMuc.builder().tenDanhMuc(name).build();
        entityManager.persist(category);
        return category;
    }

    private Sach book(String title, String price, String condition) {
        var book = Sach.builder().cuaHang(approvedStore).danhMuc(category).tenSach(title)
                .giaBanCu(price == null ? null : new BigDecimal(price))
                .doMoiPercent(condition == null ? null : new BigDecimal(condition))
                .soLuongTon(1).trangThaiDuyet("DA_DUYET").trangThaiBan("DANG_BAN").build();
        entityManager.persist(book);
        return book;
    }
}
