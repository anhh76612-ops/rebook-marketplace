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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.config.import=")
@ActiveProfiles({"test", "catalog-test"})
class AdminThongBaoIntegrationTest {

    private static final String ACCOUNTS = "/api/admin/tai-khoan";
    private static final String NOTIFICATIONS = "/api/thong-bao";

    // Các lớp test dùng chung một H2 và số điện thoại là duy nhất: mỗi lớp giữ một đầu số riêng, lớp này dùng 06.
    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Value("${local.server.port}") private int port;
    @Autowired private ObjectMapper mapper;
    @Autowired private NguoiDungRepository users;
    @Autowired private TaiKhoanRepository accounts;
    @Autowired private JwtService jwtService;
    @MockitoBean private EmailService emailService;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void accountEventsNotifyAdministratorsAccordingToTheRolesInvolved() throws Exception {
        String actor = token(account("QUAN_TRI_VIEN"));
        String otherAdmin = token(account("QUAN_TRI_VIEN"));
        String manager = token(account("QUAN_LY"));

        // Tạo một quản trị viên mới: mức quan trọng, mở ra đúng tài khoản đó.
        String adminEmail = email();
        int newAdmin = create(actor, adminEmail, "QUAN_TRI_VIEN");

        JsonNode created = latest(otherAdmin);
        assertThat(created.get("loai").asText()).isEqualTo("TAO_TAI_KHOAN");
        assertThat(created.get("mucDo").asText()).isEqualTo("QUAN_TRONG");
        assertThat(created.get("tieuDe").asText()).isEqualTo("Tài khoản Quản trị viên mới được tạo");
        assertThat(created.get("noiDung").asText()).contains("đã tạo tài khoản #" + newAdmin);
        assertThat(created.get("duongDan").asText())
                .isEqualTo("/admin/accounts?tuKhoa=" + URLEncoder.encode(adminEmail, StandardCharsets.UTF_8));
        assertThat(created.get("daDoc").asBoolean()).isFalse();
        assertThat(unread(otherAdmin)).isEqualTo(1);

        // Người thực hiện có một bản để tra lại nhưng không bị tính là chưa đọc.
        assertThat(latest(actor).get("loai").asText()).isEqualTo("TAO_TAI_KHOAN");
        assertThat(latest(actor).get("daDoc").asBoolean()).isTrue();
        assertThat(unread(actor)).isZero();

        // Thông báo quản trị không gửi cho vai trò khác.
        assertThat(list(manager, "").get("content").size()).isZero();

        // Tạo và khóa tài khoản khách hàng là vận hành thường ngày: không báo.
        int customer = create(actor, email(), "KHACH_HANG");
        int otherCustomer = create(actor, email(), "KHACH_HANG");
        assertCode(put(actor, ACCOUNTS + "/" + otherCustomer + "/khoa", Map.of("lyDo", "Vi phạm nội quy")), 200);
        assertThat(unread(otherAdmin)).isEqualTo(1);

        // Khóa một quản trị viên: mức nghiêm trọng, kèm lý do.
        assertCode(put(actor, ACCOUNTS + "/" + newAdmin + "/khoa", Map.of("lyDo", "Nghi bị chiếm quyền")), 200);
        JsonNode locked = latest(otherAdmin);
        assertThat(locked.get("loai").asText()).isEqualTo("KHOA_TAI_KHOAN");
        assertThat(locked.get("mucDo").asText()).isEqualTo("NGHIEM_TRONG");
        assertThat(locked.get("tieuDe").asText()).isEqualTo("Tài khoản Quản trị viên bị khóa");
        assertThat(locked.get("noiDung").asText()).contains("Lý do: Nghi bị chiếm quyền");

        // Mở khóa quản trị viên: quan trọng.
        assertCode(put(actor, ACCOUNTS + "/" + newAdmin + "/mo-khoa", null), 200);
        assertThat(latest(otherAdmin).get("loai").asText()).isEqualTo("MO_KHOA_TAI_KHOAN");
        assertThat(latest(otherAdmin).get("mucDo").asText()).isEqualTo("QUAN_TRONG");

        // Thêm vai trò Chủ cửa hàng cho một khách hàng là vận hành thường ngày: không báo.
        assertCode(send("POST", ACCOUNTS + "/" + customer + "/vai-tro", actor, Map.of("vaiTro", "CHU_CUA_HANG")), 200);
        assertThat(unread(otherAdmin)).isEqualTo(3);

        // Thêm vai trò Quản lý: chỉ là thông tin.
        assertCode(send("POST", ACCOUNTS + "/" + customer + "/vai-tro", actor, Map.of("vaiTro", "QUAN_LY")), 200);
        JsonNode promoted = latest(otherAdmin);
        assertThat(promoted.get("loai").asText()).isEqualTo("THEM_VAI_TRO");
        assertThat(promoted.get("mucDo").asText()).isEqualTo("THONG_TIN");
        assertThat(promoted.get("tieuDe").asText()).isEqualTo("Thêm vai trò Quản lý");

        // Cấp quyền quản trị viên: quan trọng.
        assertCode(send("POST", ACCOUNTS + "/" + customer + "/vai-tro", actor, Map.of("vaiTro", "QUAN_TRI_VIEN")), 200);
        assertThat(latest(otherAdmin).get("mucDo").asText()).isEqualTo("QUAN_TRONG");
        assertThat(latest(otherAdmin).get("tieuDe").asText()).isEqualTo("Thêm vai trò Quản trị viên");

        // Tài khoản khách hàng ban đầu vẫn là khách hàng.
        assertThat(accounts.findById(customer).orElseThrow().getVaiTro()).isEqualTo("KHACH_HANG");

        assertThat(unread(otherAdmin)).isEqualTo(5);
    }

