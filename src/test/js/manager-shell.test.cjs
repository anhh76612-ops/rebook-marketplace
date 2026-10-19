'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

// Cổng Quản lý: shell.js (chuyển mục không tải lại trang) và realtime.js (cập nhật theo tin đẩy).
// Dùng môi trường giả tối thiểu thay cho dom-harness, vì harness chưa có DOMParser, History và fetch trả HTML.

const JS = path.resolve(__dirname, '../../main/resources/static/js/management');
const read = name => fs.readFileSync(path.join(JS, name), 'utf8');
const tick = async () => { for (let i = 0; i < 6; i++) { await new Promise(resolve => setImmediate(resolve)); } };

function makeNode(extra = {}) {
  const node = {
    attrs: {}, children: [], innerHTML: '', textContent: '', parentNode: null,
    classes: new Set(), listeners: {},
    getAttribute(name) { return name in this.attrs ? this.attrs[name] : null; },
    setAttribute(name, value) { this.attrs[name] = String(value); },
    removeAttribute(name) { delete this.attrs[name]; },
    classList: null,
    addEventListener(type, fn) { (this.listeners[type] = this.listeners[type] || []).push(fn); },
    querySelectorAll() { return []; },
    replaceChild() { },
    ...extra
  };
  node.classList = {
    add: name => node.classes.add(name), remove: name => node.classes.delete(name),
    toggle: (name, force) => { if (force) { node.classes.add(name); } else { node.classes.delete(name); } },
    contains: name => node.classes.has(name)
  };
  return node;
}

// ---------------------------------------------------------------------------
// shell.js
// ---------------------------------------------------------------------------

function bootShell({ pages, failWith } = {}) {
  const events = [];
  const pushes = [];
  const replaces = [];
  const docListeners = {};
  const fetched = [];
  const hard = [];
  const scripts = [];

  const main = makeNode({ attrs: { id: 'managerMain', 'data-title': 'Tổng quan', 'data-menu': 'overview' }, innerHTML: 'trang-cu' });
  const heading = makeNode({ textContent: 'Tổng quan' });
  const updated = makeNode();
  const bar = makeNode({ style: {} });
  const menu = ['overview', 'payments'].map(name => makeNode({ attrs: { 'data-menu': name } }));
  menu[0].classes.add('active');

  const document = {
    title: 'Tổng quan - Cổng quản lý OldBook',
    body: makeNode({ style: { removeProperty() { } } }),
    getElementById: id => ({ managerMain: main, mgTitle: heading, mgUpdated: updated, mgProgress: bar, mgRefresh: null, mgSidebar: null })[id] || null,
    querySelectorAll: selector => selector === '.mg-nav [data-menu]' ? menu : [],
    createElement: () => makeNode(),
    addEventListener: (type, fn) => { (docListeners[type] = docListeners[type] || []).push(fn); },
    dispatchEvent: event => { events.push(event.type); (docListeners[event.type] || []).forEach(fn => fn(event)); }
  };
  main.querySelectorAll = selector => selector === 'script[data-page-script]' && main.pageScripts ? main.pageScripts : [];

  const location = { href: 'http://localhost:8080/management', pathname: '/management', search: '', origin: 'http://localhost:8080' };
  const window = {
    document, location, bootstrap: undefined, scrollTo() { },
    history: {
      state: null,
      pushState: (state, title, url) => { pushes.push(url); const u = new URL(url, location.origin); location.pathname = u.pathname; location.search = u.search; },
      replaceState: (state, title, url) => { replaces.push(url); }
    },
    addEventListener: (type, fn) => { docListeners['window:' + type] = [fn]; },
    setTimeout: fn => { fn(); return 1; }, clearTimeout() { },
    fetch: async url => {
      fetched.push(url);
      if (failWith === 'network') { throw new Error('offline'); }
      const page = pages[url];
      if (!page) { return { ok: false, status: 404, url: 'http://localhost:8080' + url, text: async () => '' }; }
      return { ok: true, status: 200, url: page.finalUrl || 'http://localhost:8080' + url, text: async () => page.html };
    },
    OldBook: { renderHeader() { events.push('renderHeader'); }, managerRealtime: { refreshBadges() { events.push('badges'); }, refreshAll() { } } }
  };
  Object.defineProperty(window.location, 'href', {
    get() { return 'http://localhost:8080' + location.pathname + location.search; },
    set(value) { hard.push(value); }
  });

  class FakeParser {
    parseFromString(html) {
      if (html.includes('NO-MAIN')) { return { title: '', getElementById: () => null }; }
      const title = (/<title>(.*?)<\/title>/.exec(html) || [])[1] || '';
      const next = makeNode({ attrs: { 'data-title': (/data-title="(.*?)"/.exec(html) || [])[1], 'data-menu': (/data-menu="(.*?)"/.exec(html) || [])[1] }, innerHTML: (/<main[^>]*>(.*?)<\/main>/.exec(html) || [])[1] });
      return { title, getElementById: id => id === 'managerMain' ? next : null };
    }
  }

  const context = vm.createContext({
    window, document, URL, URLSearchParams, DOMParser: FakeParser, Promise, console,
    CustomEvent: class { constructor(type) { this.type = type; } }
  });
  vm.runInContext(read('shell.js'), context, { filename: 'shell.js' });

  return {
    window, document, main, heading, menu, events, pushes, replaces, fetched, hard, location,
    click(link, extra = {}) {
      const ev = { button: 0, defaultPrevented: false, target: { closest: sel => sel === 'a[data-shell-link]' ? link : null }, preventDefault() { this.defaultPrevented = true; }, ...extra };
      (docListeners.click || []).forEach(fn => fn(ev));
      return ev;
    },
    popstate() { (docListeners['window:popstate'] || []).forEach(fn => fn({})); }
  };
}

