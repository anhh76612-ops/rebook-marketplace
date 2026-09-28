package com.nhom9.oldbook_marketplace.auth.filter;

import com.nhom9.oldbook_marketplace.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

import com.nhom9.oldbook_marketplace.auth.constant.VaiTro;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Lấy Authorization header
        String authorizationHeader =
                request.getHeader("Authorization");

        // Không có token → cho request đi tiếp
        // SecurityConfig sẽ quyết định API đó public hay cần đăng nhập
        if (authorizationHeader == null
                || !authorizationHeader.startsWith("Bearer ")) {

            filterChain.doFilter(request, response);
            return;
        }

        // 2. Lấy JWT sau "Bearer "
        String token = authorizationHeader.substring(7);

        try {

            // 3. Kiểm tra token
            if (!jwtService.isTokenValid(token)) {
                filterChain.doFilter(request, response);
                return;
            }

            // 4. Lấy thông tin từ JWT
            Claims claims = jwtService.extractClaims(token);

            Integer maND = claims.get("maND", Integer.class);
            Integer maTK = claims.get("maTK", Integer.class);
            String vaiTro = claims.get("vaiTro", String.class);

            // JWT chính thức phải có các thông tin này
            if (maND == null || maTK == null || vaiTro == null) {
                filterChain.doFilter(request, response);
                return;
            }

            VaiTro role = VaiTro.valueOf(vaiTro);

            SimpleGrantedAuthority authority =
                    new SimpleGrantedAuthority(
                            role.getAuthority()
                    );

            // 6. Tạo Authentication
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            maND,
                            null,
                            List.of(authority)
                    );

            // 7. Lưu thêm maTK và vaiTro để các tầng sau có thể lấy
            authentication.setDetails(
                    new AuthenticatedUserDetails(
                            maND,
                            maTK,
                            vaiTro
                    )
            );

            // 8. Đưa Authentication vào SecurityContext
            SecurityContextHolder
                    .getContext()
                    .setAuthentication(authentication);

        } catch (Exception e) {

            // Token lỗi → không xác thực người dùng
            SecurityContextHolder.clearContext();
        }

        // 9. Cho request đi tiếp
        filterChain.doFilter(request, response);
    }

    public record AuthenticatedUserDetails(
            Integer maND,
            Integer maTK,
            String vaiTro
    ) {
    }
}