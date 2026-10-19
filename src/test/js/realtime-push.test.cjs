'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { boot, settle } = require('./dom-harness.cjs');

// Các trang tự cập nhật khi máy chủ đẩy tin qua WebSocket, thay cho việc hỏi lại theo chu kỳ.

const TICKET_URL = '/api/thong-bao/ticket';

function page(template, scripts, handler, role, options) {
  const app = boot(template, scripts, async call => {
    if (call.url === '/api/thong-bao/chua-doc') { return { soLuong: 0 }; }
    if (call.url === TICKET_URL) { return { ticket: 've-1' }; }
    return handler(call);
  }, role, options);

  const sockets = [];
  class FakeSocket {
    constructor(url) { this.url = url; this.readyState = 1; sockets.push(this); }
    send() { }
    close() { this.closed = true; }
  }
  app.window.WebSocket = FakeSocket;

  let queued = [];
  const intervals = [];
  app.window.setTimeout = fn => { queued.push(fn); return queued.length; };
  app.window.clearTimeout = () => { };
  app.window.setInterval = (fn, ms) => { intervals.push(ms); return 0; };

  return Object.assign(app, {
    sockets,
    intervals,
    pending: () => queued.length,
    count: prefix => app.calls.filter(call => call.url.startsWith(prefix)).length,
    async open() {
      app.window.OldBook.notifications.connect();
      await settle();
    },
    async flush() {
      const run = queued;
      queued = [];
      run.forEach(fn => fn());
      await settle();
    },
    push(message) { sockets[sockets.length - 1].onmessage({ data: JSON.stringify(message) }); }
  });
}

function changed(loai) { return { type: 'DU_LIEU_THAY_DOI', loai }; }

// ---------------------------------------------------------------------------
// Trang quét mã: không còn hỏi lại mỗi 3 giây
// ---------------------------------------------------------------------------

function paySession(overrides = {}) {
  return {
    maPhien: 7, maThanhToan: 'OB7K2M9QXA', soTien: 125000, trangThai: 'CHO_THANH_TOAN',
    hetHanLuc: '2026-10-12T15:05:00', conLaiGiay: 287, qrUrl: 'https://img.vietqr.io/x.png',
    maNganHang: '970422', soTaiKhoan: '0123456789', tenTaiKhoan: 'OLDBOOK', maDH: null,
    ...overrides
  };
}

function payApp(state) {
  return page('payment/pay.html', ['banks.js', 'notifications.js', 'payment-pay.js'],
    async call => (call.url === '/api/payment/sessions/7' ? state.current : null),
    'KHACH_HANG', { pathname: '/payment/pay', search: '?maPhien=7' });
}

test('pay page does not poll on a timer and asks once when the server reports its session', async () => {
  const state = { current: paySession() };
  const app = payApp(state);
  await settle();
  await app.open();

  assert.deepEqual(app.intervals, [1000], 'only the countdown runs on a timer');
  assert.equal(app.count('/api/payment/sessions/7'), 1);

  state.current = paySession({ trangThai: 'DA_THANH_TOAN', maDH: 42 });
  app.push({ type: 'PHIEN_THANH_TOAN_DOI', maPhien: 7, trangThai: 'DA_THANH_TOAN' });
  await settle();

  assert.equal(app.count('/api/payment/sessions/7'), 2);
  assert.equal(app.window.location.href, '/order-success?maDH=42');
});

test('pay page shows the expired message as soon as the server reports it', async () => {
  const state = { current: paySession() };
  const app = payApp(state);
  await settle();
  await app.open();

  state.current = paySession({ trangThai: 'HET_HAN', conLaiGiay: 0 });
  app.push({ type: 'PHIEN_THANH_TOAN_DOI', maPhien: 7, trangThai: 'HET_HAN' });
  await settle();

  assert.ok(!app.get('payMessage').classList.contains('d-none'));
  assert.match(app.get('payMessageTitle').textContent, /hết hạn/);
});

