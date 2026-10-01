package com.oldbook.repository.system;

import org.springframework.data.jpa.repository.JpaRepository;

import com.oldbook.entity.system.NhatKyHeThong;

public interface NhatKyHeThongRepository
        extends JpaRepository<NhatKyHeThong, Integer> {
}