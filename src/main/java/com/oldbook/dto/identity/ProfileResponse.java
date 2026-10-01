// src/main/java/com/nhom9/oldbook_marketplace/identity/dto/ProfileResponse.java

package com.oldbook.dto.identity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ProfileResponse {

    private Integer maND;

    private String email;

    private String hoTen;

    private String soDienThoai;

    private String avatarUrl;
}