package com.oldbook.catalog;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.oldbook.entity.catalog.CuaHang;
import com.oldbook.entity.catalog.DanhMuc;
import com.oldbook.entity.catalog.Sach;
import com.oldbook.entity.identity.NguoiDung;
import com.oldbook.entity.identity.TaiKhoan;
import com.oldbook.entity.order.ChiTietDonHang;
import com.oldbook.entity.order.DonHang;
import com.oldbook.entity.order.DonHangCuaHang;
import com.oldbook.entity.system.DanhGia;
import com.oldbook.repository.system.DanhGiaRepository;
import com.oldbook.service.auth.EmailService;
import com.oldbook.service.auth.JwtService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=",
                "spring.datasource.url=jdbc:h2:mem:review_media_${random.uuid};MODE=MSSQLServer;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
                "cloudinary.cloud-name=test-cloud",
                "cloudinary.api-key=test-key",
                "cloudinary.api-secret=test-secret"
        })
@ActiveProfiles({"test", "catalog-test"})
class ReviewMediaIntegrationTest {

    private static final String IMAGE_URL = "https://res.cloudinary.com/test-cloud/image/upload/oldbook/reviews/book.png";
    private static final String VIDEO_URL = "https://res.cloudinary.com/test-cloud/video/upload/oldbook/reviews/book.mp4";
    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger();

    @Value("${local.server.port}") private int port;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwtService;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;
    @MockitoBean private EmailService emailService;
    @MockitoSpyBean private Cloudinary cloudinary;
    @MockitoSpyBean private DanhGiaRepository reviews;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private Uploader uploader;
    private Actor customer;
    private Actor otherCustomer;
    private Actor owner;
    private Integer storeId;
    private Integer categoryId;
    private final List<String> uploadedPublicIds = new ArrayList<>();

    @BeforeEach
    void seed() throws IOException {
        transactions.executeWithoutResult(status -> {
            customer = actor("KHACH_HANG");
            otherCustomer = actor("KHACH_HANG");
            owner = actor("CHU_CUA_HANG");
            storeId = persist(CuaHang.builder()
                    .chuShop(entityManager.getReference(NguoiDung.class, owner.maND()))
                    .tenCuaHang("Media review shop " + UUID.randomUUID()).trangThaiDuyet("DA_DUYET").build()).getMaCH();
            categoryId = persist(DanhMuc.builder().tenDanhMuc("Media review category " + UUID.randomUUID()).build())
                    .getMaDM();
        });
        uploader = mock(Uploader.class);
        uploadedPublicIds.clear();
        doReturn(uploader).when(cloudinary).uploader();
        when(uploader.upload(any(byte[].class), anyMap()))
                .thenAnswer(call -> uploadResult(call.getArgument(1), "image", "png", IMAGE_URL));
        when(uploader.destroy(any(String.class), anyMap())).thenReturn(Map.of("result", "ok"));
    }

