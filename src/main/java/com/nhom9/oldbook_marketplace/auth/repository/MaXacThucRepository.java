package com.nhom9.oldbook_marketplace.auth.repository;

import com.nhom9.oldbook_marketplace.auth.entity.MaXacThuc;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MaXacThucRepository
        extends JpaRepository<MaXacThuc, Integer> {

    Optional<MaXacThuc> findTopByNguoiDung_MaNDAndLoaiXacThucAndDaSuDungFalseOrderByNgayTaoDesc(
            Integer maND,
            String loaiXacThuc
    );
}