package com.oldbook.notification;

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
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Thông báo cho khách hàng, chủ cửa hàng và quản lý, cùng kênh WebSocket đẩy thông báo mới.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.config.import=")
@ActiveProfiles({"test", "catalog-test"})
class NghiepVuThongBaoIntegrationTest {

    private static final String NOTIFICATIONS = "/api/thong-bao";

    // Các lớp test dùng chung một H2 và số điện thoại là duy nhất: mỗi lớp giữ một đầu số riêng, lớp này dùng 04.
    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Value("${local.server.port}") private int port;
    @Autowired private ObjectMapper mapper;
    @Autowired private NguoiDungRepository users;
    @Autowired private TaiKhoanRepository accounts;
    @Autowired private JwtService jwtService;
    @MockitoBean private EmailService emailService;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void storeRegistrationNotifiesManagersAndTheDecisionNotifiesTheSeller() throws Exception {
        String manager = token(account(user(), "QUAN_LY"));
        TaiKhoan customer = account(user(), "KHACH_HANG");
        long unreadBefore = unread(manager);

        String storeName = "Tiệm " + UUID.randomUUID();
        JsonNode registered = data(send("POST", "/api/store/register", token(customer), Map.of(
                "tenCuaHang", storeName, "diaChiCH", "5 Nguyễn Xí", "soDienThoaiCH", "0281111111")));
        int maCH = registered.get("cuaHang").get("maCH").asInt();
        String seller = token(accounts.findById(registered.get("maTK").asInt()).orElseThrow());

        JsonNode pending = latest(manager);
        assertThat(pending.get("loai").asText()).isEqualTo("GIAN_HANG_CHO_DUYET");
        assertThat(pending.get("tieuDe").asText()).isEqualTo("Gian hàng mới chờ duyệt");
        assertThat(pending.get("noiDung").asText()).contains(storeName);
        assertThat(pending.get("duongDan").asText()).isEqualTo("/store-moderation");
        assertThat(unread(manager)).isEqualTo(unreadBefore + 1);

        // Người đăng ký không tự nhận thông báo về việc mình vừa làm.
        assertThat(list(seller).get("content").size()).isZero();
        assertThat(list(token(customer)).get("content").size()).isZero();

        // Quản lý duyệt: chủ cửa hàng được báo, ở đúng tài khoản Chủ cửa hàng.
        assertCode(send("POST", "/api/store/kiem-duyet/cua-hang/" + maCH + "/duyet", manager, null), 200);
        JsonNode approved = latest(seller);
        assertThat(approved.get("loai").asText()).isEqualTo("CUA_HANG_DUOC_DUYET");
        assertThat(approved.get("duongDan").asText()).isEqualTo("/store/books");
        assertThat(approved.get("daDoc").asBoolean()).isFalse();
        assertThat(list(token(customer)).get("content").size()).isZero();

        // Sửa thông tin đưa cửa hàng về chờ duyệt và báo lại cho quản lý; lần này bị từ chối kèm lý do.
        assertCode(send("PUT", "/api/store/me", seller, Map.of(
                "tenCuaHang", storeName, "diaChiCH", "7 Nguyễn Xí", "soDienThoaiCH", "0281111111")), 200);
        assertThat(latest(manager).get("tieuDe").asText()).isEqualTo("Gian hàng gửi duyệt lại");
        assertCode(send("POST", "/api/store/kiem-duyet/cua-hang/" + maCH + "/tu-choi", manager,
                Map.of("lyDo", "Địa chỉ chưa rõ ràng")), 200);
        JsonNode rejected = latest(seller);
        assertThat(rejected.get("loai").asText()).isEqualTo("CUA_HANG_BI_TU_CHOI");
        assertThat(rejected.get("mucDo").asText()).isEqualTo("QUAN_TRONG");
        assertThat(rejected.get("noiDung").asText()).contains("Địa chỉ chưa rõ ràng");
    }