    @Test
    void deliveredBuyerUploadsImageAndVideoAndBothPersistAndRenderOnBookAndOrders() throws Exception {
        Line line = line(customer, "DA_GIAO");
        when(uploader.upload(any(byte[].class), anyMap()))
                .thenAnswer(call -> uploadResult(call.getArgument(1), "image", "png", IMAGE_URL))
                .thenAnswer(call -> uploadResult(call.getArgument(1), "video", "mp4", VIDEO_URL));
        byte[] png = image("png");
        byte[] mp4 = mp4();

        JsonNode created = data(upload(line, customer.token(), "5", "Book matches description",
                List.of(new FilePart("book.png", "image/png", png), new FilePart("book.mp4", "video/mp4", mp4))));

        assertThat(created.get("media").size()).isEqualTo(2);
        assertMedia(created.get("media").get(0), "IMAGE", IMAGE_URL);
        assertMedia(created.get("media").get(1), "VIDEO", VIDEO_URL);
        assertThat(mediaCount(line)).isEqualTo(2L);
        assertThat(jdbc.queryForList("select loai_media from danh_gia_media where ma_danh_gia = ? order by ma_media",
                String.class, created.get("maDanhGia").asInt())).containsExactly("IMAGE", "VIDEO");
        assertThat(jdbc.queryForList("select media_url from danh_gia_media where ma_danh_gia = ? order by ma_media",
                String.class, created.get("maDanhGia").asInt())).containsExactly(IMAGE_URL, VIDEO_URL);
        ArgumentCaptor<Map> options = ArgumentCaptor.forClass(Map.class);
        verify(uploader, times(2)).upload(any(byte[].class), options.capture());
        assertThat(options.getAllValues().get(0)).containsEntry("resource_type", "image")
                .containsEntry("overwrite", false);
        assertThat(options.getAllValues().get(1)).containsEntry("resource_type", "video")
                .containsEntry("overwrite", false);
        assertThat(options.getAllValues()).allSatisfy(option ->
                assertThat(option.get("folder").toString()).startsWith("oldbook/reviews/"));
        assertThat(options.getAllValues()).allSatisfy(option -> {
            assertThat(option.get("folder")).isEqualTo("oldbook/reviews/" + line.maCTDH());
            assertThat(UUID.fromString(option.get("public_id").toString())).isNotNull();
            assertThat(option).doesNotContainKey("asset_folder");
        });
        assertThat(jdbc.queryForList("select cloudinary_public_id from danh_gia_media where ma_danh_gia = ? order by ma_media",
                String.class, created.get("maDanhGia").asInt())).containsExactlyElementsOf(uploadedPublicIds);
        for (String path : List.of("/api/order/orders", "/api/order/orders/" + line.maDH())) {
            JsonNode payload = data(get(path, customer.token()));
            JsonNode order = payload.isArray() ? findById(payload, "maDH", line.maDH()) : payload;
            JsonNode item = findById(findById(order.get("storeOrders"), "maDHCH", line.maDHCH()).get("items"),
                    "maCTDH", line.maCTDH());
            assertThat(item.get("danhGia").get("media")).isEqualTo(created.get("media"));
            assertThat(item.get("coTheDanhGia").asBoolean()).isFalse();
        }
        var page = get("/books/" + line.maSach(), null);
        assertCode(page, 200);
        assertThat(page.body()).contains("<img", IMAGE_URL, "<video", "controls", VIDEO_URL);
        assertThat(created.toString()).doesNotContain("test-key", "test-secret", "public_id");
    }

    @Test
    void attachmentsAreOptionalForJsonAndMultipartReviews() throws Exception {
        Line jsonLine = line(customer, "DA_GIAO");
        var jsonReview = data(jsonPost(jsonLine, Map.of("soSao", 4, "binhLuan", "Without attachments")));
        assertThat(jsonReview.get("media").isArray()).isTrue();
        assertThat(jsonReview.get("media").isEmpty()).isTrue();
        Line multipartLine = line(customer, "DA_GIAO");
        var multipartReview = data(upload(multipartLine, customer.token(), "4", "No selected files", List.of()));
        assertThat(multipartReview.get("media").isArray()).isTrue();
        assertThat(multipartReview.get("media").isEmpty()).isTrue();
        verifyNoInteractions(uploader);
    }

    @Test
    void realJpegAndWebmContainersAreAccepted() throws Exception {
        Line line = line(customer, "DA_GIAO");
        String jpegUrl = IMAGE_URL.replace(".png", ".jpg");
        String webmUrl = VIDEO_URL.replace(".mp4", ".webm");
        when(uploader.upload(any(byte[].class), anyMap()))
                .thenAnswer(call -> uploadResult(call.getArgument(1), "image", "jpg", jpegUrl))
                .thenAnswer(call -> uploadResult(call.getArgument(1), "video", "webm", webmUrl));
        byte[] webm = new byte[] {0x1a, 0x45, (byte) 0xdf, (byte) 0xa3, (byte) 0x87,
                0x42, (byte) 0x82, (byte) 0x84, 'w', 'e', 'b', 'm'};
        JsonNode review = data(upload(line, customer.token(), "4", "Photographed and filmed book", List.of(
                new FilePart("book.jpg", "image/jpeg", image("jpg")), new FilePart("book.webm", "video/webm", webm))));
        assertMedia(review.get("media").get(0), "IMAGE", jpegUrl);
        assertMedia(review.get("media").get(1), "VIDEO", webmUrl);
    }