    @Test
    void readStateBelongsToEachRecipientAndTheListIsPaged() throws Exception {
        assertThat(get(NOTIFICATIONS, null).statusCode()).isEqualTo(401);
        assertThat(get(NOTIFICATIONS + "/chua-doc", null).statusCode()).isEqualTo(401);

        String actor = token(account("QUAN_TRI_VIEN"));
        String reader = token(account("QUAN_TRI_VIEN"));

        create(actor, email(), "QUAN_TRI_VIEN");
        create(actor, email(), "QUAN_LY");
        create(actor, email(), "QUAN_TRI_VIEN");
        assertThat(unread(reader)).isEqualTo(3);

        // Mới nhất trước; mỗi trang đúng số dòng yêu cầu.
        JsonNode firstPage = list(reader, "?page=0&size=2");
        assertThat(firstPage.get("content").size()).isEqualTo(2);
        assertThat(firstPage.get("totalElements").asLong()).isEqualTo(3);
        assertThat(firstPage.get("totalPages").asInt()).isEqualTo(2);
        assertThat(firstPage.get("content").get(0).get("maThongBao").asInt())
                .isGreaterThan(firstPage.get("content").get(1).get("maThongBao").asInt());
        assertThat(list(reader, "?page=1&size=2").get("content").size()).isEqualTo(1);

        // Không ai đánh dấu được thông báo của người khác.
        int mine = firstPage.get("content").get(0).get("maThongBao").asInt();
        assertCode(put(actor, NOTIFICATIONS + "/" + mine + "/da-doc", null), 400);
        assertThat(unread(reader)).isEqualTo(3);

        // Đánh dấu một thông báo: lặp lại cũng không sao.
        assertCode(put(reader, NOTIFICATIONS + "/" + mine + "/da-doc", null), 200);
        assertCode(put(reader, NOTIFICATIONS + "/" + mine + "/da-doc", null), 200);
        assertThat(unread(reader)).isEqualTo(2);
        assertThat(latest(reader).get("daDoc").asBoolean()).isTrue();

        // Đánh dấu tất cả chỉ ảnh hưởng tới người gọi.
        String bystander = token(account("QUAN_TRI_VIEN"));
        create(actor, email(), "QUAN_TRI_VIEN");
        assertThat(unread(bystander)).isEqualTo(1);
        assertCode(put(reader, NOTIFICATIONS + "/da-doc", null), 200);
        assertThat(unread(reader)).isZero();
        assertThat(unread(bystander)).isEqualTo(1);
        for (JsonNode item : list(reader, "?page=0&size=50").get("content")) {
            assertThat(item.get("daDoc").asBoolean()).isTrue();
        }
    }

    private int create(String adminToken, String email, String role) throws Exception {
        var response = send("POST", ACCOUNTS, adminToken, Map.of(
                "hoTen", "Người dùng " + role, "email", email, "matKhau", "Admin1234", "vaiTro", role,
                "soDienThoai", String.format("06%08d", PHONE_SEQUENCE.incrementAndGet())));
        assertCode(response, 200);
        return mapper.readTree(response.body()).get("data").get("maTK").asInt();
    }

    private JsonNode list(String token, String query) throws Exception {
        var response = get(NOTIFICATIONS + query, token);
        assertCode(response, 200);
        return mapper.readTree(response.body()).get("data");
    }

    private JsonNode latest(String token) throws Exception {
        return list(token, "?page=0&size=1").get("content").get(0);
    }

    private long unread(String token) throws Exception {
        var response = get(NOTIFICATIONS + "/chua-doc", token);
        assertCode(response, 200);
        return mapper.readTree(response.body()).get("data").get("soLuong").asLong();
    }

    private String email() {
        return "tb-" + UUID.randomUUID() + "@test.local";
    }

    private TaiKhoan account(String role) {
        NguoiDung user = users.save(NguoiDung.builder().email(email()).matKhau("test-only-hash").hoTen("Admin kiểm thử")
                .soDienThoai(String.format("06%08d", PHONE_SEQUENCE.incrementAndGet())).build());
        return accounts.save(TaiKhoan.builder().nguoiDung(user).vaiTro(role).trangThai("HOAT_DONG").build());
    }

    private String token(TaiKhoan account) {
        return jwtService.generateToken(account.getNguoiDung().getMaND(), account.getMaTK(), account.getVaiTro());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return send("GET", path, token, null);
    }

    private HttpResponse<String> put(String token, String path, Object body) throws Exception {
        return send("PUT", path, token, body);
    }

    private HttpResponse<String> send(String method, String path, String token, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(15));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertCode(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
    }
}
