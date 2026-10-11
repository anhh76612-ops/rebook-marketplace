package com.oldbook.service.catalog;

import com.oldbook.constant.order.TrangThaiDonHang;
import com.oldbook.dto.catalog.CreateDanhGiaRequest;
import com.oldbook.dto.catalog.DanhGiaResponse;
import com.oldbook.dto.catalog.DanhGiaMediaResponse;
import com.oldbook.entity.system.DanhGia;
import com.oldbook.entity.system.DanhGiaMedia;
import com.oldbook.repository.order.ChiTietDonHangRepository;
import com.oldbook.repository.system.DanhGiaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class DanhGiaService {
    private final DanhGiaRepository danhGiaRepository;
    private final ChiTietDonHangRepository chiTietDonHangRepository;
    private final ReviewMediaUploadService reviewMediaUploadService;

    public DanhGiaService(DanhGiaRepository danhGiaRepository,
                         ChiTietDonHangRepository chiTietDonHangRepository,
                         ReviewMediaUploadService reviewMediaUploadService) {
        this.danhGiaRepository = danhGiaRepository;
        this.chiTietDonHangRepository = chiTietDonHangRepository;
        this.reviewMediaUploadService = reviewMediaUploadService;
    }

    public List<DanhGiaResponse> getByBook(Integer maSach) {
        return danhGiaRepository.findBySach(maSach).stream()
                .map(DanhGiaService::toResponse)
                .toList();
    }

    @Transactional
    public DanhGiaResponse createReview(Integer maND, Integer maCTDH, CreateDanhGiaRequest request) {
        return createReview(maND, maCTDH, request, List.of());
    }

    @Transactional
    public DanhGiaResponse createReview(Integer maND, Integer maCTDH, CreateDanhGiaRequest request,
                                        List<MultipartFile> files) {
        // Lock the purchased line so concurrent submissions cannot both create its review.
        var item = chiTietDonHangRepository.findByIdForUpdate(maCTDH)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy sách trong đơn hàng"));
        var storeOrder = item.getDonHangCuaHang();
        var customer = storeOrder.getDonHang().getNguoiMua();
        if (maND == null || !Objects.equals(customer.getMaND(), maND)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sách trong đơn hàng");
        }
        if (!TrangThaiDonHang.DA_GIAO.name().equals(storeOrder.getTrangThaiDonHang())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Chỉ được đánh giá sau khi sách đã giao thành công");
        }
        if (danhGiaRepository.existsByChiTietDonHang_MaCTDH(maCTDH)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bạn đã đánh giá sách trong đơn hàng này");
        }

        var uploaded = reviewMediaUploadService.upload(files, maCTDH);
        boolean cleanupOnCompletion = !uploaded.isEmpty()
                && TransactionSynchronizationManager.isSynchronizationActive();
        if (cleanupOnCompletion) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        reviewMediaUploadService.cleanup(uploaded);
                    }
                }
            });
        }

        try {
            var review = DanhGia.builder()
                .chiTietDonHang(item)
                .nguoiDung(customer)
                .soSao(request.soSao())
                .binhLuan(request.binhLuan().strip())
                .build();
            for (var asset : uploaded) {
                review.getMedia().add(DanhGiaMedia.builder()
                        .danhGia(review).loaiMedia(asset.loaiMedia()).mediaUrl(asset.mediaUrl())
                        .cloudinaryPublicId(asset.publicId()).build());
            }
            return toResponse(danhGiaRepository.saveAndFlush(review));
        } catch (RuntimeException exception) {
            if (!cleanupOnCompletion) {
                reviewMediaUploadService.cleanup(uploaded);
            }
            throw exception;
        }
    }

    public static DanhGiaResponse toResponse(DanhGia review) {
        String hoTen = review.getNguoiDung().getHoTen();
        return new DanhGiaResponse(review.getMaDanhGia(),
                hoTen == null || hoTen.isBlank() ? "Khách hàng" : hoTen,
                review.getSoSao(), review.getBinhLuan(), review.getNgayDanhGia(),
                review.getMedia() == null ? List.of() : review.getMedia().stream()
                        .filter(media -> "IMAGE".equals(media.getLoaiMedia()) || "VIDEO".equals(media.getLoaiMedia()))
                        .filter(media -> ReviewMediaUploadService.isValidMediaUrl(media.getMediaUrl()))
                        .map(media -> new DanhGiaMediaResponse(media.getMaMedia(), media.getLoaiMedia(), media.getMediaUrl()))
                        .toList());
    }
}