    @Test
    void unsafeLegacyMediaRowsAreExcludedFromOrderDtoAndPublicBookMarkup() throws Exception {
        Line line = line(customer, "DA_GIAO");
        int reviewId = data(jsonPost(line, Map.of("soSao", 4, "binhLuan", "Existing review")))
                .get("maDanhGia").asInt();
        for (String unsafe : List.of("javascript:alert(99)", "https://example.com/unsafe-review.png",
                "https://res.cloudinary.com.evil.test/unsafe-review.png")) {
            jdbc.update("insert into danh_gia_media (ma_danh_gia, loai_media, media_url) values (?, ?, ?)",
                    reviewId, "IMAGE", unsafe);
        }
        jdbc.update("insert into danh_gia_media (ma_danh_gia, loai_media, media_url) values (?, ?, ?)",
                reviewId, "OTHER", IMAGE_URL);
        JsonNode order = data(get("/api/order/orders/" + line.maDH(), customer.token()));
        JsonNode item = findById(findById(order.get("storeOrders"), "maDHCH", line.maDHCH()).get("items"),
                "maCTDH", line.maCTDH());
        assertThat(item.get("danhGia").get("media").isEmpty()).isTrue();
        var page = get("/books/" + line.maSach(), null);
        assertCode(page, 200);
        assertThat(page.body()).contains("Existing review")
                .doesNotContain("javascript:alert(99)", "unsafe-review.png", IMAGE_URL);
    }

    @Test
    void authenticationOwnershipDeliveryAndDuplicateChecksPrecedeCloudinary() throws Exception {
        Line delivered = line(customer, "DA_GIAO");
        List<FilePart> files = List.of(new FilePart("book.png", "image/png", image("png")));
        assertCode(upload(delivered, null, "5", "Review", files), 401);
        assertCode(upload(delivered, "invalid-token", "5", "Review", files), 401);
        assertCode(upload(delivered, jwtService.generateRoleSelectionToken(customer.maND()), "5", "Review", files), 401);
        assertCode(upload(delivered, owner.token(), "5", "Review", files), 403);
        assertCode(upload(delivered, otherCustomer.token(), "5", "Review", files), 404);
        assertCode(upload(new Line(0, 0, Integer.MAX_VALUE, 0), customer.token(), "5", "Review", files), 404);
        for (String status : List.of("CHO_XU_LY", "DANG_GIAO", "DA_HUY", "DA_HOAN_TRA")) {
            Line pending = line(customer, status);
            assertCode(upload(pending, customer.token(), "5", "Review", files), 409);
            assertThat(reviewCount(pending)).isZero();
        }
        data(jsonPost(delivered, Map.of("soSao", 5, "binhLuan", "Existing review")));
        assertCode(upload(delivered, customer.token(), "5", "Duplicate", files), 409);
        assertThat(reviewCount(delivered)).isEqualTo(1L);
        assertThat(mediaCount(delivered)).isZero();
        verifyNoInteractions(uploader);
    }

    @Test
    void invalidMultipartRatingsAndCommentsAreRejectedBeforeCloudinary() throws Exception {
        Line line = line(customer, "DA_GIAO");
        List<FilePart> files = List.of(new FilePart("book.png", "image/png", image("png")));
        for (String stars : List.of("0", "6", "4.5", "abc", "")) {
            assertCode(upload(line, customer.token(), stars, "Review", files), 400);
        }
        assertCode(upload(line, customer.token(), null, "Review", files), 400);
        for (String comment : List.of("", " \t\n ", "x".repeat(2001))) {
            assertCode(upload(line, customer.token(), "5", comment, files), 400);
        }
        assertCode(upload(line, customer.token(), "5", null, files), 400);
        assertThat(reviewCount(line)).isZero();
        verifyNoInteractions(uploader);
    }

