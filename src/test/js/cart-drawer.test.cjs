'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { boot, settle, event } = require('./dom-harness.cjs');

const SELECTED_KEY = 'ob_checkout_items';

function item(id, overrides = {}) {
  return {
    maCTGioHang: id, maSach: id + 100, tenSach: 'Sách ' + id, tacGia: 'Tác giả ' + id, tenCuaHang: 'Tiệm A',
    donGia: 50000, soLuong: 1, thanhTien: 50000, soLuongTon: 5, coTheMua: true, canhBao: null, ...overrides
  };
}

const sampleCart = () => ({
  soMuc: 3,
  items: [
    item(1, { soLuong: 2, thanhTien: 100000 }),
    item(2, { tenCuaHang: 'Tiệm B', donGia: 30000, thanhTien: 30000 }),
    item(3, { coTheMua: false, canhBao: 'Sách đã ngừng bán', soLuongTon: 0 })
  ]
});

// Thymeleaf mới là nơi đổi th:href thành href; ở đây làm thay để các link có địa chỉ thật như trên trang.
function resolveLinks(document) {
  for (const link of document.querySelectorAll('a')) {
    const template = link.getAttribute('th:href');
    if (template) { link.setAttribute('href', template.slice(2, -1)); }
  }
}

function drawerApp(handler, role = 'KHACH_HANG', options = {}) {
  const app = boot('fragments/cart-drawer.html', ['cart.js', 'cart-drawer.js'], async call => {
    if (call.url === '/api/profile') { return { hoTen: 'Người mua' }; }
    return handler(call);
  }, role, { setupDocument: resolveLinks, ...options });

  app.opened = 0;
  app.session = new Map();
  app.window.sessionStorage = {
    getItem: key => app.session.get(key) || null,
    setItem: (key, value) => app.session.set(key, value)
  };
  const drawer = app.get('cartDrawer');
  app.window.bootstrap.Offcanvas = {
    getOrCreateInstance: () => ({ show() { app.opened++; drawer.dispatchEvent(event('show.bs.offcanvas')); } })
  };
  app.fab = app.document.querySelector('[data-cart-fab]');
  app.state = name => app.document.querySelector('[data-cart-state="' + name + '"]');
  app.lines = () => app.document.querySelectorAll('.cart-line');
  app.text = selector => app.document.querySelector(selector).textContent;
  return app;
}

const visible = element => !element.classList.contains('d-none');

test('the floating button opens the drawer, which lists the cart grouped by shop with buyable lines preselected', async () => {
  const app = drawerApp(async call => call.url === '/api/order/cart' ? sampleCart() : null);
  await settle();
  assert.equal(app.calls.filter(call => call.url === '/api/order/cart').length, 1, 'only the badge request before opening');

  app.trigger(app.fab.querySelector('i'));
  await settle();

  assert.equal(app.opened, 1);
  assert.deepEqual(app.redirects, [], 'the link to /cart no longer leaves the page');
  assert.equal(app.calls.filter(call => call.url === '/api/order/cart').length, 2);
  assert.ok(visible(app.state('list')));
  // Hai dòng của Tiệm A đứng chung một nhóm dù trong giỏ chúng không nằm liền nhau.
  assert.deepEqual(app.document.querySelectorAll('.cart-shop').map(shop => shop.textContent), ['Tiệm A', 'Tiệm B']);
  assert.deepEqual(app.lines().map(line => line.getAttribute('data-id')), ['1', '3', '2']);

  const checks = app.document.querySelectorAll('.cart-line-check');
  assert.deepEqual(checks.map(check => check.checked), [true, false, true]);
  assert.deepEqual(checks.map(check => check.disabled), [false, true, false]);
  assert.match(app.lines()[1].textContent, /Sách đã ngừng bán/);

  // Dòng không mua được không được chọn nên không tính vào tạm tính.
  assert.equal(app.text('[data-cart-selected]'), '2 sản phẩm');
  assert.equal(app.text('[data-cart-total]'), '130,000 đ');
  assert.equal(app.document.querySelector('[data-cart-checkout]').disabled, false);
  assert.ok(visible(app.document.querySelector('[data-cart-foot]')));
  assert.ok(app.document.querySelectorAll('.cart-count').every(badge => badge.textContent === '3'));
});

