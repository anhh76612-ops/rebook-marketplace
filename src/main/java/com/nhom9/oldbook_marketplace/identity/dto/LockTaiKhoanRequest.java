package com.nhom9.oldbook_marketplace.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LockTaiKhoanRequest {

    @NotBlank(message = "Lý do khóa tài khoản không được để trống")
    private String lyDo;
}