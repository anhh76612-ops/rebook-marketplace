package com.oldbook.service.catalog;

import com.oldbook.constant.catalog.TrangThaiBan;
import com.oldbook.constant.catalog.TrangThaiDuyet;
import com.oldbook.dto.catalog.SachResponse;
import com.oldbook.entity.catalog.UserBookKey;
import com.oldbook.entity.catalog.YeuThich;
import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.repository.catalog.SachRepository;
import com.oldbook.repository.catalog.SachSpecifications;
import com.oldbook.repository.catalog.YeuThichRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class YeuThichService {

    private final YeuThichRepository yeuThichRepository;
    private final SachRepository sachRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<SachResponse> getWishlist(Integer maND) {
        return yeuThichRepository.findCongKhaiByMaND(maND,
                        TrangThaiDuyet.DA_DUYET.name(), TrangThaiBan.DANG_BAN.name())
                .stream().map(item -> SachMapper.toPublicResponse(item.getSach())).toList();
    }

    @Transactional
    public SachResponse addBook(Integer maND, Integer maSach) {
        validateBookId(maSach);
        NguoiDung nguoiDung = lockOwner(maND);
        var sach = sachRepository.findOne(SachSpecifications.publicVisible()
                        .and((root, query, cb) -> cb.equal(root.get("maSach"), maSach)))
                .orElseThrow(() -> new BusinessException("Sách không tồn tại hoặc chưa được công khai"));
        var id = new UserBookKey(maND, maSach);
        if (!yeuThichRepository.existsById(id)) {
            yeuThichRepository.save(YeuThich.builder().id(id).nguoiDung(nguoiDung).sach(sach).build());
        }
        return SachMapper.toPublicResponse(sach);
    }

    @Transactional
    public void removeBook(Integer maND, Integer maSach) {
        validateBookId(maSach);
        lockOwner(maND);
        yeuThichRepository.findById(new UserBookKey(maND, maSach)).ifPresent(yeuThichRepository::delete);
    }

    private NguoiDung lockOwner(Integer maND) {
        // Serialize changes for this user so repeated or concurrent additions stay idempotent.
        var nguoiDung = entityManager.find(NguoiDung.class, maND, LockModeType.PESSIMISTIC_WRITE);
        if (nguoiDung == null) {
            throw new BusinessException("Người dùng không tồn tại");
        }
        return nguoiDung;
    }

    private void validateBookId(Integer maSach) {
        if (maSach == null || maSach < 1) {
            throw new BusinessException("Mã sách phải là số nguyên dương");
        }
    }
}
