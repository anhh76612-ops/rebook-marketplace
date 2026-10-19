'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { File } = require('node:buffer');
const { boot, settle } = require('./dom-harness.cjs');

// Khối nhập tài khoản / ảnh QR dùng chung và hộp thoại hủy đơn chuyển khoản có tài khoản hoàn ưu tiên.

const QR_URL = 'https://res.cloudinary.com/oldbook/image/upload/oldbook/bank-qr/mau.png';

test('hợp lệ khi đủ tài khoản HOẶC có ảnh QR; thiếu một phần tài khoản thì báo lỗi', () => {
  const { OldBook } = boot('payment/refunds.html', ['banks.js', 'bank-account-form.js'], async () => [], 'KHACH_HANG').window;
  const validate = OldBook.bankForm.validate;

  assert.equal(validate({ maNganHang: '970415', soTaiKhoan: '1029384756', tenChuTaiKhoan: 'NGUYEN VAN A', qrNganHangUrl: null }), '');
  assert.equal(validate({ maNganHang: null, soTaiKhoan: null, tenChuTaiKhoan: null, qrNganHangUrl: QR_URL }), '');
  assert.equal(validate({ maNganHang: '970415', soTaiKhoan: '1029384756', tenChuTaiKhoan: 'NGUYEN VAN A', qrNganHangUrl: QR_URL }), '');
  assert.match(validate({ maNganHang: null, soTaiKhoan: null, tenChuTaiKhoan: null, qrNganHangUrl: null }), /tài khoản hoặc tải ảnh/);
  assert.match(validate({ maNganHang: '970415', soTaiKhoan: '', tenChuTaiKhoan: '', qrNganHangUrl: QR_URL }), /nhập đủ/);
  assert.match(validate({ maNganHang: '970415', soTaiKhoan: '12 34', tenChuTaiKhoan: 'A', qrNganHangUrl: null }), /từ 4 đến 30/);
});

function mount(handler) {
  const app = boot('payment/refunds.html', ['banks.js', 'bank-account-form.js'], handler, 'KHACH_HANG');
  const form = app.window.OldBook.bankForm.create(app.get('refundBankForm'), { idPrefix: 't', uploadUrl: '/api/payment/bank-qr' });
  return { app, form };
}

test('tên chủ tài khoản được chuẩn hóa thành chữ in hoa không dấu và có ghi chú khuyến khích', () => {
  const { app, form } = mount(async () => []);

  app.set('tBank', '970436');
  app.set('tAccount', ' 0071000123456 ');
  app.set('tOwner', '  Trần Thị Đào ');
  assert.deepEqual({ ...form.getValues() }, {
    maNganHang: '970436', soTaiKhoan: '0071000123456', tenChuTaiKhoan: 'TRAN THI DAO', qrNganHangUrl: null
  });
  assert.equal(form.validate(), '');
  assert.match(app.get('refundBankForm').textContent, /Khuyến khích nhập đầy đủ thông tin tài khoản và tải kèm ảnh mã QR ngân hàng/);
});

test('tải ảnh QR: gọi API upload, hiện ảnh và cho phép bỏ; ảnh quá lớn hoặc không phải ảnh bị chặn tại chỗ', async () => {
  const { app, form } = mount(async call => (call.url === '/api/payment/bank-qr' ? { url: QR_URL } : []));
  const input = app.get('tQrFile');

  const pick = file => { input.files = [file]; app.trigger(input, 'change'); };

  pick(new File(['x'], 'doc.pdf', { type: 'application/pdf' }));
  assert.match(app.get('tQrStatus').textContent, /Chỉ nhận file ảnh/);
  pick(new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'big.png', { type: 'image/png' }));
  assert.match(app.get('tQrStatus').textContent, /5 MB/);
  assert.equal(app.calls.filter(call => call.url === '/api/payment/bank-qr').length, 0);

  pick(new File(['png'], 'qr.png', { type: 'image/png' }));
  assert.match(form.validate(), /đang được tải lên/);
  await settle();
  assert.equal(app.calls.filter(call => call.url === '/api/payment/bank-qr').length, 1);
  assert.equal(form.getValues().qrNganHangUrl, QR_URL);
  assert.equal(form.validate(), '', 'chỉ có ảnh QR là hợp lệ');
  assert.equal(app.get('tQrImg').getAttribute('src'), QR_URL);

  app.trigger('tQrRemove');
  assert.equal(form.getValues().qrNganHangUrl, null);
  assert.match(form.validate(), /tài khoản hoặc tải ảnh/);
});

// ---------------------------------------------------------------------------
// Hộp thoại hủy đơn chuyển khoản
// ---------------------------------------------------------------------------

const REASON = 'Tôi đặt nhầm cuốn sách này và muốn hủy đơn để chọn lại bản khác, mong cửa hàng thông cảm giúp.';

