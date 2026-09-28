package com.nhom9.oldbook_marketplace.identity.repository;

import com.nhom9.oldbook_marketplace.identity.entity.TaiKhoan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaiKhoanRepository extends JpaRepository<TaiKhoan, Integer> {

    // Lấy tất cả tài khoản thuộc một người dùng
    List<TaiKhoan> findByNguoiDung_MaND(Integer maND);

    // Tìm tài khoản theo người dùng và vai trò
    Optional<TaiKhoan> findByNguoiDung_MaNDAndVaiTro(
            Integer maND,
            String vaiTro
    );

    // Kiểm tra người dùng đã có tài khoản với vai trò này chưa
    boolean existsByNguoiDung_MaNDAndVaiTro(
            Integer maND,
            String vaiTro
    );

    // Lấy các tài khoản đang hoạt động của người dùng
    List<TaiKhoan> findByNguoiDung_MaNDAndTrangThai(
            Integer maND,
            String trangThai
    );
}