function link(href, extra = {}) {
  return { href: 'http://localhost:8080' + href, target: '', ...extra };
}

const PAYMENTS_HTML = '<title>Thanh toán - Cổng quản lý</title><main data-title="Thanh toán &amp; Đối soát" data-menu="payments">trang-thanh-toan</main>';

test('bấm link thay nội dung, đổi tiêu đề, địa chỉ, mục đang chọn và phát manager:leave', async () => {
  const app = bootShell({ pages: { '/management/payments': { html: PAYMENTS_HTML } } });

  const ev = app.click(link('/management/payments'));
  await tick();

  assert.equal(ev.defaultPrevented, true);
  assert.equal(app.main.innerHTML, 'trang-thanh-toan');
  assert.equal(app.document.title, 'Thanh toán - Cổng quản lý');
  assert.equal(app.heading.textContent, 'Thanh toán &amp; Đối soát');
  assert.deepEqual(app.pushes, ['/management/payments']);
  assert.equal(app.menu[0].classes.has('active'), false);
  assert.equal(app.menu[1].classes.has('active'), true);
  assert.ok(app.events.indexOf('manager:leave') >= 0, 'phải dọn trang cũ trước khi thay');
  assert.ok(app.events.indexOf('manager:leave') < app.events.indexOf('renderHeader'));
  assert.deepEqual(app.hard, []);
});

test('Ctrl/Shift/chuột giữa, link ngoài miền, link mở tab mới để trình duyệt tự xử lý', async () => {
  const app = bootShell({ pages: { '/management/payments': { html: PAYMENTS_HTML } } });

  for (const extra of [{ ctrlKey: true }, { metaKey: true }, { shiftKey: true }, { button: 1 }]) {
    assert.equal(app.click(link('/management/payments'), extra).defaultPrevented, false);
  }
  assert.equal(app.click({ href: 'https://example.com/x', target: '' }).defaultPrevented, false);
  assert.equal(app.click(link('/management/payments', { target: '_blank' })).defaultPrevented, false);
  await tick();
  assert.deepEqual(app.fetched, []);
});

test('Back/Forward tải lại nội dung theo địa chỉ mới mà không thêm lịch sử', async () => {
  const app = bootShell({ pages: { '/management/payments': { html: PAYMENTS_HTML } } });
  app.location.pathname = '/management/payments';

  app.popstate();
  await tick();

  assert.deepEqual(app.fetched, ['/management/payments']);
  assert.deepEqual(app.pushes, []);
  assert.equal(app.main.innerHTML, 'trang-thanh-toan');
});

test('lỗi mạng, lỗi máy chủ hoặc trang không có #managerMain thì tải lại bình thường', async () => {
  const offline = bootShell({ pages: {}, failWith: 'network' });
  offline.click(link('/management/payments'));
  await tick();
  assert.deepEqual(offline.hard, ['/management/payments']);
  assert.equal(offline.main.innerHTML, 'trang-cu');

  const missing = bootShell({ pages: {} });
  missing.click(link('/management/payments'));
  await tick();
  assert.deepEqual(missing.hard, ['/management/payments']);

  const noMain = bootShell({ pages: { '/management/payments': { html: 'NO-MAIN login page' } } });
  noMain.click(link('/management/payments'));
  await tick();
  assert.deepEqual(noMain.hard, ['/management/payments']);
  assert.deepEqual(noMain.pushes, []);

  const redirected = bootShell({ pages: { '/management/payments': { html: PAYMENTS_HTML, finalUrl: 'https://sso.example.com/login' } } });
  redirected.click(link('/management/payments'));
  await tick();
  assert.deepEqual(redirected.hard, ['/management/payments']);
});

test('bấm lại đúng trang đang mở thì không tải lại', async () => {
  const app = bootShell({ pages: { '/management': { html: PAYMENTS_HTML } } });
  const ev = app.click(link('/management'));
  await tick();
  assert.equal(ev.defaultPrevented, true);
  assert.deepEqual(app.fetched, []);
});

// ---------------------------------------------------------------------------
// realtime.js
// ---------------------------------------------------------------------------

