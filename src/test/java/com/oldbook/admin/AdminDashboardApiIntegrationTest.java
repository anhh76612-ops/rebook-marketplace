package com.oldbook.admin;

import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.entity.identity.TaiKhoan;
import com.oldbook.repository.identity.NguoiDungRepository;
import com.oldbook.repository.identity.TaiKhoanRepository;
import com.oldbook.service.auth.EmailService;
import com.oldbook.service.auth.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.config.import=")
@ActiveProfiles({"test", "catalog-test"})
class AdminDashboardApiIntegrationTest {

    private static final String SUMMARY = "/api/admin/dashboard/summary";

    // Các lớp test dùng chung một H2: đầu số 08 và 09 đã có lớp khác dùng, lớp này dùng 07.
    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Value("${local.server.port}") private int port;
    @Autowired private ObjectMapper mapper;
    @Autowired private NguoiDungRepository users;
    @Autowired private TaiKhoanRepository accounts;
    @Autowired private JwtService jwtService;
    @MockitoBean private EmailService emailService;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void dashboardShellRendersWithTheSharedAdminLayout() throws Exception {
        for (String path : new String[]{"/admin", "/admin/dashboard"}) {
            var response = request(path, null);
            assertThat(response.statusCode()).as(path + ": " + response.body()).isEqualTo(200);
            assertThat(response.body())
                    .contains("Tổng quan hệ thống - OldBook", "tailwind.config",
                            "id=\"adminModal\"", "id=\"adminNotification\"",
                            "/js/auth.js", "/js/admin/layout.js", "/js/admin/dashboard.js")
                    .doesNotContain("th:replace", "th:fragment", "admin@oldbook.local");
        }
        for (String script : new String[]{"/js/admin/layout.js", "/js/admin/dashboard.js"}) {
            assertThat(request(script, null).statusCode()).as(script).isEqualTo(200);
        }
    }

    @Test
    void summaryIsOnlyAvailableToAdministrators() throws Exception {
        assertThat(request(SUMMARY, null).statusCode()).isEqualTo(401);
        for (String role : new String[]{"KHACH_HANG", "CHU_CUA_HANG", "QUAN_LY"}) {
            assertThat(request(SUMMARY, token(account(user(), role, "HOAT_DONG"))).statusCode()).as(role).isEqualTo(403);
        }
    }

    @Test
    void summaryCountsUsersAndAccountsSeparately() throws Exception {
        String adminToken = token(account(user(), "QUAN_TRI_VIEN", "HOAT_DONG"));
        JsonNode before = summary(adminToken);

        // Một người dùng mới với hai tài khoản: một đang hoạt động, một bị khóa.
        NguoiDung user = user();
        account(user, "KHACH_HANG", "HOAT_DONG");
        account(user, "CHU_CUA_HANG", "BI_KHOA");

        JsonNode after = summary(adminToken);

        assertThat(delta(before, after, "tongNguoiDung")).isEqualTo(1);
        assertThat(delta(before, after, "tongTaiKhoan")).isEqualTo(2);
        assertThat(delta(before, after, "taiKhoanHoatDong")).isEqualTo(1);
        assertThat(delta(before, after, "taiKhoanBiKhoa")).isEqualTo(1);
        assertThat(delta(before, after, "taiKhoanChoXacThuc")).isZero();

        assertThat(roleCount(after, "KHACH_HANG") - roleCount(before, "KHACH_HANG")).isEqualTo(1);
        assertThat(roleCount(after, "CHU_CUA_HANG") - roleCount(before, "CHU_CUA_HANG")).isEqualTo(1);
        assertThat(roleCount(after, "QUAN_TRI_VIEN")).isPositive();

        long rolesTotal = 0;
        for (JsonNode role : after.get("phanBoVaiTro")) {
            assertThat(role.get("tenHienThi").asText()).isNotBlank();
            assertThat(role.get("soLuong").asLong()).isPositive();
            rolesTotal += role.get("soLuong").asLong();
        }
        assertThat(rolesTotal).isEqualTo(after.get("tongTaiKhoan").asLong());
    }

    private NguoiDung user() {
        return users.save(NguoiDung.builder().email(UUID.randomUUID() + "@test.local")
                .matKhau("test-only-hash").soDienThoai(String.format("07%08d", PHONE_SEQUENCE.incrementAndGet())).build());
    }

    private TaiKhoan account(NguoiDung user, String role, String status) {
        return accounts.save(TaiKhoan.builder().nguoiDung(user).vaiTro(role).trangThai(status).build());
    }

    private String token(TaiKhoan account) {
        return jwtService.generateToken(account.getNguoiDung().getMaND(), account.getMaTK(), account.getVaiTro());
    }

    private JsonNode summary(String token) throws Exception {
        var response = request(SUMMARY, token);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return mapper.readTree(response.body()).get("data");
    }

    private long delta(JsonNode before, JsonNode after, String field) {
        return after.get(field).asLong() - before.get(field).asLong();
    }

    private long roleCount(JsonNode summary, String role) {
        for (JsonNode item : summary.get("phanBoVaiTro")) {
            if (role.equals(item.get("vaiTro").asText())) {
                return item.get("soLuong").asLong();
            }
        }
        return 0;
    }

    private HttpResponse<String> request(String path, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(15));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