test('book titles and shop names are escaped and unsafe images are dropped', async () => {
  const app = drawerApp(async call => call.url === '/api/order/cart' ? {
    soMuc: 1,
    items: [item(1, { tenSach: '<img src=x onerror=unsafe()>', tenCuaHang: '<script>unsafe()</script>', hinhAnhUrl: 'javascript:unsafe()' })]
  } : null);
  await settle();
  app.trigger(app.fab);
  await settle();

  const list = app.document.querySelector('[data-cart-list]');
  assert.equal(list.querySelector('script'), null);
  assert.equal(list.querySelector('img'), null);
  assert.equal(list.querySelector('.cart-line-title').textContent, '<img src=x onerror=unsafe()>');
});

test('quantity and remove buttons call the cart API and redraw from the cart the server returns', async () => {
  let cart = sampleCart();
  const app = drawerApp(async call => {
    if (call.method === 'PUT') {
      cart.items[0] = item(1, { soLuong: call.body.soLuong, thanhTien: 50000 * call.body.soLuong });
      return cart;
    }
    if (call.method === 'DELETE') {
      cart = { soMuc: 2, items: cart.items.filter(line => line.maCTGioHang !== 2) };
      return cart;
    }
    return cart;
  });
  await settle();
  app.trigger(app.fab);
  await settle();

  app.trigger(app.lines()[0].querySelector('[data-cart-plus]'));
  await settle();
  const update = app.calls.find(call => call.method === 'PUT');
  assert.equal(update.url, '/api/order/cart/items/1');
  assert.deepEqual(update.body, { soLuong: 3 });
  assert.equal(app.lines()[0].querySelector('.cart-qty-value').textContent, '3');
  assert.equal(app.text('[data-cart-total]'), '180,000 đ');

  app.trigger(app.lines()[2].querySelector('[data-cart-remove]'));
  await settle();
  assert.equal(app.calls.find(call => call.method === 'DELETE').url, '/api/order/cart/items/2');
  assert.equal(app.lines().length, 2);
  assert.equal(app.text('[data-cart-selected]'), '1 sản phẩm');
  assert.ok(app.document.querySelectorAll('.cart-count').every(badge => badge.textContent === '2'));

  // Số lượng 1 thì không giảm được nữa; dòng không mua được thì không tăng được.
  const blocked = app.lines()[1];
  assert.equal(blocked.querySelector('[data-cart-minus]').disabled, true);
  assert.equal(blocked.querySelector('[data-cart-plus]').disabled, true);
});

test('a rejected change keeps the drawer open, explains why and reloads the real cart', async () => {
  const app = drawerApp(async call => call.method === 'PUT'
    ? { httpStatus: 400, data: null, message: 'Chỉ còn 2 cuốn trong kho' }
    : sampleCart());
  await settle();
  app.trigger(app.fab);
  await settle();
  const loadsBefore = app.calls.filter(call => call.url === '/api/order/cart' && call.method === 'GET').length;

  app.trigger(app.lines()[0].querySelector('[data-cart-plus]'));
  await settle();

  const alert = app.document.querySelector('[data-cart-alert]');
  assert.ok(visible(alert));
  assert.equal(alert.textContent, 'Chỉ còn 2 cuốn trong kho');
  assert.equal(app.calls.filter(call => call.url === '/api/order/cart' && call.method === 'GET').length, loadsBefore + 1);
  assert.equal(app.lines()[0].classList.contains('is-busy'), false);
  assert.equal(app.window.OldBook.auth.getToken() !== null, true);
});

