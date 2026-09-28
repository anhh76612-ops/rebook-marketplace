package com.nhom9.oldbook_marketplace.identity.repository;

import com.nhom9.oldbook_marketplace.identity.entity.TaiKhoan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaiKhoanRepository extends JpaRepository<TaiKhoan, Integer> {

    List<TaiKhoan> findByNguoiDung_MaND(Integer maND);

    Optional<TaiKhoan> findByNguoiDung_MaNDAndVaiTro(
            Integer maND,
            String vaiTro
    );

    boolean existsByNguoiDung_MaNDAndVaiTro(
            Integer maND,
            String vaiTro
    );

    boolean existsByNguoiDung_MaNDAndVaiTroAndMaTKNot(
            Integer maND,
            String vaiTro,
            Integer maTK
    );

    List<TaiKhoan> findByNguoiDung_MaNDAndTrangThai(
            Integer maND,
            String trangThai
    );
}