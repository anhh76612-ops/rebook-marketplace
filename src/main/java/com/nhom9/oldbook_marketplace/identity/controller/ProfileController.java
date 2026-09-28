// src/main/java/com/nhom9/oldbook_marketplace/identity/controller/ProfileController.java

package com.nhom9.oldbook_marketplace.identity.controller;

import com.nhom9.oldbook_marketplace.common.dto.ApiResponse;
import com.nhom9.oldbook_marketplace.identity.dto.ProfileResponse;
import com.nhom9.oldbook_marketplace.identity.dto.UpdateProfileRequest;
import com.nhom9.oldbook_marketplace.identity.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile() {

        Integer maND = getMaND();

        ProfileResponse response =
                profileService.getProfile(maND);

        return ResponseEntity.ok(
                ApiResponse.success(response)
        );
    }

    @PutMapping
    public ResponseEntity<ApiResponse<ProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request
    ) {

        Integer maND = getMaND();

        ProfileResponse response =
                profileService.updateProfile(
                        maND,
                        request
                );

        return ResponseEntity.ok(
                ApiResponse.success(response)
        );
    }

    private Integer getMaND() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        return (Integer) authentication.getPrincipal();
    }
}