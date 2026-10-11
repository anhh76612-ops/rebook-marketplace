package com.oldbook.service.catalog;

import com.oldbook.constant.catalog.TrangThaiBan;
import com.oldbook.constant.catalog.TrangThaiDuyet;
import com.oldbook.dto.catalog.SachResponse;
import com.oldbook.entity.catalog.SachDaXem;
import com.oldbook.entity.catalog.UserBookKey;
import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.repository.catalog.SachDaXemRepository;
import com.oldbook.repository.catalog.SachRepository;
import com.oldbook.repository.catalog.SachSpecifications;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SachDaXemService {

    private static final int RECENT_BOOK_LIMIT = 20;

    private final SachDaXemRepository sachDaXemRepository;
    private final SachRepository sachRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<SachResponse> getRecentlyViewed(Integer maND) {
        return sachDaXemRepository.findCongKhaiByMaND(maND,
                        TrangThaiDuyet.DA_DUYET.name(), TrangThaiBan.DANG_BAN.name(),
                        PageRequest.of(0, RECENT_BOOK_LIMIT))
                .stream().map(item -> SachMapper.toPublicResponse(item.getSach())).toList();
    }

    @Transactional
    public SachResponse recordView(Integer maND, Integer maSach) {
        if (maSach == null || maSach < 1) {
            throw new BusinessException("Mã sách phải là số nguyên dương");
        }
        // Serialize views for this user so concurrent first views produce one history entry.
        var nguoiDung = entityManager.find(NguoiDung.class, maND, LockModeType.PESSIMISTIC_WRITE);
        if (nguoiDung == null) {
            throw new BusinessException("Người dùng không tồn tại");
        }
        var sach = sachRepository.findOne(SachSpecifications.publicVisible()
                        .and((root, query, cb) -> cb.equal(root.get("maSach"), maSach)))
                .orElseThrow(() -> new BusinessException("Sách không tồn tại hoặc chưa được công khai"));
        var id = new UserBookKey(maND, maSach);
        var viewed = sachDaXemRepository.findById(id).orElseGet(() ->
                SachDaXem.builder().id(id).nguoiDung(nguoiDung).sach(sach).build());
        // Explicitly dirty an existing entity; lifecycle callbacks alone do not trigger an update.
        viewed.setLanXemGanNhat(LocalDateTime.now());
        sachDaXemRepository.save(viewed);
        return SachMapper.toPublicResponse(sach);
    }
}
