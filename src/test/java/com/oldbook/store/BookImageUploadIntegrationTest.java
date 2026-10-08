package com.oldbook.store;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.oldbook.entity.catalog.CuaHang;
import com.oldbook.entity.catalog.DanhMuc;
import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.entity.identity.TaiKhoan;
import com.oldbook.repository.catalog.DanhMucRepository;
import com.oldbook.repository.identity.NguoiDungRepository;
import com.oldbook.repository.identity.TaiKhoanRepository;
import com.oldbook.repository.store.CuaHangRepository;
import com.oldbook.service.auth.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=",
                "spring.datasource.url=jdbc:h2:mem:book_image_upload_${random.uuid};MODE=MSSQLServer;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
                "cloudinary.cloud-name=test-cloud",
                "cloudinary.api-key=test-key",
                "cloudinary.api-secret=test-secret"
        })
@ActiveProfiles({"test", "catalog-test"})
class BookImageUploadIntegrationTest {

    private static final String UPLOAD_PATH = "/api/store/me/sach/hinh-anh";
    private static final String SECURE_URL = "https://res.cloudinary.com/test-cloud/image/upload/oldbook/books/cover.png";
    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Value("${local.server.port}") private int port;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwtService;
    @Autowired private NguoiDungRepository users;
    @Autowired private TaiKhoanRepository accounts;
    @Autowired private CuaHangRepository stores;
    @Autowired private DanhMucRepository categories;
    @MockitoSpyBean private Cloudinary cloudinary;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private Uploader uploader;
    private Actor owner;
    private CuaHang store;

