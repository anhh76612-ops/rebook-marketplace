package com.nhom9.oldbook_marketplace.store.security;

import com.nhom9.oldbook_marketplace.auth.constant.VaiTro;
import com.nhom9.oldbook_marketplace.auth.filter.JwtAuthenticationFilter.AuthenticatedUserDetails;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class StoreAccess {

    public Integer requireOwner() {// yêu cầu là chủ của hàng
        AuthenticatedUserDetails actor = currentActor();
        requireRole(VaiTro.CHU_CUA_HANG);
        return actor.maND();
    }

    public AuthenticatedUserDetails requireModerator() {
        AuthenticatedUserDetails actor = currentActor();
        requireRole(VaiTro.QUAN_LY, VaiTro.QUAN_TRI_VIEN);
        return actor;
    }

    private AuthenticatedUserDetails currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Integer maND)
                || !(authentication.getDetails() instanceof AuthenticatedUserDetails actor)
                || maND <= 0 || actor.maTK() == null || actor.maTK() <= 0
                || !maND.equals(actor.maND())) {
            throw new AuthenticationCredentialsNotFoundException("Vui lòng đăng nhập bằng tài khoản hợp lệ");
        }
        return actor;
    }

    private void requireRole(VaiTro... roles) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        for (VaiTro role : roles) {
            if (authentication.getAuthorities().stream()
                    .anyMatch(authority -> role.getAuthority().equals(authority.getAuthority()))) {
                return;
            }
        }
        throw new AccessDeniedException("Bạn không có quyền thực hiện thao tác này");
    }
}