    @Test
    void invalidFilesAndEveryFileInBatchAreValidatedBeforeAnyUpload() throws Exception {
        Line line = line(customer, "DA_GIAO");
        var png = new FilePart("book.png", "image/png", image("png"));
        List<FilePart> invalid = List.of(
                new FilePart("empty.png", "image/png", new byte[0]),
                new FilePart("notes.txt", "text/plain", "notes".getBytes(StandardCharsets.UTF_8)),
                new FilePart("image.gif", "image/gif", "GIF89a".getBytes(StandardCharsets.US_ASCII)),
                new FilePart("forged.png", "image/png", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)),
                new FilePart("truncated.png", "image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10}),
                new FilePart("wrong.jpg", "image/jpeg", png.content()),
                new FilePart("wrong.png", "video/mp4", png.content()),
                new FilePart("fake.mp4", "video/mp4", "not a video".getBytes(StandardCharsets.UTF_8)),
                new FilePart("fake.webm", "video/webm", new byte[] {0x1a, 0x45, (byte) 0xdf, (byte) 0xa3}),
                new FilePart("wrong.mp4", "image/png", mp4()));
        for (FilePart file : invalid) {
            assertCode(upload(line, customer.token(), "5", "Review", List.of(png, file)), 400);
        }
        assertCode(upload(line, customer.token(), "5", "Review", Collections.nCopies(6, png)), 400);
        assertThat(reviewCount(line)).isZero();
        verifyNoInteractions(uploader);
    }

    @Test
    void imageVideoAndCombinedBatchSizeLimitsReturn413BeforeProviderCall() throws Exception {
        Line line = line(customer, "DA_GIAO");
        assertCode(upload(line, customer.token(), "5", "Review",
                List.of(new FilePart("large.png", "image/png", new byte[10 * 1024 * 1024 + 1]))), 413);
        assertCode(upload(line, customer.token(), "5", "Review",
                List.of(new FilePart("large.mp4", "video/mp4", new byte[50 * 1024 * 1024 + 1]))), 413);
        byte[] video = new byte[30 * 1024 * 1024 + 1];
        System.arraycopy(mp4(), 0, video, 0, mp4().length);
        assertCode(upload(line, customer.token(), "5", "Review", List.of(
                new FilePart("one.mp4", "video/mp4", video), new FilePart("two.mp4", "video/mp4", video))), 413);
        assertThat(reviewCount(line)).isZero();
        verifyNoInteractions(uploader);
    }

    @Test
    void partialProviderFailureDeletesPriorUploadAndLeavesNoReviewOrMedia() throws Exception {
        Line line = line(customer, "DA_GIAO");
        when(uploader.upload(any(byte[].class), anyMap()))
                .thenAnswer(call -> uploadResult(call.getArgument(1), "image", "png", IMAGE_URL))
                .thenThrow(new IOException("test-secret provider failed"));
        var response = upload(line, customer.token(), "5", "Review", List.of(
                new FilePart("one.png", "image/png", image("png")), new FilePart("two.mp4", "video/mp4", mp4())));
        assertCode(response, 502);
        assertThat(response.body()).doesNotContain("test-secret", "provider failed");
        verify(uploader).destroy(eq(uploadedPublicIds.getFirst()), anyMap());
        assertThat(reviewCount(line)).isZero();
        assertThat(mediaCount(line)).isZero();
    }

    @Test
    void missingOrUnsafeProviderResultIsRejectedAndKnownUploadedAssetIsDeleted() throws Exception {
        Line line = line(customer, "DA_GIAO");
        FilePart png = new FilePart("one.png", "image/png", image("png"));
        List<String> urls = List.of("", "http://res.cloudinary.com/test-cloud/image/upload/book.png",
                "https://example.com/book.png", "https://res.cloudinary.com.evil.test/test-cloud/image/upload/book.png",
                "javascript:alert(1)");
        for (String url : urls) {
            when(uploader.upload(any(byte[].class), anyMap()))
                    .thenAnswer(call -> uploadResult(call.getArgument(1), "image", "png", url));
            assertCode(upload(line, customer.token(), "5", "Review", List.of(png)), 502);
            assertThat(reviewCount(line)).isZero();
        }
        ArgumentCaptor<Map> cleanup = ArgumentCaptor.forClass(Map.class);
        verify(uploader, times(urls.size())).destroy(any(String.class), cleanup.capture());
        assertThat(cleanup.getAllValues()).allSatisfy(options -> assertThat(options).containsEntry("resource_type", "image"));
        when(uploader.upload(any(byte[].class), anyMap())).thenAnswer(call -> {
            uploadResult(call.getArgument(1), "image", "png", IMAGE_URL);
            return Map.of("secure_url", IMAGE_URL);
        });
        assertCode(upload(line, customer.token(), "5", "Review", List.of(png)), 502);
        verify(uploader).destroy(eq(uploadedPublicIds.getLast()), anyMap());
        assertThat(mediaCount(line)).isZero();
    }

