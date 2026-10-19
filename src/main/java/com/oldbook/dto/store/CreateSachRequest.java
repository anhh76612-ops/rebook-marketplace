package com.oldbook.dto.store;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record CreateSachRequest(
        @NotNull(message = "Danh mục không được để trống") @Positive Integer maDM,
        @NotBlank(message = "Tên sách không được để trống") @Size(max = 255) String tenSach,
        @NotBlank(message = "Tác giả không được để trống") @Size(max = 255) String tacGia,
        @NotBlank(message = "Nhà xuất bản không được để trống") @Size(max = 255) String nhaXuatBan,
        @NotNull(message = "Năm xuất bản không được để trống") @Min(1) @Max(9999) Integer namXuatBan,
        @NotNull(message = "Giá bìa không được để trống") @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal giaBia,
        @NotNull(message = "Giá bán không được để trống") @DecimalMin("0.01")
        @Digits(integer = 13, fraction = 2) BigDecimal giaBanCu,
        @NotNull(message = "Độ mới không được để trống") @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal doMoiPercent,
        @Size(max = 255) String tinhTrangVatLy,
        @Size(max = 10000) String moTaChiTiet,
        @NotNull(message = "Số lượng tồn không được để trống")
        @Min(value = 1, message = "Tồn kho ban đầu phải lớn hơn 0") Integer soLuongTon,
        @Size(max = 500) String hinhAnhUrl,
        @Size(max = 10, message = "Mỗi sách chỉ được có tối đa 10 ảnh") List<@Size(max = 500) String> hinhAnhUrls
) {
}
