'use strict';

// Ba trang thanh toán chuyển khoản: quét mã (khách), hoàn tiền của tôi (khách), quản lý thanh toán (quản lý).

const test = require('node:test');
const assert = require('node:assert/strict');
const { boot, settle } = require('./dom-harness.cjs');

const QR = 'https://img.vietqr.io/image/970422-0123456789-compact2.png?amount=125000&addInfo=OB7K2M9QXA';

function session(overrides = {}) {
  return {
    maPhien: 7, maThanhToan: 'OB7K2M9QXA', soTien: 125000, trangThai: 'CHO_THANH_TOAN',
    hetHanLuc: '2026-10-12T15:05:00', conLaiGiay: 287, qrUrl: QR,
    maNganHang: '970422', soTaiKhoan: '0123456789', tenTaiKhoan: 'OLDBOOK', maDH: null,
    ...overrides
  };
}

function refund(overrides = {}) {
  return {
    maYeuCau: 15, loai: 'HOAN_TIEN_KHACH', trangThai: 'CHO_THONG_TIN', soTien: 125000, noiDungChuyenKhoan: 'HT15',
    maDH: 3, maDHCH: 4, lyDo: 'Đơn hàng bị hủy', tenNguoiNhan: 'Nguyễn Văn Khách', emailNguoiNhan: 'khach@example.com',
    soDienThoaiNguoiNhan: '0911111111', tenCuaHang: 'Tiệm sách cũ', maNganHang: null, soTaiKhoan: null,
    tenChuTaiKhoan: null, qrUrl: null, ngayTao: '2026-10-12T15:00:00', ngayXuLy: null,
    ...overrides
  };
}

function pageOf(content) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 };
}

function shown(app, id) { return !app.get(id).classList.contains('d-none'); }

// auth.js tự gọi /api/profile để lấy tên hiển thị; các test ở đây chỉ quan tâm tới lời gọi của trang.
function pageCalls(app) { return app.calls.filter(call => call.url !== '/api/profile'); }

function pay(handler, role = 'KHACH_HANG', search = '?maPhien=7') {
  return boot('payment/pay.html', ['banks.js', 'payment-pay.js'], handler, role, { pathname: '/payment/pay', search });
}

// ---------------------------------------------------------------------------
// Trang quét mã
// ---------------------------------------------------------------------------

test('pay page shows the QR, the transfer details and the server-side countdown', async () => {
  const app = pay(async () => session());
  await settle();

  assert.deepEqual(pageCalls(app).map(call => [call.method, call.url]), [['GET', '/api/payment/sessions/7']]);
  assert.ok(shown(app, 'payContent'));
  assert.ok(!shown(app, 'payMessage'));
  assert.equal(app.get('payQr').getAttribute('src'), QR);
  assert.equal(app.get('payBank').textContent, 'MB Bank');
  assert.equal(app.get('payAccount').textContent, '0123456789');
  assert.equal(app.get('payAccountName').textContent, 'OLDBOOK');
  assert.match(app.get('payAmount').textContent, /125[.,]000/);
  assert.equal(app.get('payAmount').getAttribute('data-raw'), '125000');
  assert.equal(app.get('payCode').textContent, 'OB7K2M9QXA');
  assert.equal(app.get('payCountdown').textContent, '04:47');
});

test('pay page goes to the order once the server says the money arrived', async () => {
  const app = pay(async () => session({ trangThai: 'DA_THANH_TOAN', maDH: 42 }));
  await settle();

  assert.equal(app.window.location.href, '/order-success?maDH=42');
});

test('pay page explains an expired code and tells the buyer not to pay twice', async () => {
  for (const state of [{ trangThai: 'HET_HAN', conLaiGiay: 0 }, { trangThai: 'CHO_THANH_TOAN', conLaiGiay: 0 }]) {
    const app = pay(async () => session(state));
    await settle();

    assert.ok(shown(app, 'payMessage'), JSON.stringify(state));
    assert.ok(!shown(app, 'payContent'));
    assert.equal(app.get('payMessageTitle').textContent, 'Mã thanh toán đã hết hạn');
    assert.match(app.get('payMessageText').textContent, /đừng chuyển lại/);
    assert.equal(app.window.location.href, undefined);
  }
});

