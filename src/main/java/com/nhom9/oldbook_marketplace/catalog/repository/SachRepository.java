package com.nhom9.oldbook_marketplace.catalog.repository;

import com.nhom9.oldbook_marketplace.catalog.entity.Sach;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SachRepository extends JpaRepository<Sach, Integer> {
}