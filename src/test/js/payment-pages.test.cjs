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
  return boot('payment/refunds.html', ['banks.js', 'bank-account-form.js', 'payment-refunds.js'], handler, 'KHACH_HANG', { pathname: '/payment/refunds' });
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
  app.set('refundAccount', ' 1029384756 ');
  app.set('refundOwner', ' Nguyễn Văn Khách ');
  app.trigger('refundAccountForm', 'submit');
  await settle();

  const put = app.calls.find(call => call.method === 'PUT');
  assert.equal(put.url, '/api/payment/refunds/15/account');
  assert.deepEqual(put.body, { maNganHang: '970415', soTaiKhoan: '1029384756', tenChuTaiKhoan: 'NGUYEN VAN KHACH', qrNganHangUrl: null });
  assert.ok(!app.get('refundAccountModal').classList.contains('show'));
  assert.match(app.document.querySelector('.refund-card').textContent, /Sàn đang chuyển khoản/);
});

// ---------------------------------------------------------------------------
// Thanh toán & Đối soát (quản lý): chi trả cửa hàng theo đợt, hoàn tiền khách, giao dịch ngân hàng
// ---------------------------------------------------------------------------

const BASE = '/api/management/payments';
const TRANSFERS = BASE + '/transfers';

function management(role, handler, search = '') {
  return boot('payment/management.html', ['banks.js', 'management/payments.js'], handler, role, { pathname: '/management/payments', search });
}

function child(overrides = {}) {
  return {
    maYeuCau: 31, maDHCH: 11, maDH: 5, ngayGiao: '2026-10-12T10:00:00', tienHang: 100000, giamGiaShop: 0,
    hoaHong: 5000, thucNhan: 95000, trangThai: 'CHO_XU_LY', maDotChi: null, ...overrides
  };
}

function group(overrides = {}) {
  const don = overrides.donCon || [child(), child({ maYeuCau: 32, maDHCH: 12, maDH: 6 })];
  return {
    maCH: 3, tenCuaHang: '<b>Tiệm sách cũ</b>', tenChuShop: 'Trần Thu Hà', soDienThoai: '0900000004', coTaiKhoan: true,
    maNganHang: '970436', soTaiKhoan: '0071000123456', tenChuTaiKhoan: 'TRAN THU HA', soDon: don.length,
    tongTienHang: 200000, tongHoaHong: 10000, tongThucNhan: 190000, daNhacLuc: null, donCon: don, ...overrides
  };
}