test('pay page sends the buyer to the refund form when money arrived but no order could be made', async () => {
  const app = pay(async () => session({ trangThai: 'CAN_HOAN_TIEN' }));
  await settle();

  assert.ok(shown(app, 'payMessage'));
  assert.match(app.get('payMessageTitle').textContent, /chưa tạo được đơn hàng/);
  assert.equal(app.get('payMessagePrimary').getAttribute('href'), '/payment/refunds');
});

test('pay page cancels the session only after the buyer confirms', async () => {
  let current = session();
  const app = pay(async call => {
    if (call.method === 'DELETE') { current = session({ trangThai: 'DA_HUY', conLaiGiay: 200 }); }
    return current;
  });
  await settle();

  app.window.OldBook.confirm = async () => false;
  app.trigger('payCancel');
  await settle();
  assert.equal(app.calls.filter(call => call.method === 'DELETE').length, 0);

  app.window.OldBook.confirm = async () => true;
  app.trigger('payCancel');
  await settle();
  assert.deepEqual(app.calls.filter(call => call.method === 'DELETE').map(call => call.url), ['/api/payment/sessions/7']);
  assert.equal(app.get('payMessageTitle').textContent, 'Bạn đã hủy lần thanh toán này');
});

test('pay page needs a session id and a signed-in buyer', async () => {
  const noId = pay(async () => session(), 'KHACH_HANG', '');
  await settle();
  assert.deepEqual(pageCalls(noId), []);
  assert.equal(noId.get('payMessageTitle').textContent, 'Không tìm thấy mã thanh toán');

  const guest = pay(async () => session(), null);
  await settle();
  assert.deepEqual(pageCalls(guest), []);
  assert.equal(guest.redirects.length, 1);
  assert.match(guest.redirects[0], /^\/login\?redirect=/);
});

// ---------------------------------------------------------------------------
// Hoàn tiền của tôi
// ---------------------------------------------------------------------------

function refunds(handler) {
  return boot('payment/refunds.html', ['banks.js', 'payment-refunds.js'], handler, 'KHACH_HANG', { pathname: '/payment/refunds' });
}

