package com.nhom9.oldbook_marketplace.store.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateTonKhoRequest(
        @NotNull(message = "Số lượng tồn không được để trống")
        @Min(value = 0, message = "Số lượng tồn không được âm") Integer soLuongTon
) {
}