test('pay page ignores other sessions and unrelated pushes, and asks again after a reconnect', async () => {
  const state = { current: paySession() };
  const app = payApp(state);
  await settle();
  await app.open();

  app.push({ type: 'PHIEN_THANH_TOAN_DOI', maPhien: 8, trangThai: 'DA_HUY' });
  app.push(changed('CAN_HOAN_TIEN'));
  app.push({ type: 'THONG_BAO_MOI', chuaDoc: 1, thongBao: { maThongBao: 1, tieuDe: 'Khác' } });
  await settle();
  assert.equal(app.count('/api/payment/sessions/7'), 1);

  app.sockets[0].onopen();
  app.sockets[0].onclose();
  await app.flush();
  app.sockets[1].onopen();
  await settle();
  assert.equal(app.count('/api/payment/sessions/7'), 2, 'changes may have been missed while disconnected');
});

// ---------------------------------------------------------------------------
// Các trang tải lại khi có thông báo thuộc loại liên quan
// ---------------------------------------------------------------------------

function pageOf(content) {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 };
}

// Trang Thanh toán của Quản lý nhận tin qua js/management/realtime.js; xem manager-shell.test.cjs.

test('refund page reloads when a refund is created or paid', async () => {
  const app = page('payment/refunds.html', ['banks.js', 'bank-account-form.js', 'notifications.js', 'payment-refunds.js'],
    async () => [], 'KHACH_HANG', { pathname: '/payment/refunds' });
  await settle();
  await app.open();
  assert.equal(app.count('/api/payment/refunds'), 1);

  app.push(changed('DA_HOAN_TIEN'));
  await app.flush();
  assert.equal(app.count('/api/payment/refunds'), 2);

  app.push(changed('CAN_TAI_KHOAN_HOAN_TIEN'));
  await app.flush();
  assert.equal(app.count('/api/payment/refunds'), 3);

  app.push(changed('DA_CHI_CUA_HANG'));
  assert.equal(app.pending(), 0);
});

test('store moderation reloads both lists when a store is waiting for review', async () => {
  const app = page('store-moderation.html', ['notifications.js', 'store-moderation.js'],
    async () => pageOf([]), 'QUAN_LY', { pathname: '/store-moderation' });
  await settle();
  await app.open();
  assert.equal(app.count('/api/store/kiem-duyet/cua-hang'), 1);
  assert.equal(app.count('/api/store/kiem-duyet/sach'), 1);

  app.push(changed('GIAN_HANG_CHO_DUYET'));
  await app.flush();
  assert.equal(app.count('/api/store/kiem-duyet/cua-hang'), 2);
  assert.equal(app.count('/api/store/kiem-duyet/sach'), 2);

  app.push(changed('CUA_HANG_DUOC_DUYET'));
  assert.equal(app.pending(), 0);
});

test('store owner page reloads when the store or a book is approved or rejected', async () => {
  const store = { maCH: 1, tenCuaHang: 'Tiệm', trangThaiDuyet: 'DA_DUYET' };
  const app = page('store-books.html', ['notifications.js', 'store.js'], async call => {
    if (call.url === '/api/store/me') { return store; }
    return { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 };
  }, 'CHU_CUA_HANG', { pathname: '/store/books' });
  await settle();
  await app.open();
  const storeLoads = () => app.calls.filter(call => call.url === '/api/store/me').length;
  assert.equal(storeLoads(), 1);
  assert.equal(app.count('/api/store/me/sach'), 1);

  app.push(changed('SACH_BI_TU_CHOI'));
  await app.flush();
  assert.equal(storeLoads(), 1, 'a book decision only reloads the book list');
  assert.equal(app.count('/api/store/me/sach'), 2);

  app.push(changed('CUA_HANG_DUOC_DUYET'));
  await app.flush();
  assert.equal(storeLoads(), 2);

  app.push(changed('DON_HANG_MOI'));
  assert.equal(app.pending(), 0);
});

