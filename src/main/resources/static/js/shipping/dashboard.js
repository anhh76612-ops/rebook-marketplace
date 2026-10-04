(function () {
  'use strict';

  // Trang không cần đăng nhập: hãng được xác định bởi mã trên URL (/shipping/{maCode}),
  // server đưa mã vào data-ma-code. Mọi API đều đi qua /api/shipping/{maCode}/...
  var page = document.querySelector('[data-shipping-page]');
  var MA_CODE = page ? page.getAttribute('data-ma-code') : null;
  var API = '/api/shipping/' + encodeURIComponent(MA_CODE || '');
  var REFRESH_MS = 30000;

  var shipments = [];
  var pending = [];

  // Không gửi token đăng nhập (nếu trình duyệt đang lưu) vì trang này không dùng tài khoản.
  function api(method, path, body) {
    return OldBook.request(method, API + path, body, { token: null });
  }

  function esc(v) { return OldBook.escapeHtml(v == null ? '' : v); }
  function money(v) { return OldBook.formatMoney(v); }
  function date(v) { return v ? new Date(v).toLocaleString('vi-VN') : '-'; }

  function showAlert(message) {
    var a = document.getElementById('accessAlert');
    a.classList.remove('d-none');
    a.textContent = message;
  }

  // Cùng bộ trạng thái và bước chuyển hợp lệ với phía cửa hàng/khách (TrangThaiVanChuyen).
  var LABELS = {
    CHO_LAY_HANG: 'Chờ lấy hàng',
    DANG_GIAO: 'Đang giao',
    DA_GIAO: 'Đã giao',
    GIAO_THAT_BAI: 'Giao thất bại',
    HOAN_TRA: 'Hoàn trả'
  };
  var NEXT = {
    CHO_LAY_HANG: ['DANG_GIAO'],
    DANG_GIAO: ['DA_GIAO', 'GIAO_THAT_BAI'],
    GIAO_THAT_BAI: ['DANG_GIAO', 'HOAN_TRA'],
    DA_GIAO: [],
    HOAN_TRA: []
  };
  var RESULT = ['DA_GIAO', 'GIAO_THAT_BAI', 'HOAN_TRA'];

  function statusBadge(status) {
    var cls = {
      DA_GIAO: 'text-bg-success',
      HOAN_TRA: 'text-bg-warning',
      GIAO_THAT_BAI: 'text-bg-danger',
      DANG_GIAO: 'text-bg-primary'
    }[status] || 'text-bg-secondary';
    return '<span class="badge ' + cls + '">' + esc(LABELS[status] || status || 'Chưa cập nhật') + '</span>';
  }

  function loadPending() {
    return api('GET', '/requests/pending').then(function (data) {
      pending = data || [];
      renderPending();
      updateStats();
    });
  }

  function loadShipments() {
    return api('GET', '/requests').then(function (data) {
      shipments = data || [];
      renderShipments();
      updateStats();
    });
  }

  function loadAll(silent) {
    return Promise.all([loadPending(), loadShipments()])
      .catch(function (e) { if (!silent) OldBook.toast(e.message, 'danger'); });
  }

  // Đơn được cửa hàng xác nhận sẽ tự xuất hiện: làm mới định kỳ khi tab đang mở và không có hộp thoại nào đang mở.
  function startAutoRefresh() {
    window.setInterval(function () {
      if (document.hidden) return;
      if (document.querySelector('.modal.show')) return;
      loadAll(true);
    }, REFRESH_MS);
    document.addEventListener('visibilitychange', function () {
      if (!document.hidden && !document.querySelector('.modal.show')) loadAll(true);
    });
  }

  function updateStats() {
    function count(status) { return shipments.filter(function (x) { return x.trangThaiVanChuyen === status; }).length; }
    document.getElementById('statPending').textContent = pending.length;
    document.getElementById('statShipping').textContent = count('DANG_GIAO');
    document.getElementById('statDelivered').textContent = count('DA_GIAO');
    document.getElementById('statReturned').textContent = count('HOAN_TRA');
  }

  function renderPending() {
    var body = document.getElementById('pendingBody');
    if (!pending.length) {
      body.innerHTML = '<tr><td colspan="7" class="text-center text-muted py-4">Không có yêu cầu chờ tiếp nhận.</td></tr>';
      return;
    }
    body.innerHTML = pending.map(function (x) {
      return '<tr>' +
        '<td><strong>#DHCH' + esc(x.maDHCH) + '</strong><div class="small text-muted">Đơn #' + esc(x.maDH) + '</div></td>' +
        '<td>' + esc(x.tenDonVi || '-') + '</td>' +
        '<td><span class="badge text-bg-light border">' + esc(x.trangThaiDonHang) + '</span></td>' +
        '<td>' + esc(x.trangThaiThanhToan || '-') + '</td>' +
        '<td>' + money(x.tongTien) + '</td>' +
        '<td class="small">' + date(x.ngayCapNhat) + '</td>' +
        '<td class="text-end"><button class="btn btn-accent btn-sm" onclick="Shipping.openAccept(' + x.maDHCH + ')"><i class="bi bi-box-arrow-in-right me-1"></i>Tiếp nhận</button></td>' +
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

  function openAccept(orderId) {
    var x = pending.find(function (p) { return p.maDHCH === orderId; });
    if (!x) return;
    document.getElementById('acceptOrderId').value = orderId;
    document.getElementById('acceptOrderLabel').value = '#DHCH' + orderId + ' / Đơn #' + x.maDH;
    document.getElementById('acceptTracking').value = '';
    document.getElementById('acceptNote').value = '';
    bootstrap.Modal.getOrCreateInstance(document.getElementById('acceptModal')).show();
  }

  function accept() {
    var body = {
      maDHCH: Number(document.getElementById('acceptOrderId').value),
      maVanDon: document.getElementById('acceptTracking').value || null,
      ghiChuGiaoHang: document.getElementById('acceptNote').value || null
    };
    if (!body.maDHCH) return;
    api('POST', '/requests', body).then(function () {
      bootstrap.Modal.getInstance(document.getElementById('acceptModal')).hide();
      OldBook.toast('Đã tiếp nhận, đơn chuyển sang trạng thái đang giao.', 'success');
      loadAll();
    }).catch(function (e) { OldBook.toast(e.message, 'danger'); });
  }

  function onStatusChange() {
    var v = document.getElementById('statusValue').value;
    document.getElementById('statusRefundWrap').classList.toggle('d-none', v !== 'HOAN_TRA');
  }

  function openStatus(id) {
    var x = shipments.find(function (s) { return s.maVanChuyen === id; });
    if (!x) return;
    var allowed = NEXT[x.trangThaiVanChuyen] || [];
    if (!allowed.length) {
      OldBook.toast('Vận đơn đã kết thúc, không thể cập nhật thêm.', 'warning');
      return;
    }
    document.getElementById('statusId').value = id;
    document.getElementById('statusValue').innerHTML = allowed.map(function (st) {
      return '<option value="' + st + '">' + esc(LABELS[st]) + '</option>';
    }).join('');
    document.getElementById('statusRefund').value = 0;
    document.getElementById('statusNote').value = x.ghiChuGiaoHang || '';
    onStatusChange();
    bootstrap.Modal.getOrCreateInstance(document.getElementById('statusModal')).show();
  }

  function updateStatus() {
    var status = document.getElementById('statusValue').value;
    var body = { trangThai: status, ghiChu: document.getElementById('statusNote').value || null };
    if (status === 'HOAN_TRA') {
      body.tienHoanTra = Number(document.getElementById('statusRefund').value || 0);
    }
    var id = document.getElementById('statusId').value;
    var path = RESULT.indexOf(status) >= 0 ? '/result' : '/status';
    api('PATCH', '/requests/' + encodeURIComponent(id) + path, body).then(function () {
      bootstrap.Modal.getInstance(document.getElementById('statusModal')).hide();
      OldBook.toast('Đã cập nhật trạng thái vận chuyển.', 'success');
      loadAll();
    }).catch(function (e) { OldBook.toast(e.message, 'danger'); });
  }

  window.Shipping = {
    loadAll: loadAll,
    loadShipments: loadShipments,
    openAccept: openAccept,
    onStatusChange: onStatusChange,
    accept: accept,
    openStatus: openStatus,
    updateStatus: updateStatus
  };

  document.addEventListener('DOMContentLoaded', function () {
    if (!MA_CODE) {
      showAlert('Liên kết thiếu mã đơn vị vận chuyển.');
      return;
    }
    loadAll();
    startAutoRefresh();
  });
})();
