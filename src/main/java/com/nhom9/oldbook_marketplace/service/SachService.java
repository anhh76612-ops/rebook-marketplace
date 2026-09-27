package com.nhom9.oldbook_marketplace.service;

import com.nhom9.oldbook_marketplace.entity.catalog.Sach;
import com.nhom9.oldbook_marketplace.repository.SachRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SachService {

    private final SachRepository sachRepository;

    public SachService(SachRepository sachRepository) {
        this.sachRepository = sachRepository;
    }

    public List<Sach> getAll() {
        return sachRepository.findAll();
    }
}