    @Test
    void unavailableCloudinaryCredentialsAreCheckedOnlyWhenFilesWereSelected() throws Exception {
        String originalSecret = cloudinary.config.apiSecret;
        cloudinary.config.apiSecret = " ";
        try {
            Line line = line(customer, "DA_GIAO");
            assertCode(upload(line, customer.token(), "5", "Review",
                    List.of(new FilePart("book.png", "image/png", image("png")))), 503);
            assertThat(reviewCount(line)).isZero();
            assertCode(upload(line, customer.token(), "5", "Review without files", List.of()), 200);
            verifyNoInteractions(uploader);
        } finally {
            cloudinary.config.apiSecret = originalSecret;
        }
    }

    @Test
    void databaseFailureAfterUploadCleansCloudinaryAndRollsBackReview() throws Exception {
        Line line = line(customer, "DA_GIAO");
        doThrow(new DataIntegrityViolationException("simulated database failure")).when(reviews).saveAndFlush(any(DanhGia.class));
        var response = upload(line, customer.token(), "5", "Review",
                List.of(new FilePart("book.png", "image/png", image("png"))));
        assertThat(response.statusCode()).as(response.body()).isBetween(400, 599);
        verify(uploader).destroy(eq(uploadedPublicIds.getFirst()), anyMap());
        assertThat(reviewCount(line)).isZero();
        assertThat(mediaCount(line)).isZero();
    }

    @Test
    void concurrentAttachmentSubmissionsUploadAndCreateOnlyOneReview() throws Exception {
        Line line = line(customer, "DA_GIAO");
        HttpRequest request = multipartRequest(line, customer.token(), "5", "Concurrent review",
                List.of(new FilePart("book.png", "image/png", image("png"))));
        var first = client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        var second = client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        var firstResponse = first.get(30, TimeUnit.SECONDS);
        var secondResponse = second.get(30, TimeUnit.SECONDS);
        assertThat(List.of(firstResponse.statusCode(), secondResponse.statusCode()))
                .as(firstResponse.body() + " / " + secondResponse.body()).containsExactlyInAnyOrder(200, 409);
        assertThat(reviewCount(line)).isEqualTo(1L);
        assertThat(mediaCount(line)).isEqualTo(1L);
        verify(uploader).upload(any(byte[].class), anyMap());
    }

    private Map<String, Object> uploadResult(Map<?, ?> options, String resourceType, String format, String url) {
        String id = options.get("folder") + "/" + options.get("public_id");
        uploadedPublicIds.add(id);
        return Map.of("resource_type", resourceType, "format", format, "public_id", id,
                "secure_url", url);
    }

    private void assertMedia(JsonNode media, String type, String url) {
        assertThat(media.get("maMedia").asInt()).isPositive();
        assertThat(media.get("loaiMedia").asString()).isEqualTo(type);
        assertThat(media.get("mediaUrl").asString()).isEqualTo(url);
    }

    private Actor actor(String role) {
        var user = persist(NguoiDung.builder().email("review-media-" + UUID.randomUUID() + "@test.local")
                .matKhau("test-only-hash").hoTen("Media reviewer")
                .soDienThoai(String.format("06%08d", PHONE_SEQUENCE.incrementAndGet())).build());
        var account = persist(TaiKhoan.builder().nguoiDung(user).vaiTro(role).trangThai("HOAT_DONG").build());
        return new Actor(user.getMaND(), jwtService.generateToken(user.getMaND(), account.getMaTK(), role));
    }

