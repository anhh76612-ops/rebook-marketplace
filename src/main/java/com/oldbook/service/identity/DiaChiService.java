package com.oldbook.service.identity;

import com.oldbook.dto.identity.DiaChiRequest;
import com.oldbook.dto.identity.DiaChiResponse;
import com.oldbook.entity.identity.DiaChi;
import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.repository.identity.DiaChiRepository;
import com.oldbook.repository.identity.NguoiDungRepository;
import com.oldbook.repository.order.DonHangRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DiaChiService {

    private final DiaChiRepository diaChiRepository;
    private final NguoiDungRepository nguoiDungRepository;
    private final DonHangRepository donHangRepository;

    @Transactional(readOnly = true)
    public List<DiaChiResponse> getAll(Integer maND) {
        return diaChiRepository
                .findAllByNguoiDung_MaNDOrderByLaMacDinhDescMaDiaChiDesc(maND)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DiaChiResponse create(Integer maND, DiaChiRequest request) {

        NguoiDung nguoiDung = getNguoiDung(maND);

        List<DiaChi> current =
                diaChiRepository.findAllByNguoiDung_MaNDOrderByLaMacDinhDescMaDiaChiDesc(maND);

        boolean makeDefault =
                Boolean.TRUE.equals(request.getLaMacDinh()) || current.isEmpty();

        if (makeDefault) {
            clearDefault(current);
        }

        DiaChi diaChi = DiaChi.builder()
                .nguoiDung(nguoiDung)
                .tenNguoiNhan(clean(request.getTenNguoiNhan()))
                .soDienThoai(clean(request.getSoDienThoai()))
                .tinhThanh(clean(request.getTinhThanh()))
                .quanHuyen(clean(request.getQuanHuyen()))
                .phuongXa(clean(request.getPhuongXa()))
                .diaChiChiTiet(clean(request.getDiaChiChiTiet()))
                .laMacDinh(makeDefault)
                .build();

        return toResponse(diaChiRepository.save(diaChi));
    }

    @Transactional
    public DiaChiResponse update(Integer maND, Integer maDiaChi, DiaChiRequest request) {

        DiaChi diaChi = getAddress(maND, maDiaChi);

        boolean wasDefault = Boolean.TRUE.equals(diaChi.getLaMacDinh());
        boolean requestedDefault = Boolean.TRUE.equals(request.getLaMacDinh());

        if (requestedDefault) {
            clearDefault(
                    diaChiRepository
                            .findAllByNguoiDung_MaNDOrderByLaMacDinhDescMaDiaChiDesc(maND)
            );
            diaChi.setLaMacDinh(true);

        } else if (!wasDefault) {
            diaChi.setLaMacDinh(false);

        } else {
            diaChi.setLaMacDinh(true);
        }

        diaChi.setTenNguoiNhan(clean(request.getTenNguoiNhan()));
        diaChi.setSoDienThoai(clean(request.getSoDienThoai()));
        diaChi.setTinhThanh(clean(request.getTinhThanh()));
        diaChi.setQuanHuyen(clean(request.getQuanHuyen()));
        diaChi.setPhuongXa(clean(request.getPhuongXa()));
        diaChi.setDiaChiChiTiet(clean(request.getDiaChiChiTiet()));

        return toResponse(diaChiRepository.save(diaChi));
    }

    @Transactional
    public void delete(Integer maND, Integer maDiaChi) {

        DiaChi diaChi = getAddress(maND, maDiaChi);
        boolean wasDefault = Boolean.TRUE.equals(diaChi.getLaMacDinh());

        // Đơn cũ giữ snapshot địa chỉ nên chỉ cần gỡ khóa ngoại, tránh lỗi ràng buộc khi xóa
        donHangRepository.clearDiaChiGiao(maDiaChi);
        diaChiRepository.delete(diaChi);

        if (wasDefault) {
            List<DiaChi> remaining =
                    diaChiRepository
                            .findAllByNguoiDung_MaNDOrderByLaMacDinhDescMaDiaChiDesc(maND);

            if (!remaining.isEmpty()) {
                DiaChi nextDefault = remaining.get(0);
                nextDefault.setLaMacDinh(true);
                diaChiRepository.save(nextDefault);
            }
        }
    }

    @Transactional
    public DiaChiResponse setDefault(Integer maND, Integer maDiaChi) {

        DiaChi target = getAddress(maND, maDiaChi);

        List<DiaChi> all =
                diaChiRepository
                        .findAllByNguoiDung_MaNDOrderByLaMacDinhDescMaDiaChiDesc(maND);

        clearDefault(all);
        target.setLaMacDinh(true);

        return toResponse(diaChiRepository.save(target));
    }

    private NguoiDung getNguoiDung(Integer maND) {
        return nguoiDungRepository.findById(maND)
                .orElseThrow(() -> new BusinessException("Người dùng không tồn tại"));
    }

    private DiaChi getAddress(Integer maND, Integer maDiaChi) {
        return diaChiRepository.findByMaDiaChiAndNguoiDung_MaND(maDiaChi, maND)
                .orElseThrow(() -> new BusinessException("Địa chỉ không tồn tại"));
    }

    private void clearDefault(List<DiaChi> addresses) {
        for (DiaChi address : addresses) {
            address.setLaMacDinh(false);
        }

        if (!addresses.isEmpty()) {
            diaChiRepository.saveAll(addresses);
        }
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private DiaChiResponse toResponse(DiaChi diaChi) {
        return DiaChiResponse.builder()
                .maDiaChi(diaChi.getMaDiaChi())
                .tenNguoiNhan(diaChi.getTenNguoiNhan())
                .soDienThoai(diaChi.getSoDienThoai())
                .tinhThanh(diaChi.getTinhThanh())
                .quanHuyen(diaChi.getQuanHuyen())
                .phuongXa(diaChi.getPhuongXa())
                .diaChiChiTiet(diaChi.getDiaChiChiTiet())
                .laMacDinh(diaChi.getLaMacDinh())
                .build();
    }
}
