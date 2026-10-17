package com.oldbook.service.notification;

import com.oldbook.repository.notification.ThongBaoRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Đẩy thông báo mới qua WebSocket cho người nhận đang mở trang.
 * Chỉ chạy sau khi giao dịch lưu thông báo đã commit, nên không bao giờ báo về một thông báo bị rollback.
 */
@Component
@RequiredArgsConstructor
public class ThongBaoPusher {

    public static final String LOAI_TIN_THAY_DOI = "DU_LIEU_THAY_DOI";

    private static final Logger log = LoggerFactory.getLogger(ThongBaoPusher.class);

    private final ThongBaoSocketHandler socketHandler;
    private final ThongBaoRepository thongBaoRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void day(ThongBaoDaGuiEvent event) {
        for (ThongBaoDaGuiEvent.BanGhi banGhi : event.thongBaos()) {
            Integer maTK = banGhi.maTKNhan();

            // Người nhận không mở trang nào thì khỏi truy vấn: họ sẽ thấy thông báo ở lần tải trang sau.
            if (!socketHandler.dangKetNoi(maTK)) {
                continue;
            }

            try {
                long chuaDoc = thongBaoRepository.countByTaiKhoanNhan_MaTKAndDaDocFalse(maTK);
                socketHandler.gui(maTK, objectMapper.writeValueAsString(Map.of(
                        "type", "THONG_BAO_MOI",
                        "thongBao", banGhi.thongBao(),
                        "chuaDoc", chuaDoc
                )));
                // Tin riêng cho các trang đang hiển thị dữ liệu liên quan: chúng tự tải lại theo loại thông báo.
                socketHandler.gui(maTK, objectMapper.writeValueAsString(Map.of(
                        "type", LOAI_TIN_THAY_DOI,
                        "loai", String.valueOf(banGhi.thongBao().loai())
                )));
            } catch (RuntimeException ex) {
                // Đẩy tức thì chỉ là tiện ích thêm; lỗi ở đây không được làm hỏng thao tác chính.
                log.warn("Không đẩy được thông báo #{}: {}", banGhi.thongBao().maThongBao(), ex.getMessage());
            }
        }
    }
}