function payoutHandler(groups, extra = {}) {
  return async call => {
    if (call.url.startsWith(BASE + '/store-payouts?')) { return groups; }
    if (call.url === '/api/management/overview') { return { cuaHangChoChi: groups.length, hoanTienCho: 0, hoanTienChoKhachKhai: 0, giaoDichLech: 0 }; }
    return extra[call.url] ? extra[call.url](call) : pageOf([]);
  };
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

test('payouts are grouped by shop; opening a shop ticks its pending orders and shows the total to transfer', async () => {
  const app = management('QUAN_LY', payoutHandler([group()]));
  await settle();

  assert.equal(pageCalls(app)[0].url, BASE + '/store-payouts?trangThai=CHO_XU_LY');
  const row = app.get('payoutRows').querySelector('tr[data-group="3"]');
  assert.equal(row.querySelector('b'), null, 'tên cửa hàng không được thành HTML');
  assert.match(row.textContent, /VietinBank|Vietcombank/);
  assert.match(row.textContent, /190[.,]000/);
  assert.ok(!shown(app, 'payoutBar'), 'chưa chọn đơn nào thì chưa có thanh tóm tắt');

  app.trigger(app.document.querySelector('[data-toggle="3"]'));
  const boxes = app.document.querySelectorAll('[data-pick]');
  assert.equal(boxes.length, 2);
  assert.ok(boxes.every(box => box.checked), 'đơn đang chờ được tích sẵn');
  assert.ok(shown(app, 'payoutBar'));
  assert.match(app.get('payoutBarTotal').textContent, /190[.,]000/);
  assert.match(app.get('payoutBarCount').textContent, /2 đơn/);

  // Bỏ tích một đơn thì tổng giảm theo.
  const first = app.document.querySelectorAll('[data-pick]')[0];
  first.checked = false;
  app.trigger(first);
  assert.match(app.get('payoutBarTotal').textContent, /95[.,]000/);
  assert.match(app.get('payoutBarCount').textContent, /1 đơn/);
});

test('only one shop can be ticked at a time and shops without an account are reminded instead', async () => {
  const other = group({ maCH: 4, tenCuaHang: 'Cửa hàng B', coTaiKhoan: false, maNganHang: null, soTaiKhoan: null,
    donCon: [child({ maYeuCau: 41, maDHCH: 21 })] });
  let reminded = null;
  // Máy chủ trả giờ địa phương không kèm múi giờ; lấy 5 phút trước để còn nằm trong giới hạn 1 giờ.
  const at = new Date(Date.now() - 5 * 60000);
  const pad = n => String(n).padStart(2, '0');
  const sent = at.getFullYear() + '-' + pad(at.getMonth() + 1) + '-' + pad(at.getDate()) + 'T' + pad(at.getHours()) + ':' + pad(at.getMinutes()) + ':00';
  const app = management('QUAN_LY', payoutHandler([group(), other], {
    [BASE + '/stores/4/remind-bank']: call => { reminded = call.method; return { daNhacLuc: sent }; }
  }));
  await settle();

  // Cửa hàng chưa khai tài khoản: nút nhắc thay cho nút chọn đơn.
  assert.equal(app.document.querySelector('[data-toggle="4"]'), null);
  assert.equal(app.document.querySelector('[data-remind-store="4"]').textContent, 'Gửi thông báo cập nhật STK');

  app.trigger(app.document.querySelector('[data-toggle="3"]'));
  assert.ok(shown(app, 'payoutBar'));

  app.trigger(app.document.querySelector('[data-remind-store="4"]'));
  await settle();
  assert.equal(reminded, 'POST');
  assert.equal(app.document.querySelector('[data-remind-store="4"]').textContent, 'Đã nhắc lúc ' + pad(at.getHours()) + ':' + pad(at.getMinutes()));
  assert.ok(app.document.querySelector('[data-remind-store="4"]').disabled);
});

test('creating a payout previews the batch, then marks every order done after confirming', async () => {
  const preview = { maDotChi: 'DCABCD2345', maCH: 3, tenCuaHang: 'Tiệm sách cũ', soDon: 2, tongTien: 190000,
    qrUrl: 'https://img.vietqr.io/image/970436-0071000123456-compact2.png?amount=190000&addInfo=DCABCD2345',
    taiKhoan: { maNganHang: '970436', soTaiKhoan: '0071000123456', tenChuTaiKhoan: 'TRAN THU HA' } };
  const app = management('QUAN_LY', payoutHandler([group()], {
    [BASE + '/store-payouts/preview']: () => preview,
    [BASE + '/store-payouts/done']: () => ({ soKhoan: 2 })
  }));
  await settle();

  app.trigger(app.document.querySelector('[data-toggle="3"]'));
  app.trigger('payoutCreate');
  await settle();

  const previewCall = app.calls.find(call => call.url === BASE + '/store-payouts/preview');
  assert.deepEqual(previewCall.body, { maYeuCau: [31, 32] });
  assert.ok(app.get('transferModal').classList.contains('show'));
  assert.equal(app.get('transferCode').textContent, 'DCABCD2345');
  assert.equal(app.get('transferQr').getAttribute('src'), preview.qrUrl);
  assert.match(app.get('transferAmount').textContent, /190[.,]000/);

  app.window.OldBook.confirm = async () => false;
  app.trigger('transferDone');
  await settle();
  assert.equal(app.calls.filter(call => call.url.endsWith('/store-payouts/done')).length, 0);

  app.window.OldBook.confirm = async () => true;
  app.trigger('transferDone');
  await settle();
  const done = app.calls.find(call => call.url === BASE + '/store-payouts/done');
  assert.deepEqual(done.body, { maDotChi: 'DCABCD2345', maYeuCau: [31, 32] });
  assert.ok(!app.get('transferModal').classList.contains('show'));
});

test('refund tab searches by keyword and offers reminder or QR depending on the account', async () => {
  const waiting = refund({ huyBoi: 'KHACH_HANG', lyDoHuy: '<i>Đặt nhầm</i>' });
  const ready = refund({ maYeuCau: 16, noiDungChuyenKhoan: 'HT16', trangThai: 'CHO_XU_LY', maNganHang: '970415',
    soTaiKhoan: '1029384756', tenChuTaiKhoan: 'NGUYEN VAN KHACH', qrUrl: 'https://img.vietqr.io/image/970415-1029384756-compact2.png?amount=125000&addInfo=HT16' });
  let reminded = null;
  const app = management('QUAN_LY', async call => {
    if (call.url.startsWith(TRANSFERS)) { return pageOf([waiting, ready]); }
    if (call.url.endsWith('/remind-bank')) { reminded = call.url; return { daNhacLuc: '2026-10-12T15:30:00' }; }
    return { cuaHangChoChi: 0, hoanTienCho: 1, hoanTienChoKhachKhai: 1, giaoDichLech: 0 };
  }, '?tab=refunds');
  await settle();

  assert.equal(pageCalls(app).find(call => call.url.startsWith(TRANSFERS)).url,
    TRANSFERS + '?loai=HOAN_TIEN_KHACH&page=0&size=20&trangThai=CHO_XU_LY');
  const rows = app.get('refundRows').querySelectorAll('tr');
  assert.equal(rows.length, 2);
  assert.match(rows[0].textContent, /Bên hủy: Khách/);
  assert.equal(rows[0].querySelector('i'), null, 'lý do hủy không được thành HTML');
  assert.equal(rows[0].querySelector('[data-remind-refund]').textContent, 'Gửi thông báo cập nhật STK');
  assert.equal(rows[1].querySelector('[data-refund]').textContent, 'Tạo mã VietQR');

  app.trigger(rows[0].querySelector('[data-remind-refund]'));
  await settle();
  assert.equal(reminded, BASE + '/refunds/15/remind-bank');

  app.set('refundKeyword', 'khach@example.com');
  app.trigger('refundKeyword', 'input');
  await settle();

  app.trigger(rows[1].querySelector('[data-refund]'));
  assert.ok(app.get('transferModal').classList.contains('show'));
  assert.equal(app.get('transferCode').textContent, 'HT16');
  assert.ok(shown(app, 'transferDone'));
});

test('transactions tab filters the ledger and shows the three summary numbers', async () => {
  const transaction = {
    maGiaoDich: 1, maGiaoDichCong: '700001', soTien: 125000, noiDung: 'MBVCB.1. OB7K2M9QXA <script>', thoiGianGiaoDich: '2026-10-12T15:36:21',
    tenNguoiChuyen: 'NGUYEN VAN KHACH', soTaiKhoanChuyen: '8888888888', maThanhToan: 'OB7K2M9QXA', ketQua: 'KHOP', ghiChu: null, ngayNhan: '2026-10-12T15:36:25'
  };
  const app = management('QUAN_LY', async call => {
    if (call.url.startsWith(BASE + '/transactions')) {
      return { content: [transaction], page: 0, size: 20, totalElements: 1, totalPages: 1,
        tomTat: { tongSo: 7, tongTienKhop: 500000, tongTienLech: 50000 } };
    }
    return { cuaHangChoChi: 0, hoanTienCho: 0, hoanTienChoKhachKhai: 0, giaoDichLech: 2 };
  }, '?tab=transactions');
  await settle();

  assert.ok(pageCalls(app).find(call => call.url.startsWith(BASE + '/transactions')).url.startsWith(BASE + '/transactions?page=0&size=20'));
  assert.equal(app.get('txTotal').textContent, '7');
  assert.match(app.get('txMatched').textContent, /500[.,]000/);
  assert.match(app.get('txMismatch').textContent, /50[.,]000/);
  const row = app.get('txRows').querySelector('tr');
  assert.match(row.textContent, /Khớp, đã tạo đơn/);
  assert.match(row.textContent, /12\/10\/2026 15:36/);
  assert.equal(row.querySelector('script'), null);

  app.set('txResult', 'CAN_HOAN_TIEN');
  app.set('txFrom', '2026-10-01');
  app.trigger('txResult', 'change');
  await settle();
  const last = app.calls.filter(call => call.url.startsWith(BASE + '/transactions')).at(-1).url;
  assert.match(last, /ketQua=CAN_HOAN_TIEN/);
  assert.match(last, /tuNgay=2026-10-01/);
});
