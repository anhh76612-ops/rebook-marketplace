package com.oldbook.repository.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.oldbook.entity.catalog.Sach;

@Repository
public interface SachRepository extends JpaRepository<Sach, Integer> {
}