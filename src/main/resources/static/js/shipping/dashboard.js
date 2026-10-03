(function () {
  'use strict';

  var ROLE = 'DON_VI_VAN_CHUYEN';
  var shipments = [];
  var partners = [];
  var pending = [];

  function esc(v) { return OldBook.escapeHtml(v == null ? '' : v); }
  function money(v) { return OldBook.formatMoney(v); }
  function date(v) { return v ? new Date(v).toLocaleString('vi-VN') : '-'; }

  function ensureAccess() {
    if (!OldBook.auth.isLoggedIn()) {
      OldBook.auth.redirectToLogin('/shipping');
      return false;
    }
    var user = OldBook.auth.getUser();
    var allowed = ['DON_VI_VAN_CHUYEN', 'QUAN_LY', 'QUAN_TRI_VIEN'].indexOf(user && user.vaiTro) >= 0;
    if (!allowed) {
      var a = document.getElementById('accessAlert');
      a.classList.remove('d-none');
      a.textContent = 'Tài khoản hiện tại không có quyền truy cập trung tâm vận chuyển.';
      return false;
    }
    return true;
  }

  function statusBadge(status) {
    var labels = {
      DA_TIEP_NHAN: 'Đã tiếp nhận',
      DA_LAY_HANG: 'Đã lấy hàng',
      DANG_VAN_CHUYEN: 'Đang vận chuyển',
      GIAO_THANH_CONG: 'Giao thành công',
      GIAO_THAT_BAI: 'Giao thất bại',
      HOAN_TRA: 'Hoàn trả'
    };
    var cls = {
      GIAO_THANH_CONG: 'text-bg-success',
      HOAN_TRA: 'text-bg-warning',
      GIAO_THAT_BAI: 'text-bg-danger',
      DANG_VAN_CHUYEN: 'text-bg-primary'
    }[status] || 'text-bg-secondary';
    return '<span class="badge ' + cls + '">' + esc(labels[status] || status || 'Chưa cập nhật') + '</span>';
  }

  function loadPartners() {
    return OldBook.request('GET', '/api/shipping/partners').then(function (data) {
      partners = data || [];
      renderPartnerOptions();
      return partners;
    });
  }

  function loadPending() {
    return OldBook.request('GET', '/api/shipping/requests/pending').then(function (data) {
      pending = data || [];
      renderPending();
      updateStats();
    });
  }

  function loadShipments() {
    return OldBook.request('GET', '/api/shipping/requests').then(function (data) {
      shipments = data || [];
      renderShipments();
      updateStats();
    });
  }

  function loadAll() {
    Promise.all([loadPartners(), loadPending(), loadShipments()])
      .catch(function (e) { OldBook.toast(e.message, 'danger'); });
  }

  function updateStats() {
    document.getElementById('statPending').textContent = pending.length;
    document.getElementById('statShipping').textContent = shipments.filter(function (x) {
      return ['DA_TIEP_NHAN', 'DA_LAY_HANG', 'DANG_VAN_CHUYEN'].indexOf(x.trangThaiVanChuyen) >= 0;
    }).length;
    document.getElementById('statDelivered').textContent = shipments.filter(function (x) { return x.trangThaiVanChuyen === 'GIAO_THANH_CONG'; }).length;
    document.getElementById('statReturned').textContent = shipments.filter(function (x) { return x.trangThaiVanChuyen === 'HOAN_TRA'; }).length;
  }

  function renderPending() {
    var body = document.getElementById('pendingBody');
    if (!pending.length) {
      body.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-4">Không có yêu cầu chờ tiếp nhận.</td></tr>';
      return;
    }
    body.innerHTML = pending.map(function (x) {
      return '<tr>' +
        '<td><strong>#DHCH' + esc(x.maDHCH) + '</strong><div class="small text-muted">Đơn #' + esc(x.maDH) + '</div></td>' +
        '<td><span class="badge text-bg-light border">' + esc(x.trangThaiDonHang) + '</span></td>' +
        '<td>' + esc(x.trangThaiThanhToan || '-') + '</td>' +
        '<td>' + money(x.tongTien) + '</td>' +
        '<td class="small">' + date(x.ngayCapNhat) + '</td>' +
        '<td class="text-end"><button class="btn btn-accent btn-sm" onclick="Shipping.openAccept(' + x.maDHCH + ',' + x.maDH + ')"><i class="bi bi-box-arrow-in-right me-1"></i>Tiếp nhận</button></td>' +
      '</tr>';
    }).join('');
  }

  function renderShipments() {
    var body = document.getElementById('shipmentBody');
    if (!shipments.length) {
      body.innerHTML = '<tr><td colspan="7" class="text-center text-muted py-4">Chưa có vận đơn.</td></tr>';
      return;
    }
    body.innerHTML = shipments.map(function (x) {
      return '<tr>' +
        '<td><strong>' + esc(x.maVanDon || ('VC-' + x.maVanChuyen)) + '</strong></td>' +
        '<td>#DHCH' + esc(x.maDHCH) + '</td>' +
        '<td>' + esc(x.tenDonVi || '-') + '</td>' +
        '<td>' + statusBadge(x.trangThaiVanChuyen) + '</td>' +
        '<td>' + esc(x.soLanGiaoThatBai || 0) + '</td>' +
        '<td class="small">' + date(x.ngayCapNhat) + '</td>' +
        '<td class="text-end"><button class="btn btn-outline-secondary btn-sm" onclick="Shipping.openStatus(' + x.maVanChuyen + ')"><i class="bi bi-pencil-square me-1"></i>Cập nhật</button></td>' +
      '</tr>';
    }).join('');
  }

  function renderPartnerOptions() {
    var select = document.getElementById('acceptPartner');
    select.innerHTML = partners.filter(function (x) { return x.trangThai !== 'NGUNG_HOAT_DONG'; })
      .map(function (x) { return '<option value="' + x.maDVVC + '">' + esc(x.tenDonVi) + '</option>'; }).join('');
    if (!select.innerHTML) select.innerHTML = '<option value="">Chưa có đối tác hoạt động</option>';
  }

  function openAccept(orderId, maDH) {
    document.getElementById('acceptOrderId').value = orderId;
    document.getElementById('acceptOrderLabel').value = '#DHCH' + orderId + ' / Đơn #' + maDH;
    document.getElementById('acceptTracking').value = '';
    document.getElementById('acceptNote').value = '';
    renderPartnerOptions();
    bootstrap.Modal.getOrCreateInstance(document.getElementById('acceptModal')).show();
  }

  function accept() {
    var partner = Number(document.getElementById('acceptPartner').value);
    if (!partner) { OldBook.toast('Hãy chọn một đơn vị vận chuyển.', 'warning'); return; }
    var body = {
      maDHCH: Number(document.getElementById('acceptOrderId').value),
      maDVVC: partner,
      maVanDon: document.getElementById('acceptTracking').value || null,
      trangThaiVanChuyen: 'DA_TIEP_NHAN',
      ghiChuGiaoHang: document.getElementById('acceptNote').value || null
    };
    if (!body.maDHCH) return;
    if (!body.maVanDon) body.maVanDon = 'OB-' + Date.now().toString().slice(-10);
    OldBook.request('POST', '/api/shipping/requests', body).then(function () {
      bootstrap.Modal.getInstance(document.getElementById('acceptModal')).hide();
      OldBook.toast('Đã tiếp nhận yêu cầu vận chuyển.', 'success');
      loadAll();
    }).catch(function (e) { OldBook.toast(e.message, 'danger'); });
  }

  function openStatus(id) {
    var x = shipments.find(function (s) { return s.maVanChuyen === id; });
    if (!x) return;
    document.getElementById('statusId').value = id;
    document.getElementById('statusValue').value = x.trangThaiVanChuyen || 'DA_TIEP_NHAN';
    document.getElementById('statusFail').value = x.soLanGiaoThatBai || 0;
    document.getElementById('statusRefund').value = 0;
    document.getElementById('statusNote').value = x.ghiChuGiaoHang || '';
    bootstrap.Modal.getOrCreateInstance(document.getElementById('statusModal')).show();
  }

  function updateStatus() {
    var status = document.getElementById('statusValue').value;
    var body = {
      trangThai: status,
      soLanGiaoThatBai: Number(document.getElementById('statusFail').value || 0),
      tienHoanTra: Number(document.getElementById('statusRefund').value || 0),
      ghiChu: document.getElementById('statusNote').value || null
    };
    var id = document.getElementById('statusId').value;
    OldBook.request('PATCH', '/api/shipping/requests/' + id + '/status', body).then(function () {
      bootstrap.Modal.getInstance(document.getElementById('statusModal')).hide();
      OldBook.toast('Đã cập nhật trạng thái vận chuyển.', 'success');
      loadShipments();
    }).catch(function (e) { OldBook.toast(e.message, 'danger'); });
  }

  window.Shipping = {
    loadAll: loadAll,
    loadShipments: loadShipments,
    openAccept: openAccept,
    accept: accept,
    openStatus: openStatus,
    updateStatus: updateStatus
  };

  document.addEventListener('DOMContentLoaded', function () {
    if (!ensureAccess()) return;
    loadAll();
  });
})();