test('refund page lists each refund with its state and the right next step', async () => {
  const app = refunds(async () => [
    refund(),
    refund({ maYeuCau: 16, maDHCH: null, trangThai: 'CHO_THONG_TIN', maNganHang: '970415', soTaiKhoan: '8888888888', tenChuTaiKhoan: 'NGUYEN VAN KHACH', lyDo: '<b>thiếu tiền</b>' }),
    refund({ maYeuCau: 17, trangThai: 'CHO_XU_LY', maNganHang: '970436', soTaiKhoan: '0071000', tenChuTaiKhoan: 'A' }),
    refund({ maYeuCau: 18, trangThai: 'DA_CHUYEN', maNganHang: '970436', soTaiKhoan: '0071000', tenChuTaiKhoan: 'A' })
  ]);
  await settle();

  const cards = app.document.querySelectorAll('.refund-card');
  assert.equal(cards.length, 4);
  assert.ok(!shown(app, 'refundsEmpty'));
  assert.match(cards[0].textContent, /Đơn #4/);
  assert.match(cards[0].textContent, /Chờ bạn nhập tài khoản nhận/);
  assert.equal(cards[0].querySelector('[data-refund]').textContent, 'Nhập tài khoản nhận');
  // Tài khoản điền sẵn từ lần chuyển tiền: khách phải xác nhận, và nội dung lạ không thành HTML.
  assert.match(cards[1].textContent, /Lần thanh toán chưa tạo được đơn/);
  assert.equal(cards[1].querySelector('[data-refund]').textContent, 'Xác nhận tài khoản nhận');
  assert.equal(cards[1].querySelector('b'), null);
  assert.match(cards[1].textContent, /VietinBank · 8888888888/);
  assert.equal(cards[2].querySelector('[data-refund]').textContent, 'Sửa tài khoản');
  assert.match(cards[3].textContent, /Đã hoàn/);
  assert.equal(cards[3].querySelector('[data-refund]'), null);
});

test('refund page shows the empty state', async () => {
  const app = refunds(async () => []);
  await settle();

  assert.ok(shown(app, 'refundsEmpty'));
  assert.equal(app.document.querySelectorAll('.refund-card').length, 0);
});

test('refund page saves the receiving account and reloads the list', async () => {
  let saved = false;
  const app = refunds(async call => {
    if (call.method === 'PUT') { saved = true; return refund({ trangThai: 'CHO_XU_LY' }); }
    return [refund(saved ? { trangThai: 'CHO_XU_LY', maNganHang: '970415', soTaiKhoan: '1029384756', tenChuTaiKhoan: 'NGUYEN VAN KHACH' } : {})];
  });
  await settle();

  app.trigger(app.document.querySelector('[data-refund="15"]'));
  assert.ok(app.get('refundAccountModal').classList.contains('show'));
  assert.match(app.get('refundAccountAmount').textContent, /125[.,]000/);

  // Thiếu thông tin thì báo lỗi tại chỗ, không gọi máy chủ.
  app.trigger('refundAccountForm', 'submit');
  await settle();
  assert.ok(shown(app, 'refundAccountError'));
  assert.equal(app.calls.filter(call => call.method === 'PUT').length, 0);

  app.set('refundBank', '970415');
  app.set('refundAccountNo', ' 1029384756 ');
  app.set('refundAccountName', ' NGUYEN VAN KHACH ');
  app.trigger('refundAccountForm', 'submit');
  await settle();

  const put = app.calls.find(call => call.method === 'PUT');
  assert.equal(put.url, '/api/payment/refunds/15/account');
  assert.deepEqual(put.body, { maNganHang: '970415', soTaiKhoan: '1029384756', tenChuTaiKhoan: 'NGUYEN VAN KHACH' });
  assert.ok(!app.get('refundAccountModal').classList.contains('show'));
  assert.match(app.document.querySelector('.refund-card').textContent, /Sàn đang chuyển khoản/);
});

// ---------------------------------------------------------------------------
// Quản lý thanh toán
// ---------------------------------------------------------------------------

const TRANSFERS = '/api/management/payments/transfers';

function management(role, handler) {
  return boot('payment/management.html', ['banks.js', 'management/payments.js'], handler, role, { pathname: '/management/payments' });
}

test('payment management is open to managers only', async () => {
  for (const role of ['KHACH_HANG', 'CHU_CUA_HANG', 'QUAN_TRI_VIEN']) {
    const app = management(role, async () => pageOf([]));
    await settle();

    assert.ok(shown(app, 'paymentsDenied'), role);
    assert.ok(!shown(app, 'paymentsContent'));
    assert.deepEqual(pageCalls(app), []);
  }
});

test('manager sees pending refunds first and can only transfer once the account is confirmed', async () => {
  const ready = refund({ trangThai: 'CHO_XU_LY', maNganHang: '970415', soTaiKhoan: '1029384756', tenChuTaiKhoan: 'NGUYEN VAN KHACH',
    tenNguoiNhan: '<i>Khách</i>', qrUrl: 'https://img.vietqr.io/image/970415-1029384756-compact2.png?amount=125000&addInfo=HT15' });
  const waiting = refund({ maYeuCau: 16, noiDungChuyenKhoan: 'HT16' });
  const app = management('QUAN_LY', async () => pageOf([ready, waiting]));
  await settle();

  assert.equal(pageCalls(app)[0].url, TRANSFERS + '?loai=HOAN_TIEN_KHACH&page=0&size=20&trangThai=CHO_XU_LY');
  const rows = app.get('paymentsRows').querySelectorAll('tr');
  assert.equal(rows.length, 2);
  assert.equal(rows[0].querySelector('i'), null, 'tên khách không được thành HTML');
  assert.match(rows[0].textContent, /HT15/);
  assert.match(rows[0].textContent, /VietinBank/);
  assert.equal(rows[0].querySelector('[data-transfer]').textContent, 'Chuyển khoản');
  assert.match(rows[1].textContent, /Khách chưa nhập/);
  assert.equal(rows[1].querySelector('[data-transfer]').textContent, 'Xem');

  // Khoản chưa có tài khoản: mở ra chỉ để xem, không có nút "Đã chuyển".
  app.trigger(rows[1].querySelector('[data-transfer]'));
  assert.ok(shown(app, 'transferNoAccount'));
  assert.ok(!shown(app, 'transferDone'));
  assert.ok(!shown(app, 'transferQrBox'));
});

test('manager marks a transfer as done only after confirming, then the list reloads', async () => {
  const ready = refund({ trangThai: 'CHO_XU_LY', maNganHang: '970415', soTaiKhoan: '1029384756', tenChuTaiKhoan: 'NGUYEN VAN KHACH',
    qrUrl: 'https://img.vietqr.io/image/970415-1029384756-compact2.png?amount=125000&addInfo=HT15' });
  let done = false;
  const app = management('QUAN_LY', async call => {
    if (call.method === 'POST') { done = true; return { ...ready, trangThai: 'DA_CHUYEN' }; }
    return pageOf(done ? [] : [ready]);
  });
  await settle();

  app.trigger(app.document.querySelector('[data-transfer="15"]'));
  assert.ok(app.get('transferModal').classList.contains('show'));
  assert.equal(app.get('transferQr').getAttribute('src'), ready.qrUrl);
  assert.equal(app.get('transferAccount').textContent, '1029384756');
  assert.equal(app.get('transferCode').textContent, 'HT15');
  assert.ok(shown(app, 'transferDone'));

  app.window.OldBook.confirm = async () => false;
  app.trigger('transferDone');
  await settle();
  assert.equal(app.calls.filter(call => call.method === 'POST').length, 0);

  app.window.OldBook.confirm = async () => true;
  app.trigger('transferDone');
  await settle();
  assert.deepEqual(app.calls.filter(call => call.method === 'POST').map(call => call.url), [TRANSFERS + '/15/done']);
  assert.ok(!app.get('transferModal').classList.contains('show'));
  assert.ok(shown(app, 'paymentsEmpty'));
});

test('manager switches to shop payouts and to the bank ledger', async () => {
  const payout = refund({ maYeuCau: 8, loai: 'CHI_CUA_HANG', noiDungChuyenKhoan: 'CT8', trangThai: 'CHO_XU_LY', soTien: 95000, lyDo: null });
  const transaction = {
    maGiaoDich: 1, maGiaoDichCong: '700001', soTien: 125000, noiDung: 'MBVCB.1. OB7K2M9QXA <script>', thoiGianGiaoDich: '2026-10-12T15:36:21',
    tenNguoiChuyen: 'NGUYEN VAN KHACH', soTaiKhoanChuyen: '8888888888', maThanhToan: 'OB7K2M9QXA', ketQua: 'KHOP', ghiChu: null, ngayNhan: '2026-10-12T15:36:25'
  };
  const app = management('QUAN_LY', async call => {
    if (call.url.includes('/transactions')) { return pageOf([transaction]); }
    return pageOf(call.url.includes('CHI_CUA_HANG') ? [payout] : []);
  });
  await settle();

  app.trigger(app.document.querySelector('[data-tab="CHI_CUA_HANG"]'));
  await settle();
  assert.equal(app.calls.at(-1).url, TRANSFERS + '?loai=CHI_CUA_HANG&page=0&size=20&trangThai=CHO_XU_LY');
  const payoutRow = app.get('paymentsRows').querySelector('tr');
  assert.match(payoutRow.textContent, /CT8/);
  assert.match(payoutRow.textContent, /Tiệm sách cũ/);
  // Cửa hàng chưa khai tài khoản thì chưa chi được.
  assert.match(payoutRow.textContent, /Cửa hàng chưa khai/);
  assert.equal(payoutRow.querySelector('[data-transfer]').textContent, 'Xem');

  app.trigger(app.document.querySelector('[data-tab="GIAO_DICH"]'));
  await settle();
  assert.equal(app.calls.at(-1).url, '/api/management/payments/transactions?page=0&size=20');
  assert.ok(!shown(app, 'paymentsFilter'));
  const ledgerRow = app.get('paymentsRows').querySelector('tr');
  assert.match(ledgerRow.textContent, /Khớp, đã tạo đơn/);
  assert.match(ledgerRow.textContent, /12\/10\/2026 15:36/);
  assert.equal(ledgerRow.querySelector('script'), null);
});