function cancelApp(info, pay = 'CHUYEN_KHOAN') {
  const store = { maDHCH: 2, tenCuaHang: 'Hiệu sách', trangThaiDonHang: 'CHO_XU_LY', items: [{ maSach: 3, tenSach: 'Sách cũ', donGia: 50000, soLuong: 1 }] };
  const cancelled = { maDH: 1, phuongThucTT: pay, storeOrders: [{ ...store, trangThaiDonHang: 'DA_HUY' }] };
  const app = boot('orders.html', ['order-tracking.js', 'banks.js', 'bank-account-form.js'], async call => {
    if (call.url === '/api/order/orders') { return [{ maDH: 1, phuongThucTT: pay, storeOrders: [store] }]; }
    if (call.url === '/api/payment/orders/1/refund-account' && call.method === 'GET') { return info; }
    if (call.url === '/api/payment/orders/1/refund-account') { return info; }
    if (call.url.endsWith('/cancel')) { return cancelled; }
    return null;
  }, 'KHACH_HANG', { runInline: true });
  const modalOpen = () => app.get('orderCancelModal').classList.contains('show');
  const hidden = id => app.get(id).classList.contains('d-none');
  return { app, modalOpen, hidden, open: async () => {
    await settle();
    app.trigger(app.document.querySelector('[data-tab="CHO_XU_LY"]'));
    app.trigger(app.document.querySelector('button[data-act="cancel-order"]'));
    await settle();
    app.set('orderCancelReason', REASON);
  } };
}

const KNOWN = { chuyenKhoan: true, maNganHangDaChuyen: '970415', soTaiKhoanDaChuyenRutGon: '***8888', tenNguoiChuyen: 'NGUYEN VAN KHACH' };
const UNKNOWN = { chuyenKhoan: true };

test('hủy đơn đã biết tài khoản người chuyển: hiện sẵn "hoàn về tài khoản đã chuyển", hủy thẳng không cảnh báo', async () => {
  const { app, hidden, open } = cancelApp(KNOWN);
  await open();

  assert.ok(!hidden('orderRefundBox'));
  assert.match(app.get('orderRefundKnown').textContent, /Tiền sẽ hoàn về tài khoản đã chuyển: VietinBank \*\*\*8888 – NGUYEN VAN KHACH/);
  assert.ok(hidden('orderRefundForm'), 'form chỉ mở khi khách chọn hoàn về tài khoản khác');

  app.trigger('orderCancelForm', 'submit');
  await settle();
  assert.ok(hidden('orderRefundWarn'));
  assert.equal(app.calls.filter(call => call.url.endsWith('/refund-account') && call.method === 'PUT').length, 0);
  assert.equal(app.calls.filter(call => call.url.endsWith('/cancel')).length, 1);
});

test('chọn hoàn về tài khoản khác: lưu tài khoản ưu tiên rồi mới gọi API hủy, không sửa API hủy', async () => {
  const { app, hidden, open } = cancelApp(KNOWN);
  await open();

  app.trigger('orderRefundOther');
  assert.ok(!hidden('orderRefundForm'));
  app.set('orderRefundBank', '970436');
  app.set('orderRefundAccount', '0071000123456');
  app.set('orderRefundOwner', 'Trần Thị B');
  app.trigger('orderCancelForm', 'submit');
  await settle();

  const calls = app.calls.filter(call => call.url.endsWith('/refund-account') && call.method === 'PUT' || call.url.endsWith('/cancel'));
  assert.deepEqual(calls.map(call => [call.method, call.url]), [
    ['PUT', '/api/payment/orders/1/refund-account'], ['POST', '/api/order/orders/store-orders/2/cancel']
  ]);
  assert.deepEqual({ ...calls[0].body }, { maNganHang: '970436', soTaiKhoan: '0071000123456', tenChuTaiKhoan: 'TRAN THI B', qrNganHangUrl: null });
  assert.deepEqual({ ...calls[1].body }, { lyDo: REASON });
});

test('không có tài khoản nào: cảnh báo, "Quay lại để nhập" đưa con trỏ vào ô ngân hàng, "Đồng ý" thì hủy', async () => {
  const { app, hidden, open } = cancelApp(UNKNOWN);
  await open();
  assert.ok(!hidden('orderRefundForm'), 'chưa biết tài khoản thì mở sẵn form');

  app.trigger('orderCancelForm', 'submit');
  await settle();
  assert.ok(!hidden('orderRefundWarn'));
  assert.match(app.get('orderRefundWarn').textContent, /Bạn chưa nhập tài khoản ngân hàng nhận hoàn tiền/);
  assert.equal(app.calls.filter(call => call.url.endsWith('/cancel')).length, 0);

  app.trigger('orderRefundBack');
  assert.ok(hidden('orderRefundWarn'));
  assert.equal(app.document.activeElement.id, 'orderRefundBank');
  assert.equal(app.calls.filter(call => call.url.endsWith('/cancel')).length, 0);

  app.trigger('orderCancelForm', 'submit');
  await settle();
  assert.ok(!hidden('orderRefundWarn'));
  app.trigger('orderRefundAgree');
  await settle();
  assert.equal(app.calls.filter(call => call.url.endsWith('/cancel')).length, 1);
  assert.equal(app.calls.filter(call => call.url.endsWith('/refund-account') && call.method === 'PUT').length, 0);
});

test('đơn COD không có khối nhận lại tiền', async () => {
  const { app, hidden, open } = cancelApp({ chuyenKhoan: false }, 'COD');
  await open();

  assert.ok(hidden('orderRefundBox'));
  assert.equal(app.calls.filter(call => call.url.includes('/refund-account')).length, 0);
  app.trigger('orderCancelForm', 'submit');
  await settle();
  assert.equal(app.calls.filter(call => call.url.endsWith('/cancel')).length, 1);
});