test('checkout hands only the selected lines to the checkout page under the key the cart page uses', async () => {
  const app = drawerApp(async call => call.url === '/api/order/cart' ? sampleCart() : null);
  await settle();
  app.trigger(app.fab);
  await settle();

  const second = app.lines()[2].querySelector('.cart-line-check');
  second.checked = false;
  app.trigger(second, 'change');
  assert.equal(app.text('[data-cart-selected]'), '1 sản phẩm');
  assert.equal(app.text('[data-cart-total]'), '100,000 đ');

  app.trigger(app.document.querySelector('[data-cart-checkout]'));
  assert.deepEqual(JSON.parse(app.session.get(SELECTED_KEY)), [1]);
  assert.deepEqual(app.redirects, ['/checkout']);

  // Bỏ chọn hết thì không đặt hàng được.
  const first = app.lines()[0].querySelector('.cart-line-check');
  first.checked = false;
  app.trigger(first, 'change');
  assert.equal(app.document.querySelector('[data-cart-checkout]').disabled, true);
  app.trigger(app.document.querySelector('[data-cart-checkout]'));
  assert.deepEqual(app.redirects, ['/checkout']);
});

test('empty cart and load failure each show their own state without the order footer', async () => {
  const empty = drawerApp(async call => call.url === '/api/order/cart' ? { soMuc: 0, items: [] } : null);
  await settle();
  empty.trigger(empty.fab);
  await settle();
  assert.ok(visible(empty.state('empty')));
  assert.equal(visible(empty.document.querySelector('[data-cart-foot]')), false);
  assert.equal(empty.text('[data-cart-count-line]'), 'Chưa có cuốn nào');

  let fail = true;
  const failing = drawerApp(async call => {
    if (call.url !== '/api/order/cart') { return null; }
    return fail ? { httpStatus: 500, data: null, message: 'Máy chủ đang bận' } : sampleCart();
  });
  await settle();
  failing.trigger(failing.fab);
  await settle();
  assert.ok(visible(failing.state('error')));
  assert.equal(failing.text('[data-cart-error-text]'), 'Máy chủ đang bận');
  assert.equal(visible(failing.document.querySelector('[data-cart-foot]')), false);

  fail = false;
  failing.trigger(failing.document.querySelector('[data-cart-retry]'));
  await settle();
  assert.ok(visible(failing.state('list')));
  assert.equal(failing.lines().length, 3);
});

test('the full-cart link, guests, other roles and the cart and checkout pages keep ordinary navigation', async () => {
  const cartApi = async call => call.url === '/api/order/cart' ? sampleCart() : null;

  // Link "Xem giỏ hàng đầy đủ" trong ngăn phải dẫn tới trang /cart thật.
  const customer = drawerApp(cartApi);
  await settle();
  const fullCart = customer.document.querySelector('[data-cart-page]');
  const click = event('click');
  fullCart.dispatchEvent(click);
  assert.equal(click.defaultPrevented, false);
  assert.equal(customer.opened, 0);

  // Khách vãng lai: auth.js đưa về trang đăng nhập, ngăn không mở.
  const guest = drawerApp(cartApi, null);
  await settle();
  guest.trigger(guest.fab);
  assert.equal(guest.opened, 0);
  assert.deepEqual(guest.redirects, ['/login?redirect=%2Fcart']);

  // Vai trò khác không có giỏ hàng.
  const seller = drawerApp(cartApi, 'CHU_CUA_HANG');
  await settle();
  const sellerClick = event('click');
  seller.fab.dispatchEvent(sellerClick);
  assert.equal(sellerClick.defaultPrevented, false);
  assert.equal(seller.opened, 0);

  // Trên /cart và /checkout: nút nổi ẩn, link /cart đi như thường.
  for (const pathname of ['/cart', '/checkout']) {
    const page = drawerApp(cartApi, 'KHACH_HANG', { pathname });
    await settle();
    assert.equal(page.fab.classList.contains('d-none'), true, pathname);
    const pageClick = event('click');
    page.fab.dispatchEvent(pageClick);
    assert.equal(pageClick.defaultPrevented, false, pathname);
    assert.equal(page.opened, 0, pathname);
  }
});