// ---------------------------------------------------------------------------
// Cổng đơn vị vận chuyển: kênh riêng theo mã hãng, không đăng nhập
// ---------------------------------------------------------------------------

function shippingApp() {
  const calls = [];
  const listeners = {};
  const sockets = [];
  const intervals = [];
  let queued = [];
  const state = { modalOpen: false };

  const element = () => ({
    textContent: '', innerHTML: '', value: '', classList: { add() { }, remove() { }, toggle() { } }, setAttribute() { }
  });
  const document = {
    hidden: false,
    querySelector: selector => {
      if (selector === '[data-shipping-page]') { return { getAttribute: () => 'GHN' }; }
      return selector === '.modal.show' && state.modalOpen ? {} : null;
    },
    getElementById: element,
    addEventListener: (type, callback) => { (listeners[type] = listeners[type] || []).push(callback); }
  };
  class FakeSocket {
    constructor(url) { this.url = url; this.readyState = 1; sockets.push(this); }
    send() { }
    close() { this.closed = true; }
  }
  const window = {
    document,
    WebSocket: FakeSocket,
    location: { origin: 'http://localhost:8080' },
    addEventListener() { },
    setTimeout: fn => { queued.push(fn); return queued.length; },
    clearTimeout() { },
    setInterval: (fn, ms) => { intervals.push(ms); return 0; },
    clearInterval() { }
  };
  const OldBook = {
    request: async (method, url) => { calls.push(url); return []; },
    toast() { }, escapeHtml: value => String(value), formatMoney: value => String(value)
  };
  const context = vm.createContext({ window, document, OldBook, bootstrap: {}, Math, JSON, Number, encodeURIComponent });
  vm.runInContext(
    fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/shipping/dashboard.js'), 'utf8'),
    context, { filename: 'shipping/dashboard.js' });

  return {
    state, sockets, intervals,
    pending: () => queued.length,
    loads: () => calls.filter(url => url === '/api/shipping/GHN/requests').length,
    start: async () => { listeners.DOMContentLoaded.forEach(callback => callback()); await settle(); },
    async flush() {
      const run = queued;
      queued = [];
      run.forEach(fn => fn());
      await settle();
    },
    push(message) { sockets[sockets.length - 1].onmessage({ data: JSON.stringify(message) }); }
  };
}

test('shipping portal opens its own socket for its carrier code and reloads when the server pushes a change', async () => {
  const app = shippingApp();
  await app.start();

  assert.equal(app.sockets.length, 1);
  assert.equal(app.sockets[0].url, 'ws://localhost:8080/ws/van-chuyen?maCode=GHN');
  assert.deepEqual(app.intervals.filter(ms => ms !== 120000), [], 'no periodic refresh any more');
  assert.equal(app.loads(), 1);

  app.push({ type: 'VAN_CHUYEN_THAY_DOI' });
  app.push({ type: 'VAN_CHUYEN_THAY_DOI' });
  assert.equal(app.pending(), 1, 'bursts are merged into one reload');
  await app.flush();
  assert.equal(app.loads(), 2);

  app.push({ type: 'KHAC' });
  app.sockets[0].onmessage({ data: 'không phải JSON' });
  assert.equal(app.pending(), 0);
});

test('shipping portal waits for an open dialog to close, and reconnects then reloads', async () => {
  const app = shippingApp();
  await app.start();

  app.state.modalOpen = true;
  app.push({ type: 'VAN_CHUYEN_THAY_DOI' });
  await app.flush();
  assert.equal(app.loads(), 1, 'a dialog being edited is not wiped by a reload');
  app.state.modalOpen = false;
  await app.flush();
  assert.equal(app.loads(), 2);

  app.sockets[0].onopen();
  app.sockets[0].onclose();
  await app.flush();
  assert.equal(app.sockets.length, 2, 'reconnects by itself');
  app.sockets[1].onopen();
  await app.flush();
  assert.equal(app.loads(), 3, 'changes may have been missed while disconnected');
});
