/*
 * Khối nhập nơi nhận tiền dùng chung: khách nhận hoàn tiền, chủ cửa hàng nhận doanh thu, hộp thoại hủy đơn.
 *
 * Gồm hai phần: (1) tài khoản ngân hàng (ngân hàng, số tài khoản, tên chủ tài khoản) và (2) ảnh mã QR ngân hàng
 * tải lên Cloudinary qua API riêng của từng vai trò (tối đa 5 MB). Hợp lệ khi có đủ phần 1 HOẶC có ảnh ở phần 2.
 *
 *   var form = OldBook.bankForm.create(container, { idPrefix: 'refund', uploadUrl: '/api/payment/bank-qr' });
 *   form.setValues({ maNganHang, soTaiKhoan, tenChuTaiKhoan, qrNganHangUrl });
 *   var values = form.getValues();     // { maNganHang, soTaiKhoan, tenChuTaiKhoan, qrNganHangUrl } đã chuẩn hóa
 *   var error = form.validate();       // chuỗi lỗi, hoặc '' khi hợp lệ
 */
(function () {
  'use strict';

  var OB = window.OldBook;
  if (!OB || OB.bankForm) { return; }

  var Banks = window.OldBookBanks;
  var ACCOUNT_PATTERN = /^[0-9A-Za-z]{4,30}$/;
  var MAX_QR_BYTES = 5 * 1024 * 1024;
  var NOTE = 'Khuyến khích nhập đầy đủ thông tin tài khoản và tải kèm ảnh mã QR ngân hàng để hệ thống đối chiếu '
    + 'tính chính xác của tài khoản nhận tiền.';

  // Tên chủ tài khoản: chữ in hoa, không dấu (đúng như ngân hàng ghi).
  function normalizeName(value) {
    return String(value || '')
      .normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D')
      .toUpperCase().replace(/\s+/g, ' ').trim();
  }

  function blank(value) { return value === null || value === undefined || String(value).trim() === ''; }

  // Hàm thuần để kiểm tra (và để test): trả về thông báo lỗi hoặc ''.
  function validate(values) {
    var any = !blank(values.maNganHang) || !blank(values.soTaiKhoan) || !blank(values.tenChuTaiKhoan);
    var full = !blank(values.maNganHang) && !blank(values.soTaiKhoan) && !blank(values.tenChuTaiKhoan);
    if (any && !full) { return 'Vui lòng nhập đủ ngân hàng, số tài khoản và tên chủ tài khoản.'; }
    if (full && !ACCOUNT_PATTERN.test(String(values.soTaiKhoan).trim())) {
      return 'Số tài khoản chỉ gồm chữ và số, từ 4 đến 30 ký tự.';
    }
    if (!full && blank(values.qrNganHangUrl)) {
      return 'Vui lòng nhập thông tin tài khoản hoặc tải ảnh mã QR ngân hàng.';
    }
    return '';
  }

  function create(root, options) {
    options = options || {};
    var p = options.idPrefix || 'bank';
    var qrUrl = '';
    var uploading = false;
    var onChange = options.onChange || function () { };

    root.innerHTML =
      '<div class="mb-3"><label for="' + p + 'Bank" class="form-label">Ngân hàng</label><select id="' + p + 'Bank" class="form-select"></select></div>'
      + '<div class="mb-3"><label for="' + p + 'Account" class="form-label">Số tài khoản</label>'
      + '<input id="' + p + 'Account" class="form-control" maxlength="30" inputmode="numeric" autocomplete="off"></div>'
      + '<div class="mb-3"><label for="' + p + 'Owner" class="form-label">Tên chủ tài khoản</label>'
      + '<input id="' + p + 'Owner" class="form-control" maxlength="255" autocomplete="off" style="text-transform:uppercase"></div>'
      + '<div class="mb-2"><label for="' + p + 'QrFile" class="form-label">Ảnh mã QR ngân hàng <span class="text-muted">(không bắt buộc nếu đã nhập tài khoản)</span></label>'
      + '<input id="' + p + 'QrFile" type="file" class="form-control" accept="image/png,image/jpeg,image/webp"></div>'
      + '<div id="' + p + 'QrBox" class="d-none align-items-center gap-3 mb-2">'
      + '<img id="' + p + 'QrImg" alt="Ảnh mã QR ngân hàng đã tải lên" style="max-width:120px;max-height:120px;border:1px solid var(--border-soft);border-radius:6px">'
      + '<button type="button" id="' + p + 'QrRemove" class="btn btn-sm btn-outline-secondary">Bỏ ảnh</button></div>'
      + '<div id="' + p + 'QrStatus" class="small text-muted mb-2" role="status"></div>'
      + '<p class="small text-muted mb-0">' + NOTE + '</p>';

    function $(suffix) { return root.querySelector('#' + p + suffix); }

    Banks.fill($('Bank'), '');

    function showQr() {
      var box = $('QrBox');
      box.classList.toggle('d-none', !qrUrl);
      box.classList.toggle('d-flex', !!qrUrl);
      if (qrUrl) { $('QrImg').setAttribute('src', qrUrl); }
    }

    function status(message) { $('QrStatus').textContent = message || ''; }

    $('Owner').addEventListener('blur', function () { $('Owner').value = normalizeName($('Owner').value); });

    $('QrFile').addEventListener('change', function () {
      var file = $('QrFile').files && $('QrFile').files[0];
      if (!file) { return; }
      if (!/^image\//.test(file.type || '')) {
        status('Chỉ nhận file ảnh.');
        $('QrFile').value = '';
        return;
      }
      if (file.size > MAX_QR_BYTES) {
        status('Ảnh không được vượt quá 5 MB.');
        $('QrFile').value = '';
        return;
      }
      var body = new window.FormData();
      body.append('file', file);
      uploading = true;
      status('Đang tải ảnh lên...');
      onChange();
      OB.request('POST', options.uploadUrl, body).then(function (data) {
        qrUrl = (data && data.url) || '';
        status(qrUrl ? 'Đã tải ảnh lên.' : 'Không tải được ảnh. Vui lòng thử lại.');
        showQr();
      }, function (err) {
        status(err.message || 'Không tải được ảnh. Vui lòng thử lại.');
      }).then(function () {
        uploading = false;
        $('QrFile').value = '';
        onChange();
      });
    });

    $('QrRemove').addEventListener('click', function () {
      qrUrl = '';
      status('');
      showQr();
      onChange();
    });

    var api = {
      getValues: function () {
        var account = $('Account').value.trim();
        var owner = normalizeName($('Owner').value);
        return {
          maNganHang: $('Bank').value || null,
          soTaiKhoan: account || null,
          tenChuTaiKhoan: owner || null,
          qrNganHangUrl: qrUrl || null
        };
      },
      setValues: function (values) {
        values = values || {};
        Banks.fill($('Bank'), values.maNganHang || '');
        $('Account').value = values.soTaiKhoan || '';
        $('Owner').value = values.tenChuTaiKhoan || '';
        qrUrl = values.qrNganHangUrl || '';
        status('');
        showQr();
      },
      validate: function () {
        if (uploading) { return 'Ảnh mã QR đang được tải lên, vui lòng chờ.'; }
        return validate(api.getValues());
      },
      isEmpty: function () {
        var v = api.getValues();
        return blank(v.maNganHang) && blank(v.soTaiKhoan) && blank(v.tenChuTaiKhoan) && blank(v.qrNganHangUrl);
      },
      isUploading: function () { return uploading; },
      focus: function () { $('Bank').focus(); }
    };
    api.setValues({});
    return api;
  }

  OB.bankForm = { create: create, validate: validate, normalizeName: normalizeName };
})();
