package com.oldbook.service.shipping;

import com.oldbook.dto.shipping.*;
import com.oldbook.entity.order.DonHangCuaHang;
import com.oldbook.entity.shipping.DonViVanChuyen;
import com.oldbook.entity.shipping.VanChuyen;
import com.oldbook.exception.common.BusinessException;
import com.oldbook.repository.order.DonHangCuaHangRepository;
import com.oldbook.repository.shipping.DonViVanChuyenRepository;
import com.oldbook.repository.shipping.VanChuyenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VanChuyenService {
    private final VanChuyenRepository vanChuyenRepository;
    private final DonViVanChuyenRepository donViRepository;
    private final DonHangCuaHangRepository donHangCuaHangRepository;

    @Transactional
    public VanChuyenResponse tiepNhan(TiepNhanVanChuyenRequest req) {
        if (vanChuyenRepository.findByDonHangCuaHang_MaDHCH(req.getMaDHCH()).isPresent()) {
            throw new BusinessException("Đơn hàng đã có yêu cầu vận chuyển");
        }
        DonHangCuaHang dhch = donHangCuaHangRepository.findById(req.getMaDHCH())
                .orElseThrow(() -> new BusinessException("Đơn hàng cửa hàng không tồn tại"));
        DonViVanChuyen dvvc = donViRepository.findById(req.getMaDVVC())
                .orElseThrow(() -> new BusinessException("Đối tác vận chuyển không tồn tại"));
        if (dvvc.getTrangThai() != null && "NGUNG_HOAT_DONG".equalsIgnoreCase(dvvc.getTrangThai())) {
            throw new BusinessException("Đối tác vận chuyển đang ngừng hoạt động");
        }

        VanChuyen vc = VanChuyen.builder()
                .donHangCuaHang(dhch)
                .donViVanChuyen(dvvc)
                .maVanDon(req.getMaVanDon())
                .trangThaiVanChuyen(req.getTrangThaiVanChuyen() == null ? "DA_TIEP_NHAN" : req.getTrangThaiVanChuyen())
                .soLanGiaoThatBai(0)
                .ghiChuGiaoHang(req.getGhiChuGiaoHang())
                .build();
        return VanChuyenResponse.from(vanChuyenRepository.save(vc));
    }

    @Transactional
    public VanChuyenResponse capNhat(Integer id, CapNhatVanChuyenRequest req) {
        VanChuyen vc = vanChuyenRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Vận chuyển không tồn tại"));
        vc.setTrangThaiVanChuyen(req.getTrangThai());
        if (req.getGhiChu() != null) vc.setGhiChuGiaoHang(req.getGhiChu());
        if (req.getSoLanGiaoThatBai() != null) vc.setSoLanGiaoThatBai(req.getSoLanGiaoThatBai());

        DonHangCuaHang dhch = vc.getDonHangCuaHang();
        String status = req.getTrangThai().toUpperCase();
        if ("GIAO_THANH_CONG".equals(status)) {
            dhch.setTrangThaiDonHang("DA_GIAO");
        } else if ("GIAO_THAT_BAI".equals(status)) {
            dhch.setTrangThaiDonHang("GIAO_THAT_BAI");
        } else if ("HOAN_TRA".equals(status)) {
            dhch.setTrangThaiDonHang("HOAN_TRA");
            if (req.getTienHoanTra() != null) dhch.setTienHoanTra(req.getTienHoanTra());
        }
        if (req.getMaVanDon() != null && !req.getMaVanDon().equals(vc.getMaVanDon())) {
            if (vanChuyenRepository.existsByMaVanDon(req.getMaVanDon())) throw new BusinessException("Mã vận đơn đã tồn tại");
            vc.setMaVanDon(req.getMaVanDon());
        }
        return VanChuyenResponse.from(vanChuyenRepository.save(vc));
    }

    @Transactional(readOnly = true)
    public List<YeuCauVanChuyenResponse> getYeuCauChuaTiepNhan() {
        return donHangCuaHangRepository.findAll().stream()
                .filter(d -> vanChuyenRepository.findByDonHangCuaHang_MaDHCH(d.getMaDHCH()).isEmpty())
                .filter(d -> d.getTrangThaiDonHang() != null)
                .filter(d -> {
                    String status = d.getTrangThaiDonHang().toUpperCase();
                    return !status.equals("DA_GIAO")
                            && !status.equals("HOAN_TRA")
                            && !status.equals("DA_HUY")
                            && !status.equals("HUY");
                })
                .map(d -> YeuCauVanChuyenResponse.builder()
                        .maDHCH(d.getMaDHCH())
                        .maDH(d.getDonHang().getMaDH())
                        .trangThaiDonHang(d.getTrangThaiDonHang())
                        .trangThaiThanhToan(d.getTrangThaiThanhToanShop())
                        .tongTien(d.getTongTienShopOrder())
                        .phiShip(d.getPhiShipCH())
                        .ngayCapNhat(d.getNgayCapNhat())
                        .ghiChu(d.getGhiChu())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VanChuyenResponse> getAll() {
        return vanChuyenRepository.findAll().stream().map(VanChuyenResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public VanChuyenResponse getById(Integer id) {
        return vanChuyenRepository.findById(id).map(VanChuyenResponse::from)
                .orElseThrow(() -> new BusinessException("Vận chuyển không tồn tại"));
    }

    @Transactional(readOnly = true)
    public List<DonViVanChuyenResponse> getPartners() {
        return donViRepository.findAll().stream().map(DonViVanChuyenResponse::from).toList();
    }
}
