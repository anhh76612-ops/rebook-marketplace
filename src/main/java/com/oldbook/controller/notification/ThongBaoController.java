package com.oldbook.controller.notification;

import com.oldbook.dto.catalog.PageResponse;
import com.oldbook.dto.common.ApiResponse;
import com.oldbook.dto.notification.ThongBaoChuaDocResponse;
import com.oldbook.dto.notification.ThongBaoResponse;
import com.oldbook.dto.notification.ThongBaoTicketResponse;
import com.oldbook.filter.auth.JwtAuthenticationFilter.AuthenticatedUserDetails;
import com.oldbook.service.notification.ThongBaoService;
import com.oldbook.service.notification.ThongBaoTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thông báo của chính tài khoản đang đăng nhập, dùng được cho mọi vai trò.
 * Mọi thao tác đều lấy mã tài khoản từ JWT, không nhận từ tham số, nên không xem được thông báo của người khác.
 */
@RestController
@RequestMapping("/api/thong-bao")
@RequiredArgsConstructor
public class ThongBaoController {

    private final ThongBaoService thongBaoService;
    private final ThongBaoTicketService ticketService;

    @GetMapping
    public ApiResponse<PageResponse<ThongBaoResponse>> danhSach(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {

        return ApiResponse.success(
                thongBaoService.danhSach(currentMaTK(), page, size)
        );
    }

    @GetMapping("/chua-doc")
    public ApiResponse<ThongBaoChuaDocResponse> demChuaDoc() {

        return ApiResponse.success(
                new ThongBaoChuaDocResponse(
                        thongBaoService.demChuaDoc(currentMaTK())
                )
        );
    }

    @PutMapping("/{maThongBao}/da-doc")
    public ApiResponse<Void> danhDauDaDoc(
            @PathVariable Integer maThongBao
    ) {

        thongBaoService.danhDauDaDoc(currentMaTK(), maThongBao);

        return ApiResponse.success("Đã đánh dấu đã đọc");
    }

    @PutMapping("/da-doc")
    public ApiResponse<Void> danhDauTatCaDaDoc() {

        thongBaoService.danhDauTatCaDaDoc(currentMaTK());

        return ApiResponse.success("Đã đánh dấu tất cả đã đọc");
    }

    // Vé dùng một lần để mở WebSocket /ws/thong-bao: JWT không được đặt lên URL của socket.
    @PostMapping("/ticket")
    public ApiResponse<ThongBaoTicketResponse> capVeKetNoi() {

        return ApiResponse.success(
                new ThongBaoTicketResponse(
                        ticketService.capVe(currentMaTK())
                )
        );
    }

    private Integer currentMaTK() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        return ((AuthenticatedUserDetails) authentication.getDetails()).maTK();
    }
}
