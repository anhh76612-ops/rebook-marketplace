package com.oldbook.store.dto;

import com.oldbook.dto.store.*;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class StoreRequestValidationTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();

    @AfterAll
    static void closeValidator() {
        FACTORY.close();
    }

    @Test
    void stockRequestRejectsNullAndNegativeValues() {
        var validator = FACTORY.getValidator();
        assertThat(validator.validate(new UpdateTonKhoRequest(null))).isNotEmpty();
        assertThat(validator.validate(new UpdateTonKhoRequest(-1))).isNotEmpty();
        assertThat(validator.validate(new UpdateTonKhoRequest(0))).isEmpty();
    }

    @Test
    void saleRequestOnlyAcceptsSaleStates() {
        var validator = FACTORY.getValidator();
        assertThat(validator.validate(new UpdateTrangThaiBanRequest("DA_DUYET"))).isNotEmpty();
        assertThat(validator.validate(new UpdateTrangThaiBanRequest("NGUNG_BAN"))).isEmpty();
    }

    @Test
    void bookRequestRejectsValuesThatCannotFitDatabaseColumns() {
        var request = new CreateSachRequest(2, "Sách", null, null, 2020,
                null, new BigDecimal("12345678901234.00"), new BigDecimal("101.00"), null, null, 1, null, null);

        var violations = FACTORY.getValidator().validate(request);
        assertThat(violations).extracting(v -> v.getPropertyPath().toString())
                .contains("giaBanCu", "doMoiPercent");
    }

    @Test
    void storeRequestRequiresUsableContactInformation() {
        var request = new CreateCuaHangRequest(" ", null, " ", " ");

        assertThat(FACTORY.getValidator().validate(request)).hasSize(3);
    }
}
