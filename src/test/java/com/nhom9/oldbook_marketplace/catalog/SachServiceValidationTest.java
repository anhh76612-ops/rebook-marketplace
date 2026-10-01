package com.nhom9.oldbook_marketplace.catalog;

import com.nhom9.oldbook_marketplace.catalog.dto.SachSearchRequest;
import com.nhom9.oldbook_marketplace.catalog.repository.DanhMucRepository;
import com.nhom9.oldbook_marketplace.catalog.repository.SachRepository;
import com.nhom9.oldbook_marketplace.catalog.service.SachService;
import com.nhom9.oldbook_marketplace.common.exception.BusinessException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class SachServiceValidationTest {
    @ParameterizedTest
    @MethodSource("invalidFilters")
    void invalidSearchIsRejectedBeforeQuerying(Consumer<SachSearchRequest> invalidFilter) {
        var repository = mock(SachRepository.class);
        var categories = mock(DanhMucRepository.class);
        var service = new SachService(repository, categories);
        var request = new SachSearchRequest();
        invalidFilter.accept(request);

        assertThatThrownBy(() -> service.search(request)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(repository, categories);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @NullSource
    void invalidBookIdentifierIsRejectedBeforeQuerying(Integer id) {
        var repository = mock(SachRepository.class);
        var categories = mock(DanhMucRepository.class);

        assertThatThrownBy(() -> new SachService(repository, categories).getDetail(id))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(repository, categories);
    }

    private static Stream<Consumer<SachSearchRequest>> invalidFilters() {
        return Stream.of(
                request -> request.setPage(-1),
                request -> request.setSize(0),
                request -> request.setSize(101),
                request -> { request.setPage(Integer.MAX_VALUE); request.setSize(100); },
                request -> request.setMaDM(0),
                request -> request.setMaCH(-1),
                request -> request.setTuKhoa("x".repeat(256)),
                request -> request.setGiaTu(new BigDecimal("-0.01")),
                request -> request.setGiaDen(new BigDecimal("-1")),
                request -> { request.setGiaTu(new BigDecimal("2")); request.setGiaDen(BigDecimal.ONE); },
                request -> request.setDoMoiTu(new BigDecimal("-1")),
                request -> request.setDoMoiDen(new BigDecimal("101")),
                request -> { request.setDoMoiTu(new BigDecimal("80")); request.setDoMoiDen(new BigDecimal("70")); },
                request -> request.setSort("chuShop.matKhau"),
                request -> request.setSort(null)
        );
    }
}
