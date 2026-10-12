package com.oldbook.store;

import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.entity.identity.TaiKhoan;
import com.oldbook.repository.identity.NguoiDungRepository;
import com.oldbook.repository.identity.TaiKhoanRepository;
import com.oldbook.repository.store.CuaHangRepository;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Khách hàng tự đăng ký gian hàng rồi chuyển qua lại giữa các tài khoản của chính mình.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.config.import=")
@ActiveProfiles({"test", "catalog-test"})
class StoreRegistrationIntegrationTest {

    private static final String REGISTER = "/api/store/register";
    private static final String SWITCH = "/api/auth/switch-account";

    // Các lớp test dùng chung một H2 và số điện thoại là duy nhất: mỗi lớp giữ một đầu số riêng, lớp này dùng 03.
    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Value("${local.server.port}") private int port;
    @Autowired private ObjectMapper mapper;
    @Autowired private NguoiDungRepository users;
    @Autowired private TaiKhoanRepository accounts;
    @Autowired private CuaHangRepository stores;
    @Autowired private JwtService jwtService;
    @MockitoBean private EmailService emailService;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void customerRegistersAStoreAndGetsASellerAccountWithAPendingStore() throws Exception {
        TaiKhoan customer = account(user(), "KHACH_HANG", "HOAT_DONG");
        String token = token(customer);
        int maND = customer.getNguoiDung().getMaND();

        assertCode(send("POST", REGISTER, null, storeBody()), 401);
        assertCode(send("POST", REGISTER, token, Map.of("tenCuaHang", " ")), 400);
        assertThat(accounts.findByNguoiDung_MaND(maND)).hasSize(1);

        JsonNode registered = data(send("POST", REGISTER, token, storeBody()));
        int sellerMaTK = registered.get("maTK").asInt();
        assertThat(sellerMaTK).isNotEqualTo(customer.getMaTK());
        assertThat(registered.get("cuaHang").get("trangThaiDuyet").asText()).isEqualTo("CHO_DUYET");

        TaiKhoan seller = accounts.findById(sellerMaTK).orElseThrow();
        assertThat(seller.getVaiTro()).isEqualTo("CHU_CUA_HANG");
        assertThat(seller.getTrangThai()).isEqualTo("HOAT_DONG");
        assertThat(stores.findByChuShop_MaND(maND)).isPresent();

        // Hồ sơ liệt kê cả hai tài khoản để header biết có thể chuyển sang tài khoản nào.
        JsonNode profile = data(send("GET", "/api/profile", token, null));
        assertThat(profile.get("taiKhoans").findValuesAsString("vaiTro"))
                .containsExactlyInAnyOrder("KHACH_HANG", "CHU_CUA_HANG");

        // Mỗi người chỉ có một tài khoản Chủ cửa hàng.
        assertCode(send("POST", REGISTER, token, storeBody()), 400);
        assertThat(accounts.findByNguoiDung_MaND(maND)).hasSize(2);

        // Chỉ tài khoản Khách hàng mới đăng ký được.
        assertCode(send("POST", REGISTER, token(seller), storeBody()), 403);
        assertCode(send("POST", REGISTER, token(account(user(), "QUAN_LY", "HOAT_DONG")), storeBody()), 403);
    }

    @Test
    void registrationRollsBackTheSellerAccountWhenTheStoreIsRejectedByValidation() throws Exception {
        String taken = "Tiệm sách " + UUID.randomUUID();
        assertCode(send("POST", REGISTER, token(account(user(), "KHACH_HANG", "HOAT_DONG")), storeBody(taken)), 200);

        TaiKhoan second = account(user(), "KHACH_HANG", "HOAT_DONG");
        assertCode(send("POST", REGISTER, token(second), storeBody(taken)), 400);
        assertThat(accounts.findByNguoiDung_MaND(second.getNguoiDung().getMaND())).hasSize(1);
    }

    @Test
    void switchingAccountIssuesATokenForTheOtherRoleAndRevokesTheOldOne() throws Exception {
        NguoiDung user = user();
        TaiKhoan customer = account(user, "KHACH_HANG", "HOAT_DONG");
        TaiKhoan seller = account(user, "CHU_CUA_HANG", "HOAT_DONG");
        TaiKhoan locked = account(user, "QUAN_LY", "BI_KHOA");
        TaiKhoan stranger = account(user(), "CHU_CUA_HANG", "HOAT_DONG");
        String customerToken = token(customer);

        assertCode(send("POST", SWITCH, null, Map.of("maTK", seller.getMaTK())), 401);
        assertCode(send("POST", SWITCH, customerToken, Map.of()), 400);
        assertCode(send("POST", SWITCH, customerToken, Map.of("maTK", customer.getMaTK())), 400);
        assertCode(send("POST", SWITCH, customerToken, Map.of("maTK", stranger.getMaTK())), 400);
        assertCode(send("POST", SWITCH, customerToken, Map.of("maTK", locked.getMaTK())), 400);

        // Các lần bị từ chối không làm mất phiên hiện tại.
        assertCode(send("GET", "/api/profile", customerToken, null), 200);

        JsonNode switched = data(send("POST", SWITCH, customerToken, Map.of("maTK", seller.getMaTK())));
        assertThat(switched.get("vaiTro").asText()).isEqualTo("CHU_CUA_HANG");
        assertThat(switched.get("maTK").asInt()).isEqualTo(seller.getMaTK());
        String sellerToken = switched.get("token").asText();

        // Token mới mang quyền chủ cửa hàng, token cũ bị thu hồi.
        assertCode(send("POST", REGISTER, sellerToken, storeBody()), 403);
        assertCode(send("GET", "/api/profile", sellerToken, null), 200);
        assertCode(send("GET", "/api/profile", customerToken, null), 401);

        // Chuyển ngược lại cũng được.
        JsonNode back = data(send("POST", SWITCH, sellerToken, Map.of("maTK", customer.getMaTK())));
        assertThat(back.get("vaiTro").asText()).isEqualTo("KHACH_HANG");
    }

    private Map<String, String> storeBody() {
        return storeBody("Tiệm sách " + UUID.randomUUID());
    }

    private Map<String, String> storeBody(String name) {
        return Map.of("tenCuaHang", name, "diaChiCH", "12 Đinh Lễ, Hà Nội", "soDienThoaiCH", "0240000000",
                "moTa", "Sách văn học cũ");
    }

    private NguoiDung user() {
        return users.save(NguoiDung.builder().email("dk-" + UUID.randomUUID() + "@test.local").matKhau("test-only-hash")
                .hoTen("Người đăng ký").soDienThoai(String.format("03%08d", PHONE_SEQUENCE.incrementAndGet())).build());
    }

    private TaiKhoan account(NguoiDung user, String role, String status) {
        return accounts.save(TaiKhoan.builder().nguoiDung(user).vaiTro(role).trangThai(status).build());
    }

    private String token(TaiKhoan account) {
        return jwtService.generateToken(account.getNguoiDung().getMaND(), account.getMaTK(), account.getVaiTro());
    }

    private HttpResponse<String> send(String method, String path, String token, Object body) throws Exception {
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
