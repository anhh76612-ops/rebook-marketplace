package com.oldbook.web;

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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.config.import=")
@ActiveProfiles({"test", "catalog-test"})
class ApiUiIntegrationTest {

    @Value("${local.server.port}") private int port;
    @Autowired private ObjectMapper mapper;
    @Autowired private NguoiDungRepository users;
    @Autowired private TaiKhoanRepository accounts;
    @Autowired private JwtService jwtService;
    @MockitoBean private EmailService emailService;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void htmlShellsRenderWithoutJwtAndTheirApisStillRequirePermissions() throws Exception {
        var pages = Map.of(
                "/store", "store.js",
                "/vendor/books", "store.js",
                "/store-moderation", "store-moderation.js",
                "/admin/accounts", "admin-accounts.js",
                "/profile", "profile.js",
                "/addresses", "addresses.js");
        for (var page : pages.entrySet()) {
            var response = request("GET", page.getKey(), null, null);
            assertThat(response.statusCode()).as(page.getKey() + ": " + response.body()).isEqualTo(200);
            assertThat(response.body()).contains("/js/" + page.getValue(), "/js/auth.js")
                    .doesNotContain("th:replace", "fragments/layout");
            assertThat(request("GET", "/js/" + page.getValue(), null, null).statusCode()).isEqualTo(200);
        }
        for (String endpoint : new String[]{"/api/store/me", "/api/store/kiem-duyet/sach", "/api/admin/tai-khoan"}) {
            assertThat(request("GET", endpoint, null, null).statusCode()).as(endpoint).isEqualTo(401);
        }
        String customerToken = actor("KHACH_HANG");
        for (String endpoint : new String[]{"/api/store/me", "/api/store/kiem-duyet/sach", "/api/admin/tai-khoan"}) {
            assertThat(request("GET", endpoint, customerToken, null).statusCode()).as(endpoint).isEqualTo(403);
        }
    }

    @Test
    void adminFormContractsValidateCreateChangeRoleAndLockUnlock() throws Exception {
        String adminToken = actor("QUAN_TRI_VIEN");
        assertCode(request("POST", "/api/admin/tai-khoan", adminToken, Map.of()), 400);
        String email = "owner-" + UUID.randomUUID() + "@test.local";
        var created = data(request("POST", "/api/admin/tai-khoan", adminToken, Map.of(
                "hoTen", "Chủ shop", "email", email, "matKhau", "Owner1234", "vaiTro", "CHU_CUA_HANG", "soDienThoai", "0942345678")));
        int id = created.get("maTK").asInt();
        String path = "/api/admin/tai-khoan/" + id;
        assertThat(data(request("GET", "/api/admin/tai-khoan?tuKhoa=" + email + "&vaiTro=CHU_CUA_HANG", adminToken, null)).size()).isEqualTo(1);
        assertCode(request("PUT", path + "/vai-tro", adminToken, Map.of()), 400);
        assertThat(data(request("PUT", path + "/vai-tro", adminToken, Map.of("vaiTro", "QUAN_LY"))).get("vaiTro").asText()).isEqualTo("QUAN_LY");
        assertCode(request("PUT", path + "/khoa", adminToken, Map.of("lyDo", " ")), 400);
        assertCode(request("PUT", path + "/khoa", adminToken, Map.of("lyDo", "Kiểm tra tài khoản")), 200);
        assertThat(accounts.findById(id).orElseThrow().getTrangThai()).isEqualTo("BI_KHOA");
        assertCode(request("PUT", path + "/mo-khoa", adminToken, null), 200);
        assertThat(accounts.findById(id).orElseThrow().getTrangThai()).isEqualTo("HOAT_DONG");
    }

    private String actor(String role) {
        var user = users.save(NguoiDung.builder().email(UUID.randomUUID() + "@test.local")
                .matKhau("test-only-hash").soDienThoai(String.format("09%08d", users.count() + 1)).build());
        var account = accounts.save(TaiKhoan.builder().nguoiDung(user).vaiTro(role).trangThai("HOAT_DONG").build());
        return jwtService.generateToken(user.getMaND(), account.getMaTK(), role);
    }

    private HttpResponse<String> request(String method, String path, String token, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(15));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode data(HttpResponse<String> response) {
        assertCode(response, 200);
        return mapper.readTree(response.body()).get("data");
    }

    private void assertCode(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
    }
}
