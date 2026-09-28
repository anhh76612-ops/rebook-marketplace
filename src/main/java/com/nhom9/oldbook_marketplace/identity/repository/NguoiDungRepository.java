package com.nhom9.oldbook_marketplace.identity.repository;

import com.nhom9.oldbook_marketplace.identity.entity.NguoiDung;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NguoiDungRepository extends JpaRepository<NguoiDung, Integer> {

    // Tìm người dùng theo email đăng nhập
    Optional<NguoiDung> findByEmail(String email);

    // Kiểm tra email đã tồn tại chưa
    boolean existsByEmail(String email);

    // Kiểm tra số điện thoại đã tồn tại chưa
    boolean existsBySoDienThoai(String soDienThoai);
}