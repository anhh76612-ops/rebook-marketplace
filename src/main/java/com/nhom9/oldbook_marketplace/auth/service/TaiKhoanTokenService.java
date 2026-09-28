package com.nhom9.oldbook_marketplace.auth.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TaiKhoanTokenService {

    /*
     * maTK -> thời điểm mà tất cả token trước thời điểm này
     * trở nên không hợp lệ.
     */
    private final Map<Integer, Instant> invalidBefore =
            new ConcurrentHashMap<>();

    /**
     * Vô hiệu toàn bộ token hiện tại của tài khoản.
     */
    public void invalidateAllTokens(Integer maTK) {
        invalidBefore.put(maTK, Instant.now());
    }

    /**
     * Kiểm tra token có được tạo trước thời điểm vô hiệu hóa hay không.
     */
    public boolean isTokenInvalid(
            Integer maTK,
            Instant issuedAt
    ) {

        Instant invalidTime = invalidBefore.get(maTK);

        if (invalidTime == null) {
            return false;
        }

        return !issuedAt.isAfter(invalidTime);
    }
}