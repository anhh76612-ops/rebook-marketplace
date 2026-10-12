package com.oldbook.payment;

import com.oldbook.config.payment.PaymentProperties;
import com.oldbook.service.payment.CassoSignatureVerifier;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chữ ký mong đợi ở đây được tính độc lập từ chuỗi đã sắp khóa viết tay, không gọi lại code đang được thử,
 * nên test bắt được cả lỗi sắp khóa lẫn lỗi dựng lại JSON.
 */
class CassoSignatureVerifierTest {

    private static final String KHOA = "khoa-bao-mat";
    private static final String THOI_GIAN = "1727948258788";

    // Khóa cố ý không theo thứ tự chữ cái, có đối tượng lồng, số nguyên, chuỗi rỗng và chữ tiếng Việt.
    private static final String NOI_DUNG =
            "{\"error\":0,\"data\":{\"id\":123,\"reference\":\"FT1\",\"description\":\"OB23456789 mua sách cũ\","
                    + "\"amount\":599000,\"virtualAccountName\":\"\",\"accountNumber\":\"88888888\"}}";

    private static final String DA_SAP_XEP =
            "{\"data\":{\"accountNumber\":\"88888888\",\"amount\":599000,\"description\":\"OB23456789 mua sách cũ\","
                    + "\"id\":123,\"reference\":\"FT1\",\"virtualAccountName\":\"\"},\"error\":0}";

    @Test
    void acceptsTheSignatureCassoWouldSend() throws Exception {
        String chuKy = hmacSha512(KHOA, THOI_GIAN + "." + DA_SAP_XEP);

        assertThat(verifier(KHOA).hopLe("t=" + THOI_GIAN + ",v1=" + chuKy, NOI_DUNG)).isTrue();
        // Thứ tự hai phần trong header và chữ hoa của mã hex không quan trọng.
        assertThat(verifier(KHOA).hopLe("v1=" + chuKy.toUpperCase() + ", t=" + THOI_GIAN, NOI_DUNG)).isTrue();
    }

    @Test
    void rejectsTamperedContentWrongKeyAndMalformedHeaders() throws Exception {
        String chuKy = hmacSha512(KHOA, THOI_GIAN + "." + DA_SAP_XEP);
        String header = "t=" + THOI_GIAN + ",v1=" + chuKy;

        assertThat(verifier(KHOA).hopLe(header, NOI_DUNG.replace("599000", "5990000"))).isFalse();
        assertThat(verifier(KHOA).hopLe("t=1727948258789,v1=" + chuKy, NOI_DUNG)).isFalse();
        assertThat(verifier("khoa-khac").hopLe(header, NOI_DUNG)).isFalse();
        assertThat(verifier(KHOA).hopLe(null, NOI_DUNG)).isFalse();
        assertThat(verifier(KHOA).hopLe("v1=" + chuKy, NOI_DUNG)).isFalse();
        assertThat(verifier(KHOA).hopLe(header, "không phải JSON")).isFalse();
    }

    @Test
    void rejectsEverythingWhenNoKeyIsConfigured() throws Exception {
        String chuKy = hmacSha512("", THOI_GIAN + "." + DA_SAP_XEP);

        assertThat(verifier("").hopLe("t=" + THOI_GIAN + ",v1=" + chuKy, NOI_DUNG)).isFalse();
        assertThat(verifier(null).hopLe("t=" + THOI_GIAN + ",v1=" + chuKy, NOI_DUNG)).isFalse();
    }

    private static CassoSignatureVerifier verifier(String khoa) {
        PaymentProperties properties = new PaymentProperties();
        properties.getCasso().setChecksumKey(khoa);
        return new CassoSignatureVerifier(properties);
    }

    private static String hmacSha512(String khoa, String chuoi) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        // Khóa rỗng không tạo được SecretKeySpec; dùng một byte để vẫn có chữ ký "hợp lệ về hình thức".
        byte[] key = khoa.isEmpty() ? new byte[]{0} : khoa.getBytes(StandardCharsets.UTF_8);
        mac.init(new SecretKeySpec(key, "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(chuoi.getBytes(StandardCharsets.UTF_8)));
    }
}
