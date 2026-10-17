package com.oldbook.service.notification;

import com.oldbook.service.payment.PhienThanhToanDoiEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Đẩy tin "phiên thanh toán vừa đổi trạng thái" qua kênh WebSocket /ws/thong-bao cho khách đang mở trang quét mã.
 * Tin chỉ báo mã phiên và trạng thái mới: trang tự gọi lại REST để lấy dữ liệu đầy đủ.
 * Chỉ chạy sau khi giao dịch đổi trạng thái đã commit.
 */
@Component
@RequiredArgsConstructor
public class PhienThanhToanPusher {

    public static final String LOAI_TIN = "PHIEN_THANH_TOAN_DOI";

    private static final Logger log = LoggerFactory.getLogger(PhienThanhToanPusher.class);

    private final ThongBaoSocketHandler socketHandler;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void day(PhienThanhToanDoiEvent event) {
        if (!socketHandler.dangKetNoi(event.maTK())) {
            return;
        }

        try {
            socketHandler.gui(event.maTK(), objectMapper.writeValueAsString(Map.of(
                    "type", LOAI_TIN,
                    "maPhien", event.maPhien(),
                    "trangThai", event.trangThai()
            )));
        } catch (RuntimeException ex) {
            // Đẩy tức thì chỉ là tiện ích thêm; lỗi ở đây không được làm hỏng thao tác chính.
            log.warn("Không đẩy được trạng thái phiên thanh toán #{}: {}", event.maPhien(), ex.getMessage());
        }
    }
}
