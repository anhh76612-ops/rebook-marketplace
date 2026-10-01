package com.oldbook.dto.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderRequest {

    @NotNull(message = "Mã địa chỉ giao hàng không được để trống")
    private Integer maDiaChi;

    @NotBlank(message = "Phương thức thanh toán không được để trống")
    private String phuongThucTT;

    private String ghiChu;
}
