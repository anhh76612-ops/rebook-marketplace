package com.oldbook.service.notification;

import com.oldbook.constant.auth.TrangThaiTaiKhoan;
import com.oldbook.constant.auth.VaiTro;
import com.oldbook.constant.notification.MucDoThongBao;
import com.oldbook.dto.catalog.PageResponse;
import com.oldbook.dto.notification.ThongBaoResponse;
import com.oldbook.entity.identity.TaiKhoan;
import com.oldbook.entity.notification.ThongBao;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.repository.notification.ThongBaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lưu và đọc thông báo. Lớp này không biết sự kiện nào thì cần báo cho ai:
 * quy tắc đó thuộc về từng khu vực (ví dụ AdminThongBaoService cho quản trị viên).
 */
@Service
@RequiredArgsConstructor
public class ThongBaoService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int TIEU_DE_MAX = 255;
    private static final int NOI_DUNG_MAX = 1000;

    private final ThongBaoRepository thongBaoRepository;

    public record NoiDungThongBao(
            String loai,
            MucDoThongBao mucDo,
            String tieuDe,
            String noiDung,
            String duongDan
    ) {
    }

    /**
     * Gửi cùng một thông báo cho mọi tài khoản đang hoạt động của một vai trò.
     * Nếu người thực hiện cũng thuộc vai trò đó, họ vẫn có một bản để tra lại,
     * nhưng bản đó được lưu ở trạng thái đã đọc: không ai cần được báo về việc chính mình vừa làm.
     */
    @Transactional
    public void guiChoVaiTro(VaiTro vaiTroNhan, Integer maTKThucHien, NoiDungThongBao noiDung) {

        List<TaiKhoan> nguoiNhan = thongBaoRepository.findAccountsByRoleAndStatus(
                vaiTroNhan.name(),
                TrangThaiTaiKhoan.HOAT_DONG.name()
        );

        LocalDateTime now = LocalDateTime.now();

        List<ThongBao> thongBaos = nguoiNhan.stream()
                .map(taiKhoan -> {
                    boolean laNguoiThucHien = taiKhoan.getMaTK().equals(maTKThucHien);
                    return ThongBao.builder()
                            .taiKhoanNhan(taiKhoan)
                            .loai(noiDung.loai())
                            .mucDo(noiDung.mucDo().name())
                            .tieuDe(cut(noiDung.tieuDe(), TIEU_DE_MAX))
                            .noiDung(cut(noiDung.noiDung(), NOI_DUNG_MAX))
                            .duongDan(noiDung.duongDan())
                            .daDoc(laNguoiThucHien)
                            .ngayDoc(laNguoiThucHien ? now : null)
                            .ngayTao(now)
                            .build();
                })
                .toList();

        thongBaoRepository.saveAll(thongBaos);
    }

    @Transactional(readOnly = true)
    public PageResponse<ThongBaoResponse> danhSach(Integer maTK, int page, int size) {

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        return PageResponse.of(
                thongBaoRepository
                        .findByTaiKhoanNhan_MaTKOrderByNgayTaoDescMaThongBaoDesc(
                                maTK,
                                PageRequest.of(safePage, safeSize)
                        )
                        .map(ThongBaoResponse::from)
        );
    }

    @Transactional(readOnly = true)
    public long demChuaDoc(Integer maTK) {
        return thongBaoRepository.countByTaiKhoanNhan_MaTKAndDaDocFalse(maTK);
    }

    @Transactional
    public void danhDauDaDoc(Integer maTK, Integer maThongBao) {

        ThongBao thongBao = thongBaoRepository
                .findByMaThongBaoAndTaiKhoanNhan_MaTK(maThongBao, maTK)
                .orElseThrow(() -> new BusinessException("Thông báo không tồn tại"));

        if (!thongBao.isDaDoc()) {
            thongBao.setDaDoc(true);
            thongBao.setNgayDoc(LocalDateTime.now());
            thongBaoRepository.save(thongBao);
        }
    }

    @Transactional
    public void danhDauTatCaDaDoc(Integer maTK) {
        thongBaoRepository.markAllRead(maTK, LocalDateTime.now());
    }

    private static String cut(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + "…";
    }
}
