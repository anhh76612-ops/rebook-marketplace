package com.nhom9.oldbook_marketplace.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SelectRoleRequest {

    @NotNull(message = "Mã tài khoản không được để trống")
    private Integer maTK;
}