function bootRealtime() {
  const timers = [];
  const requests = [];
  const docListeners = {};
  const badgeNodes = { moderation: makeNode(), payments: makeNode() };
  let onChange = null;
  let listenerCount = 0;

  const OB = {
    auth: { isLoggedIn: () => true },
    request: async (method, url) => { requests.push(url); return { kiemDuyet: 3, thanhToan: 120 }; },
    notifications: { onChange: fn => { onChange = fn; listenerCount++; } }
  };
  const document = {
    addEventListener: (type, fn) => { docListeners[type] = fn; },
    querySelectorAll: selector => {
      const name = /data-badge="(.*?)"/.exec(selector);
      return name && badgeNodes[name[1]] ? [badgeNodes[name[1]]] : [];
    }
  };
  const window = {
    OldBook: OB, document,
    setTimeout: fn => { timers.push(fn); return timers.length; },
    clearTimeout: () => { timers.length = 0; }
  };
  const context = vm.createContext({ window, document, Promise, console, setTimeout: window.setTimeout, clearTimeout: window.clearTimeout });
  vm.runInContext(read('realtime.js'), context, { filename: 'realtime.js' });

  return {
    OB, requests, badgeNodes, timers,
    get listeners() { return listenerCount; },
    push: tin => onChange(tin),
    leave: () => docListeners['manager:leave'](),
    async flush() { const pending = timers.splice(0); pending.forEach(fn => fn()); await tick(); }
  };
}

test('gộp các tin trong 1 giây thành một lần tải cho mỗi phần', async () => {
  const app = bootRealtime();
  let payouts = 0;
  let overview = 0;
  app.OB.managerRealtime.on('payouts', () => { payouts++; });
  app.OB.managerRealtime.on('overview', () => { overview++; });
  await tick();
  app.requests.length = 0;

  for (let i = 0; i < 5; i++) { app.push({ type: 'DU_LIEU_THAY_DOI', loai: 'CAN_CHI_CUA_HANG' }); }
  assert.equal(app.timers.length, 1, 'chỉ một hẹn giờ cho cả 5 tin');
  await app.flush();

  assert.equal(payouts, 1);
  assert.equal(overview, 1);
  assert.equal(app.requests.filter(url => url === '/api/management/overview/badges').length, 1);
});

test('chỉ tải lại phần đúng với loại thông báo hoặc phan của tin VAN_HANH_THAY_DOI', async () => {
  const app = bootRealtime();
  const hit = { payouts: 0, refunds: 0, transactions: 0, moderation: 0, commissions: 0 };
  Object.keys(hit).forEach(name => app.OB.managerRealtime.on(name, () => { hit[name]++; }));

  app.push({ type: 'DU_LIEU_THAY_DOI', loai: 'GIAO_DICH_LECH' });
  await app.flush();
  assert.deepEqual(hit, { payouts: 0, refunds: 0, transactions: 1, moderation: 0, commissions: 0 });

  app.push({ type: 'DU_LIEU_THAY_DOI', loai: 'SACH_CHO_DUYET' });
  app.push({ type: 'DU_LIEU_THAY_DOI', loai: 'CAN_HOAN_TIEN' });
  await app.flush();
  assert.equal(hit.moderation, 1);
  assert.equal(hit.refunds, 1);
  assert.equal(hit.payouts, 0);

  app.push({ type: 'VAN_HANH_THAY_DOI', phan: 'commissions' });
  await app.flush();
  assert.equal(hit.commissions, 1);

  // Loại không liên quan và tin lạ bị bỏ qua.
  app.push({ type: 'DU_LIEU_THAY_DOI', loai: 'DON_HANG_MOI' });
  app.push({ type: 'PHIEN_THANH_TOAN_DOI' });
  assert.equal(app.timers.length, 0);
});

test('nối lại kênh đẩy thì tải lại mọi phần đang mở một lần', async () => {
  const app = bootRealtime();
  let loads = 0;
  ['payouts', 'refunds', 'overview'].forEach(name => app.OB.managerRealtime.on(name, () => { loads++; }));

  app.push({ resync: true });
  await app.flush();
  assert.equal(loads, 3);
});

test('chuyển mục xóa đăng ký cũ và không cộng dồn listener trên kênh đẩy', async () => {
  const app = bootRealtime();
  let old = 0;
  let fresh = 0;
  app.OB.managerRealtime.on('payouts', () => { old++; });
  app.leave();
  app.OB.managerRealtime.on('payouts', () => { fresh++; });

  for (let page = 0; page < 5; page++) {
    app.OB.managerRealtime.on('overview', () => { });
    app.leave();
  }
  app.OB.managerRealtime.on('payouts', () => { fresh++; });

  app.push({ type: 'DU_LIEU_THAY_DOI', loai: 'CAN_CHI_CUA_HANG' });
  await app.flush();

  assert.equal(app.listeners, 1, 'realtime.js chỉ đăng ký một listener duy nhất');
  assert.equal(old, 0);
  assert.equal(fresh, 1);
});

test('huy hiệu ở sidebar lấy từ API và ẩn khi bằng 0', async () => {
  const app = bootRealtime();
  await tick();
  assert.equal(app.badgeNodes.moderation.textContent, '3');
  assert.equal(app.badgeNodes.moderation.classes.has('d-none'), false);
  assert.equal(app.badgeNodes.payments.textContent, '99+');
});
