package com.oldbook.admin;

import com.oldbook.repository.notification.ThongBaoRepository;
import com.oldbook.service.admin.AdminDashboardService;
import com.oldbook.service.admin.AdminNhatKyHeThongService;
import com.oldbook.service.admin.AdminReportService;
import com.oldbook.service.notification.ThongBaoService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chạy các truy vấn của khu vực admin trên SQL Server thật (cấu hình trong .env). Chỉ đọc dữ liệu.
 *
 * Các test khác dùng H2, mà H2 dễ tính hơn SQL Server: ví dụ H2 cho phép lower() trên cột TEXT
 * còn SQL Server thì báo lỗi. Lớp này để bắt loại lỗi đó trước khi chạy ứng dụng thật.
 *
 * Mặc định bị bỏ qua. Chạy khi cần:
 *   ./mvnw test -Dtest=AdminSqlServerSmokeTest -Dsqlserver=true
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "sqlserver", matches = "true")
class AdminSqlServerSmokeTest {

    @Autowired private AdminNhatKyHeThongService logs;
    @Autowired private AdminDashboardService dashboard;
    @Autowired private AdminReportService reports;
    @Autowired private ThongBaoService notifications;
    @Autowired private ThongBaoRepository notificationRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void auditLogQueriesRun() {
        LocalDate today = LocalDate.now();

        // Không có bộ lọc: đúng lời gọi của khối "Nhật ký gần đây" trên dashboard.
        assertThat(logs.search(null, null, null, null, null, null, 0, 10).content()).isNotNull();
        // Có từ khóa: nhánh so khớp trên mọi cột, kể cả cột mo_ta kiểu TEXT.
        assertThat(logs.search("khóa", "KHOA_TAI_KHOAN", "QUAN_TRI_VIEN", "127", today.minusDays(30), today, 0, 10).content()).isNotNull();
        assertThat(logs.export("a", null, null, null, null, null)).isNotNull();
        assertThat(logs.getSummary()).isNotNull();
    }

    @Test
    void dashboardAndReportQueriesRun() {
        LocalDate today = LocalDate.now();

        assertThat(dashboard.getSummary().getTongTaiKhoan()).isNotNegative();
        assertThat(reports.getRevenue(null, null).getPoints()).hasSize(30);
        assertThat(reports.getRevenue(today.minusDays(365), today).getPoints()).hasSize(366);
    }

    @Test
    void notificationQueriesRun() {
        // Mã tài khoản không tồn tại: chạy đủ các câu lệnh mà không đụng tới dữ liệu của ai.
        int nobody = -1;

        assertThat(notifications.danhSach(nobody, 0, 10).content()).isEmpty();
        assertThat(notifications.demChuaDoc(nobody)).isZero();
        notifications.danhDauTatCaDaDoc(nobody);
        assertThat(notificationRepository.findAccountsByRoleAndStatus("QUAN_TRI_VIEN", "HOAT_DONG")).isNotNull();

        // Tiêu đề và nội dung là tiếng Việt nên phải là cột Unicode (nvarchar), không phải varchar hay text.
        assertThat(entityManager.createNativeQuery("""
                        select data_type
                        from information_schema.columns
                        where table_name = 'thong_bao' and column_name in ('tieu_de', 'noi_dung')
                        """).getResultList())
                .containsOnly("nvarchar");
    }
}