    @BeforeEach
    void seed() throws IOException {
        owner = actor("CHU_CUA_HANG");
        store = stores.save(CuaHang.builder().chuShop(users.getReferenceById(owner.maND()))
                .tenCuaHang("Image upload shop " + UUID.randomUUID()).trangThaiDuyet("DA_DUYET").build());
        uploader = mock(Uploader.class);
        doReturn(uploader).when(cloudinary).uploader();
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of("secure_url", SECURE_URL));
    }

    @Test
    void approvedStoreOwnerUploadsRealPngAndReceivesSecureCloudinaryUrl() throws Exception {
        byte[] png = image("png");
        var response = upload(owner.token(), "cover.png", "image/png", png);

        assertCode(response, 200);
        assertThat(json(response).get("data").get("hinhAnhUrl").asText()).isEqualTo(SECURE_URL);
        ArgumentCaptor<Map> options = ArgumentCaptor.forClass(Map.class);
        verify(uploader).upload(eq(png), options.capture());
        assertThat(options.getValue()).containsEntry("resource_type", "image")
                .containsEntry("folder", "oldbook/books")
                .containsEntry("timeout", 30)
                .containsEntry("allowed_formats", List.of("jpg", "jpeg", "png", "webp"));
        assertThat(response.body()).doesNotContain("test-key", "test-secret");
    }

    @Test
    void acceptsRealJpegImages() throws Exception {
        byte[] jpeg = image("jpg");
        assertCode(upload(owner.token(), "cover.jpg", "image/jpeg", jpeg), 200);
        verify(uploader).upload(eq(jpeg), anyMap());
    }

    @Test
    void uploadRequiresOwnerAuthenticationBeforeCallingCloudinary() throws Exception {
        byte[] png = image("png");
        assertCode(upload(null, "cover.png", "image/png", png), 401);
        assertCode(upload("invalid-token", "cover.png", "image/png", png), 401);
        assertCode(upload(jwtService.generateRoleSelectionToken(owner.maND()), "cover.png", "image/png", png), 401);
        for (String role : List.of("KHACH_HANG", "QUAN_LY", "QUAN_TRI_VIEN")) {
            assertCode(upload(actor(role).token(), "cover.png", "image/png", png), 403);
        }
        verifyNoInteractions(uploader);
    }

    @Test
    void ownerMustHaveAnApprovedStoreBeforeCallingCloudinary() throws Exception {
        byte[] png = image("png");
        assertCode(upload(actor("CHU_CUA_HANG").token(), "cover.png", "image/png", png), 400);
        for (String approval : List.of("CHO_DUYET", "TU_CHOI")) {
            store.setTrangThaiDuyet(approval);
            stores.save(store);
            assertCode(upload(owner.token(), "cover.png", "image/png", png), 400);
        }
        verifyNoInteractions(uploader);
    }

    @Test
    void missingAndEmptyFileReturnStructuredValidationErrors() throws Exception {
        assertCode(upload(owner.token(), null, null, null), 400);
        assertCode(upload(owner.token(), "empty.png", "image/png", new byte[0]), 400);
        verifyNoInteractions(uploader);
    }

    @Test
    void unsupportedFormatsForgedContentAndSpoofedMimeAreRejected() throws Exception {
        assertCode(upload(owner.token(), "notes.txt", "text/plain", "not an image".getBytes(StandardCharsets.UTF_8)), 400);
        assertCode(upload(owner.token(), "cover.gif", "image/gif", "GIF89a".getBytes(StandardCharsets.US_ASCII)), 400);
        assertCode(upload(owner.token(), "cover.png", "image/png", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)), 400);
        assertCode(upload(owner.token(), "cover.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10}), 400);
        assertCode(upload(owner.token(), "cover.jpg", "image/jpeg", image("png")), 400);
        verifyNoInteractions(uploader);
    }

    @Test
    void fileOverTenMiBReturns413WithoutCallingCloudinary() throws Exception {
        byte[] oversized = new byte[10 * 1024 * 1024 + 1];
        assertCode(upload(owner.token(), "large.png", "image/png", oversized), 413);
        verifyNoInteractions(uploader);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void cloudinaryFailureReturns502WithoutLeakingProviderDetails(CapturedOutput output) throws Exception {
        when(uploader.upload(any(byte[].class), anyMap())).thenThrow(new IOException(
                "test-secret provider failure", new IllegalStateException("test-key test-cloud\nroot failure")));
        var response = upload(owner.token(), "cover.png", "image/png", image("png"));
        assertCode(response, 502);
        assertThat(response.body()).doesNotContain("test-secret", "provider failure");
        assertThat(output.getAll()).contains("Cloudinary upload failed (IOException)",
                "[REDACTED] provider failure", "IllegalStateException: [REDACTED] [REDACTED] root failure")
                .doesNotContain("test-secret", "test-key", "test-cloud");
    }

    @Test
    void cloudinaryResponseMustContainAnHttpsSecureUrl() throws Exception {
        byte[] png = image("png");
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of("url", "http://example.com/cover.png"));
        assertCode(upload(owner.token(), "cover.png", "image/png", png), 502);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of("secure_url", "http://example.com/cover.png"));
        assertCode(upload(owner.token(), "cover.png", "image/png", png), 502);
    }

    @Test
    void missingCloudinaryCredentialsReturn503BeforeProviderCall() throws Exception {
        String originalSecret = cloudinary.config.apiSecret;
        cloudinary.config.apiSecret = " ";
        try {
            assertCode(upload(owner.token(), "cover.png", "image/png", image("png")), 503);
            verifyNoInteractions(uploader);
        } finally {
            cloudinary.config.apiSecret = originalSecret;
        }
    }

    @Test
    void uploadedImageUrlIsSavedThroughExistingBookCreateAndEditApis() throws Exception {
        var category = categories.save(DanhMuc.builder().tenDanhMuc("Image upload " + UUID.randomUUID()).build());
        var uploaded = upload(owner.token(), "cover.png", "image/png", image("png"));
        assertCode(uploaded, 200);
        String imageUrl = json(uploaded).get("data").get("hinhAnhUrl").asText();
        var created = request("POST", "/api/store/me/sach", owner.token(), Map.of(
                "maDM", category.getMaDM(), "tenSach", "Book with uploaded cover", "giaBanCu", 75000,
                "doMoiPercent", 80, "soLuongTon", 3, "hinhAnhUrl", imageUrl));
        assertCode(created, 200);
        JsonNode book = json(created).get("data");
        assertThat(book.get("hinhAnhUrl").asText()).isEqualTo(imageUrl);

        String replacementUrl = SECURE_URL.replace("cover.png", "replacement.jpg");
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of("secure_url", replacementUrl));
        var replacement = upload(owner.token(), "replacement.jpg", "image/jpeg", image("jpg"));
        assertCode(replacement, 200);
        var edited = request("PUT", "/api/store/me/sach/" + book.get("maSach").asInt(), owner.token(), Map.of(
                "maDM", category.getMaDM(), "tenSach", "Book with updated cover", "giaBanCu", 75000,
                "doMoiPercent", 80, "hinhAnhUrl", json(replacement).get("data").get("hinhAnhUrl").asText()));
        assertCode(edited, 200);
        assertThat(json(edited).get("data").get("hinhAnhUrl").asText()).isEqualTo(replacementUrl);
        assertThat(json(edited).get("data").get("soLuongTon").asInt()).isEqualTo(3);
    }

    @Test
    void avatarUploadRequiresAuthenticationBeforeCallingCloudinary() throws Exception {
        byte[] png = image("png");
        assertCode(uploadTo("/api/profile/avatar", null, "avatar.png", "image/png", png), 401);
        assertCode(uploadTo("/api/profile/avatar", "invalid-token", "avatar.png", "image/png", png), 401);
        verifyNoInteractions(uploader);
    }

    @Test
    void customerUploadsAvatarAndSavesItsUrlWithoutLosingExistingProfileData() throws Exception {
        Actor customer = actor("KHACH_HANG");
        var user = users.findById(customer.maND()).orElseThrow();
        user.setHoTen("Existing customer name");
        user.setTieuSu("Existing customer biography");
        user.setAvatarUrl("https://example.com/existing-avatar.png");
        users.save(user);
        String avatarUrl = SECURE_URL.replace("oldbook/books/cover.png", "oldbook/avatars/avatar.png");
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of("secure_url", avatarUrl));

        byte[] png = image("png");
        var uploaded = uploadTo("/api/profile/avatar", customer.token(), "avatar.png", "image/png", png);
        assertCode(uploaded, 200);
        assertThat(json(uploaded).get("data").get("avatarUrl").asText()).isEqualTo(avatarUrl);
        ArgumentCaptor<Map> options = ArgumentCaptor.forClass(Map.class);
        verify(uploader).upload(eq(png), options.capture());
        assertThat(options.getValue()).containsEntry("folder", "oldbook/avatars")
                .containsEntry("resource_type", "image");

        var beforeSave = request("GET", "/api/profile", customer.token(), null);
        assertCode(beforeSave, 200);
        assertThat(json(beforeSave).get("data").get("avatarUrl").asText()).isEqualTo(user.getAvatarUrl());
        var saved = request("PUT", "/api/profile", customer.token(), Map.of(
                "hoTen", user.getHoTen(), "soDienThoai", user.getSoDienThoai(),
                "tieuSu", user.getTieuSu(), "avatarUrl", json(uploaded).get("data").get("avatarUrl").asText()));
        assertCode(saved, 200);
        var persisted = users.findById(customer.maND()).orElseThrow();
        assertThat(persisted.getAvatarUrl()).isEqualTo(avatarUrl);
        assertThat(persisted.getHoTen()).isEqualTo(user.getHoTen());
        assertThat(persisted.getSoDienThoai()).isEqualTo(user.getSoDienThoai());
        assertThat(persisted.getTieuSu()).isEqualTo(user.getTieuSu());
        assertThat(persisted.getEmail()).isEqualTo(user.getEmail());
        assertThat(persisted.getMatKhau()).isEqualTo(user.getMatKhau());
    }

    @Test
    void avatarValidationAndProviderFailuresUseStructuredErrorResponses() throws Exception {
        Actor customer = actor("KHACH_HANG");
        assertCode(uploadTo("/api/profile/avatar", customer.token(), null, null, null), 400);
        assertCode(uploadTo("/api/profile/avatar", customer.token(), "empty.png", "image/png", new byte[0]), 400);
        assertCode(uploadTo("/api/profile/avatar", customer.token(), "forged.png", "image/png",
                "not an image".getBytes(StandardCharsets.UTF_8)), 400);
        verifyNoInteractions(uploader);

        when(uploader.upload(any(byte[].class), anyMap())).thenThrow(new IOException("test-secret avatar provider failure"));
        var failed = uploadTo("/api/profile/avatar", customer.token(), "avatar.png", "image/png", image("png"));
        assertCode(failed, 502);
        assertThat(failed.body()).doesNotContain("test-secret", "avatar provider failure");
    }

    private Actor actor(String role) {
        NguoiDung user = users.save(NguoiDung.builder().email("image-upload-" + UUID.randomUUID() + "@test.local")
                .matKhau("test-only-hash").soDienThoai(String.format("08%08d", PHONE_SEQUENCE.incrementAndGet())).build());
        TaiKhoan account = accounts.save(TaiKhoan.builder().nguoiDung(user).vaiTro(role).trangThai("HOAT_DONG").build());
        return new Actor(user.getMaND(), jwtService.generateToken(user.getMaND(), account.getMaTK(), role));
    }

    private byte[] image(String format) throws IOException {
        var output = new ByteArrayOutputStream();
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xA36A43);
        assertThat(ImageIO.write(image, format, output)).isTrue();
        return output.toByteArray();
    }

    private HttpResponse<String> upload(String token, String filename, String contentType, byte[] file) throws Exception {
        return uploadTo(UPLOAD_PATH, token, filename, contentType, file);
    }

    private HttpResponse<String> uploadTo(String path, String token, String filename, String contentType, byte[] file) throws Exception {
        String boundary = "oldbook-upload-test-" + UUID.randomUUID();
        var multipart = new ByteArrayOutputStream();
        if (file != null) {
            multipart.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                    + filename + "\"\r\nContent-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            multipart.write(file);
            multipart.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        } else {
            multipart.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"description\"\r\n\r\nMissing file\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
        }
        multipart.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "multipart/form-data; boundary=" + boundary);
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return client.send(builder.POST(HttpRequest.BodyPublishers.ofByteArray(multipart.toByteArray())).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> request(String method, String path, String token, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        var request = builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(HttpResponse<String> response) {
        return mapper.readTree(response.body());
    }

    private void assertCode(HttpResponse<String> response, int code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(code);
        assertThat(json(response).get("code").asInt()).isEqualTo(code);
    }

    private record Actor(Integer maND, String token) { }
}
