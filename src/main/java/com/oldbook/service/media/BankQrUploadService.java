package com.oldbook.service.media;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Tải ảnh mã QR ngân hàng (khách nhận hoàn tiền, cửa hàng nhận doanh thu) lên Cloudinary. Tối đa 5 MB. */
@Service
@RequiredArgsConstructor
public class BankQrUploadService {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final String FOLDER = "oldbook/bank-qr";

    private final ImageUploadService imageUploadService;

    public String upload(MultipartFile file) {
        if (file != null && file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Ảnh mã QR không được vượt quá 5 MB");
        }
        return imageUploadService.upload(file, FOLDER);
    }
}