    @Test
    void addingARoleNotifiesTheAccountsThePersonAlreadyUses() throws Exception {
        String admin = token(account(user(), "QUAN_TRI_VIEN"));
        TaiKhoan customer = account(user(), "KHACH_HANG");

        JsonNode added = data(send("POST", "/api/admin/tai-khoan/" + customer.getMaTK() + "/vai-tro", admin,
                Map.of("vaiTro", "CHU_CUA_HANG")));

        JsonNode notice = latest(token(customer));
        assertThat(notice.get("loai").asText()).isEqualTo("DUOC_THEM_VAI_TRO");
        assertThat(notice.get("tieuDe").asText()).isEqualTo("Bạn có thêm vai trò Chủ cửa hàng");

        // Tài khoản mới chưa ai đăng nhập nên không nhận bản nào.
        String seller = token(accounts.findById(added.get("maTK").asInt()).orElseThrow());
        assertThat(list(seller).get("content").size()).isZero();
    }

    @Test
    void newNotificationIsPushedOverTheSocketOpenedWithAOneTimeTicket() throws Exception {
        String manager = token(account(user(), "QUAN_LY"));

        assertCode(send("POST", NOTIFICATIONS + "/ticket", null, null), 401);
        assertThatThrownBy(() -> connect("khong-phai-ve", new CompletableFuture<>()))
                .isInstanceOf(ExecutionException.class);

        String ticket = data(send("POST", NOTIFICATIONS + "/ticket", manager, null)).get("ticket").asText();
        CompletableFuture<String> firstMessage = new CompletableFuture<>();
        WebSocket socket = connect(ticket, firstMessage);

        try {
            // Vé đã dùng thì không mở thêm được kết nối nào.
            assertThatThrownBy(() -> connect(ticket, new CompletableFuture<>()))
                    .isInstanceOf(ExecutionException.class);

            long unreadBefore = unread(manager);
            String storeName = "Tiệm " + UUID.randomUUID();
            assertCode(send("POST", "/api/store/register", token(account(user(), "KHACH_HANG")), Map.of(
                    "tenCuaHang", storeName, "diaChiCH", "9 Lê Lợi", "soDienThoaiCH", "0282222222")), 200);

            JsonNode pushed = mapper.readTree(firstMessage.get(10, TimeUnit.SECONDS));
            assertThat(pushed.get("type").asText()).isEqualTo("THONG_BAO_MOI");
            assertThat(pushed.get("thongBao").get("loai").asText()).isEqualTo("GIAN_HANG_CHO_DUYET");
            assertThat(pushed.get("thongBao").get("noiDung").asText()).contains(storeName);
            assertThat(pushed.get("chuaDoc").asLong()).isEqualTo(unreadBefore + 1);
        } finally {
            socket.abort();
        }
    }

    private WebSocket connect(String ticket, CompletableFuture<String> firstMessage) throws Exception {
        return client.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/thong-bao?ticket=" + ticket), new WebSocket.Listener() {
                    private final StringBuilder text = new StringBuilder();

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        text.append(data);
                        if (last) {
                            firstMessage.complete(text.toString());
                        }
                        webSocket.request(1);
                        return null;
                    }
                })
                .get(10, TimeUnit.SECONDS);
    }

    private JsonNode list(String token) throws Exception {
        return data(send("GET", NOTIFICATIONS + "?page=0&size=10", token, null));
    }

    private JsonNode latest(String token) throws Exception {
        return list(token).get("content").get(0);
    }

    private long unread(String token) throws Exception {
        return data(send("GET", NOTIFICATIONS + "/chua-doc", token, null)).get("soLuong").asLong();
    }

    private NguoiDung user() {
        return users.save(NguoiDung.builder().email("nv-" + UUID.randomUUID() + "@test.local").matKhau("test-only-hash")
                .hoTen("Người kiểm thử").soDienThoai(String.format("04%08d", PHONE_SEQUENCE.incrementAndGet())).build());
    }

    private TaiKhoan account(NguoiDung user, String role) {
        return accounts.save(TaiKhoan.builder().nguoiDung(user).vaiTro(role).trangThai("HOAT_DONG").build());
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
