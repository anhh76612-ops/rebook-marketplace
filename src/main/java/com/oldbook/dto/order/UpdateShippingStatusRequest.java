package com.oldbook.dto.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateShippingStatusRequest {

    @NotBlank(message = "Trạng thái giao hàng không được để trống")
    private String trangThaiVanChuyen;

    @Size(max = 1000, message = "Ghi chú giao hàng không được vượt quá 1000 ký tự")
    private String ghiChuGiaoHang;
}