    private Line line(Actor buyer, String status) {
        return transactions.execute(transaction -> {
            var store = entityManager.getReference(CuaHang.class, storeId);
            var book = persist(Sach.builder().cuaHang(store).danhMuc(entityManager.getReference(DanhMuc.class, categoryId))
                    .tenSach("Purchased book with media").giaBanCu(new BigDecimal("50000"))
                    .giaBia(new BigDecimal("75000")).soLuongTon(3).trangThaiDuyet("DA_DUYET")
                    .trangThaiBan("DANG_BAN").build());
            var parent = persist(DonHang.builder().nguoiMua(entityManager.getReference(NguoiDung.class, buyer.maND()))
                    .tongTienHang(new BigDecimal("50000")).tongPhiShip(BigDecimal.ZERO).tongGiamGia(BigDecimal.ZERO)
                    .tongThanhToan(new BigDecimal("50000")).phuongThucTT("COD").trangThaiThanhToan("DA_THANH_TOAN")
                    .tenNguoiNhan("Buyer").soDienThoaiNhan("0900000000").diaChiGiaoChiTiet("1 Test Street").build());
            var child = persist(DonHangCuaHang.builder().donHang(parent).cuaHang(store)
                    .tienHangCH(new BigDecimal("50000")).phiShipCH(BigDecimal.ZERO)
                    .tongTienShopOrder(new BigDecimal("50000")).trangThaiDonHang(status)
                    .trangThaiThanhToanShop("DA_THANH_TOAN")
                    .ngayGiao("DA_GIAO".equals(status) ? LocalDateTime.now().minusHours(1) : null).build());
            var item = persist(ChiTietDonHang.builder().donHangCuaHang(child).sach(book).soLuong(1)
                    .donGia(new BigDecimal("50000")).thanhTien(new BigDecimal("50000"))
                    .tyLeChietKhau(BigDecimal.ZERO).tienChietKhau(BigDecimal.ZERO).build());
            return new Line(parent.getMaDH(), child.getMaDHCH(), item.getMaCTDH(), book.getMaSach());
        });
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    private long reviewCount(Line line) {
        return jdbc.queryForObject("select count(*) from danh_gia where ma_ctdh = ?", Long.class, line.maCTDH());
    }

    private long mediaCount(Line line) {
        return jdbc.queryForObject("select count(*) from danh_gia_media m join danh_gia d on m.ma_danh_gia = d.ma_danh_gia where d.ma_ctdh = ?",
                Long.class, line.maCTDH());
    }

    private byte[] image(String format) throws IOException {
        var output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, output)).isTrue();
        return output.toByteArray();
    }

    private byte[] mp4() {
        return ByteBuffer.allocate(40).putInt(24).put("ftypisom".getBytes(StandardCharsets.US_ASCII))
                .putInt(0).put("isommp42".getBytes(StandardCharsets.US_ASCII))
                .putInt(8).put("moov".getBytes(StandardCharsets.US_ASCII))
                .putInt(8).put("mdat".getBytes(StandardCharsets.US_ASCII)).array();
    }

    private JsonNode findById(JsonNode items, String field, Integer id) {
        for (JsonNode item : items) {
            if (item.get(field).asInt() == id) return item;
        }
        throw new AssertionError("Missing " + field + "=" + id + " in " + items);
    }

    private HttpRequest multipartRequest(Line line, String token, String stars, String comment, List<FilePart> files) throws IOException {
        String boundary = "review-media-test-" + UUID.randomUUID();
        var multipart = new ByteArrayOutputStream();
        if (stars != null) field(multipart, boundary, "soSao", stars);
        if (comment != null) field(multipart, boundary, "binhLuan", comment);
        for (FilePart file : files) {
            multipart.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\""
                    + file.name() + "\"\r\nContent-Type: " + file.type() + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            multipart.write(file.content());
            multipart.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        }
        multipart.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/reviews/order-items/" + line.maCTDH()))
                .timeout(Duration.ofSeconds(30)).header("Content-Type", "multipart/form-data; boundary=" + boundary);
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return builder.POST(HttpRequest.BodyPublishers.ofByteArray(multipart.toByteArray())).build();
    }

    private void field(ByteArrayOutputStream body, String boundary, String name, String value) throws IOException {
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name
                + "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> upload(Line line, String token, String stars, String comment, List<FilePart> files) throws Exception {
        return client.send(multipartRequest(line, token, stars, comment, files), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> jsonPost(Line line, Object body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/reviews/order-items/" + line.maCTDH()))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + customer.token())
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(15));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private JsonNode data(HttpResponse<String> response) {
        assertCode(response, 200);
        return mapper.readTree(response.body()).get("data");
    }

    private void assertCode(HttpResponse<String> response, int code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(code);
        if (response.headers().firstValue("Content-Type").orElse("").contains("json")) {
            assertThat(mapper.readTree(response.body()).get("code").asInt()).isEqualTo(code);
        }
    }

    private record Actor(Integer maND, String token) { }
    private record Line(Integer maDH, Integer maDHCH, Integer maCTDH, Integer maSach) { }
    private record FilePart(String name, String type, byte[] content) { }
}
