package com.oldbook.security.store;

import com.oldbook.constant.auth.VaiTro;
import com.oldbook.filter.auth.JwtAuthenticationFilter.AuthenticatedUserDetails;
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

    // Dùng cho việc khách hàng tự đăng ký gian hàng.
    public Integer requireCustomer() {
        AuthenticatedUserDetails actor = currentActor();
        requireRole(VaiTro.KHACH_HANG);
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
