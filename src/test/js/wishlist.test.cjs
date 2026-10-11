'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { boot, settle } = require('./dom-harness.cjs');

function book(id = 42, overrides = {}) {
  return {
    maSach: id, tenSach: 'Đắc nhân tâm', tacGia: 'Dale Carnegie', tenCuaHang: 'Sách cũ',
    soLuongTon: 2, giaBanCu: 65000, doMoiPercent: 95, ...overrides
  };
}

function appFor(template, handler, role = 'KHACH_HANG') {
  const app = boot(template, ['book-cards.js', 'cart.js', 'wishlist.js'], async call => {
    if (call.url === '/api/profile') { return { hoTen: 'Người mua' }; }
    if (call.url === '/api/order/cart' && call.method === 'GET') { return { soMuc: 0 }; }
    if (call.url === '/api/order/cart/items') { return { soMuc: 1 }; }
    if (call.url.startsWith('/api/wishlist')) { return handler(call); }
    throw new Error('Unexpected API: ' + call.url);
  }, role, { pathname: template === 'books/detail.html' ? '/books/42' : '/wishlist', includeFragments: true });
  // The source-template harness does not evaluate Thymeleaf's th:attr.
  if (app.get('wishlistButton')) { app.get('wishlistButton').setAttribute('data-ma-sach', '42'); }
  return app;
}

function calls(app, method) {
  return app.calls.filter(call => call.url.startsWith('/api/wishlist') && (!method || call.method === method));
}

function saved(app) { return app.get('wishlistButton').getAttribute('aria-pressed') === 'true'; }
function shown(app, id) { return !app.get(id).classList.contains('d-none'); }

test('saved detail state is loaded from the server and remains saved after reloading', async () => {
  for (let reload = 0; reload < 2; reload++) {
    const app = appFor('books/detail.html', () => [book()]);
    assert.equal(app.get('wishlistButton').disabled, true, 'membership must load before any write');
    await settle();
    assert.equal(saved(app), true);
    assert.equal(app.get('wishlistButton').classList.contains('active'), true);
    assert.ok(app.get('wishlistButton').querySelector('.bi-heart-fill'));
    assert.equal(app.get('wishlistButton').querySelector('[data-wishlist-label]').textContent, 'Đã yêu thích');
    assert.equal(app.get('wishlistButton').disabled, false);
    assert.equal(calls(app, 'GET').length, 1);
    assert.match(calls(app)[0].headers.Authorization, /^Bearer /);
    assert.equal(calls(app, 'PUT').length, 0);
  }
});

test('a detail favorite is written once, activates only after success and appears on wishlist', async () => {
  const items = [];
  let completeAdd;
  const server = call => {
    if (call.method === 'GET') { return items.slice(); }
    if (call.method === 'PUT') {
      return new Promise(resolve => { completeAdd = () => { items.push(book()); resolve(book()); }; });
    }
    throw new Error('Unexpected write');
  };
  const detail = appFor('books/detail.html', server);
  await settle();
  detail.trigger(detail.get('wishlistButton').querySelector('i'));
  detail.trigger('wishlistButton');
  assert.equal(calls(detail, 'PUT').length, 1);
  assert.equal(calls(detail, 'PUT')[0].url, '/api/wishlist/42');
  assert.equal(detail.get('wishlistButton').disabled, true);
  assert.equal(saved(detail), false);
  completeAdd();
  await settle();
  assert.equal(saved(detail), true);
  assert.equal(detail.get('wishlistButton').disabled, false);
  assert.equal(detail.get('ob-toast-holder').querySelector('a').href, '/wishlist');

  const list = appFor('wishlist.html', server);
  await settle();
  assert.equal(list.get('wishlistCount').textContent, '1 cuốn');
  assert.equal(list.get('wishlistGrid').querySelector('.book-title a').getAttribute('href'), '/books/42');
  assert.equal(list.get('wishlistGrid').querySelector('.book-title').textContent, 'Đắc nhân tâm');
  assert.equal(list.get('wishlistGrid').querySelector('[data-remove-wishlist]'), null);
  assert.equal(list.get('wishlistGrid').querySelector('.book-info [data-toggle-wishlist]'), null);
  assert.ok(list.get('wishlistGrid').querySelector('.book-cover-wrap [data-toggle-wishlist]'));
  assert.equal(shown(list, 'wishlistEmpty'), false);
  assert.equal(shown(list, 'wishlistLoading'), false);
});

test('a successful detail removal resets heart state and removes the persisted favorite', async () => {
  let items = [book()];
  const app = appFor('books/detail.html', call => {
    if (call.method === 'GET') { return items; }
    if (call.method === 'DELETE') { items = []; return { data: null }; }
    throw new Error('Unexpected write');
  });
  await settle();
  app.trigger('wishlistButton');
  await settle();
  assert.equal(calls(app, 'DELETE').length, 1);
  assert.equal(saved(app), false);
  assert.ok(app.get('wishlistButton').querySelector('.bi-heart'));
  assert.equal(items.length, 0);
});

test('all controls for the same book stay synchronized and block simultaneous writes', async () => {
  let completeAdd;
  const app = appFor('books/detail.html', call => call.method === 'GET' ? [] : new Promise(resolve => { completeAdd = resolve; }));
  const second = app.document.createElement('button');
  second.setAttribute('data-toggle-wishlist', '');
  second.setAttribute('data-ma-sach', '42');
  second.innerHTML = '<i class="bi bi-heart"></i><span data-wishlist-label>Yêu thích</span>';
  app.document.body.appendChild(second);
  await settle();
  app.trigger('wishlistButton');
  app.trigger(second);
  assert.equal(calls(app, 'PUT').length, 1);
  assert.equal(second.disabled, true);
  completeAdd(book());
  await settle();
  assert.equal(second.getAttribute('aria-pressed'), 'true');
  assert.equal(second.disabled, false);
  assert.ok(second.querySelector('.bi-heart-fill'));
});

test('wishlist removal waits for success, blocks duplicates and shows the empty state', async () => {
  let completeRemove;
  const app = appFor('wishlist.html', call => call.method === 'GET' ? [book()] : new Promise(resolve => { completeRemove = resolve; }));
  await settle();
  const button = app.get('wishlistGrid').querySelector('.book-cover-wrap [data-toggle-wishlist]');
  assert.equal(app.get('wishlistGrid').querySelector('[data-remove-wishlist]'), null);
  assert.equal(button.getAttribute('aria-pressed'), 'true');
  app.trigger(button.querySelector('i'));
  app.trigger(button);
  assert.equal(calls(app, 'DELETE').length, 1);
  assert.equal(button.disabled, true);
  assert.equal(button.getAttribute('aria-pressed'), 'true', 'the heart remains saved while removal is pending');
  assert.equal(app.get('wishlistCount').textContent, '1 cuốn');
  completeRemove({ data: null });
  await settle();
  assert.equal(app.get('wishlistCount').textContent, '0 cuốn');
  assert.equal(app.get('wishlistGrid').children.length, 0);
  assert.equal(shown(app, 'wishlistEmpty'), true);
});

test('failed wishlist removal preserves the item and restores its button for retry', async () => {
  const app = appFor('wishlist.html', call => call.method === 'GET' ? [book()] : { httpStatus: 500, message: 'Chưa thể bỏ yêu thích', data: null });
  await settle();
  const button = app.get('wishlistGrid').querySelector('.book-cover-wrap [data-toggle-wishlist]');
  app.trigger(button);
  await settle();
  assert.equal(app.get('wishlistCount').textContent, '1 cuốn');
  assert.equal(app.get('wishlistGrid').querySelector('[data-toggle-wishlist]'), button);
  assert.equal(app.get('wishlistGrid').querySelector('[data-remove-wishlist]'), null);
  assert.equal(button.disabled, false);
  assert.equal(button.getAttribute('aria-pressed'), 'true');
  assert.ok(button.querySelector('.bi-heart-fill'), 'a failed removal retains the saved heart state');
  assert.equal(shown(app, 'wishlistEmpty'), false);
  assert.equal(shown(app, 'wishlistError'), true);
  assert.equal(app.get('wishlistError').textContent, 'Chưa thể bỏ yêu thích');
  assert.match(app.get('ob-toast-holder').textContent, /Chưa thể bỏ yêu thích/);
});

test('failed detail mutation retains the last saved state', async () => {
  for (const initiallySaved of [false, true]) {
    const app = appFor('books/detail.html', call => call.method === 'GET' ? initiallySaved ? [book()] : [] : { httpStatus: 400, message: 'Không thể cập nhật yêu thích', data: null });
    await settle();
    app.trigger('wishlistButton');
    await settle();
    assert.equal(saved(app), initiallySaved);
    assert.equal(app.get('wishlistButton').disabled, false);
    assert.match(app.get('ob-toast-holder').textContent, /Không thể cập nhật yêu thích/);
  }
});

test('failed detail preload offers a read-only retry before allowing mutations', async () => {
  let reads = 0;
  const app = appFor('books/detail.html', call => {
    if (call.method === 'GET') { return ++reads === 1 ? { httpStatus: 500, message: 'Tải yêu thích thất bại', data: null } : [book()]; }
    return { data: null };
  });
  app.trigger('wishlistButton');
  assert.equal(calls(app).length, 1, 'clicking during preload cannot write');
  await settle();
  assert.equal(app.get('wishlistButton').disabled, false);
  assert.equal(app.get('wishlistButton').querySelector('[data-wishlist-label]').textContent, 'Thử lại');
  app.trigger('wishlistButton');
  await settle();
  assert.equal(calls(app, 'GET').length, 2);
  assert.equal(calls(app, 'PUT').length + calls(app, 'DELETE').length, 0);
  assert.equal(saved(app), true);
  app.trigger('wishlistButton');
  await settle();
  assert.equal(calls(app, 'DELETE').length, 1);
  assert.equal(saved(app), false);
});

test('wishlist preload failure shows a retry and cannot appear as an empty list', async () => {
  let reads = 0;
  const app = appFor('wishlist.html', () => ++reads === 1 ? { httpStatus: 503, message: 'Vui lòng thử lại', data: null } : []);
  await settle();
  assert.equal(shown(app, 'wishlistError'), true);
  assert.equal(shown(app, 'wishlistRetry'), true);
  assert.equal(shown(app, 'wishlistEmpty'), false);
  assert.equal(shown(app, 'wishlistLoading'), false);
  app.trigger('wishlistRetry');
  await settle();
  assert.equal(calls(app, 'GET').length, 2);
  assert.equal(shown(app, 'wishlistError'), false);
  assert.equal(shown(app, 'wishlistRetry'), false);
  assert.equal(shown(app, 'wishlistEmpty'), true);
});

test('guests use the shared login guard and never request wishlist data', async () => {
  const list = appFor('wishlist.html', () => { throw new Error('Guest wishlist request'); }, null);
  await settle();
  assert.deepEqual(list.redirects, ['/login?redirect=%2Fwishlist']);
  assert.equal(calls(list).length, 0);

  const detail = appFor('books/detail.html', () => { throw new Error('Guest wishlist request'); }, null);
  await settle();
  detail.trigger('wishlistButton');
  await settle();
  assert.deepEqual(detail.redirects, ['/login?redirect=%2Fbooks%2F42']);
  assert.equal(calls(detail).length, 0);
});

test('other roles see an access message, cannot mutate and keep their session', async () => {
  const list = appFor('wishlist.html', () => { throw new Error('Non-customer request'); }, 'CHU_CUA_HANG');
  await settle();
  assert.equal(shown(list, 'wishlistAccessError'), true);
  assert.equal(shown(list, 'wishlistLoading'), false);
  assert.equal(calls(list).length, 0);
  assert.equal(list.window.OldBook.auth.isLoggedIn(), true);

  const detail = appFor('books/detail.html', () => { throw new Error('Non-customer request'); }, 'QUAN_TRI_VIEN');
  await settle();
  detail.trigger('wishlistButton');
  await settle();
  assert.equal(calls(detail).length, 0);
  assert.match(detail.get('ob-toast-holder').textContent, /Chỉ tài khoản khách hàng/);
  assert.equal(detail.window.OldBook.auth.isLoggedIn(), true);
});

test('unauthorized writes use the auth error handler while forbidden writes keep the session', async () => {
  for (const status of [401, 403]) {
    const app = appFor('books/detail.html', call => call.method === 'GET' ? [] : { httpStatus: status, message: 'Không có quyền', data: null });
    await settle();
    app.trigger('wishlistButton');
    await settle();
    assert.equal(saved(app), false);
    assert.equal(app.get('wishlistButton').disabled, false);
    assert.equal(app.window.OldBook.auth.isLoggedIn(), status === 403);
    assert.deepEqual(app.redirects, status === 401 ? ['/login?redirect=%2Fbooks%2F42'] : []);
  }
});

test('wishlist cards escape content and reject dangerous cover URLs', async () => {
  const unsafe = ['javascript:alert(1)', 'data:image/svg+xml,<svg onload=alert(1)>', '//evil.example/image', '/\\evil.example/image'];
  const items = unsafe.map((hinhAnhUrl, index) => book(index + 1, {
    tenSach: '<script>alert(1)</script>', tacGia: '<img src=x onerror=alert(1)>',
    tenCuaHang: '"><img src=x onerror=alert(1)>', hinhAnhUrl
  }));
  items.push(book(9, { hinhAnhUrl: 'https://example.test/cover.jpg?q="quoted"', tenSach: 'Sách "mới"' }));
  const app = appFor('wishlist.html', () => items);
  await settle();
  const grid = app.get('wishlistGrid');
  assert.equal(grid.querySelectorAll('script').length, 0);
  assert.equal(grid.querySelectorAll('img').length, 1);
  assert.equal(grid.querySelector('img').getAttribute('src'), 'https://example.test/cover.jpg?q="quoted"');
  assert.equal(grid.querySelectorAll('[onerror]').length, 0);
  assert.equal(grid.querySelectorAll('.book-title')[0].textContent, '<script>alert(1)</script>');
  assert.equal(app.get('wishlistCount').textContent, '5 cuốn');
});

test('wishlist retains removable out-of-stock books and uses the shared cart action for available books', async () => {
  const app = appFor('wishlist.html', call => call.method === 'GET' ? [book(), book(43, { soLuongTon: 0 })] : { data: null });
  await settle();
  assert.equal(app.get('wishlistCount').textContent, '2 cuốn');
  const cards = app.get('wishlistGrid').children;
  assert.ok(cards[0].querySelector('[data-add-to-cart]'));
  assert.equal(cards[1].querySelector('[data-add-to-cart]'), null);
  assert.match(cards[1].textContent, /Hết hàng/);
  const soldOutHeart = cards[1].querySelector('.book-cover-wrap [data-toggle-wishlist]');
  assert.ok(soldOutHeart, 'out-of-stock favorites remain removable through their cover heart');
  assert.equal(soldOutHeart.getAttribute('aria-pressed'), 'true');
  assert.equal(app.get('wishlistGrid').querySelector('[data-remove-wishlist]'), null);
  app.trigger(cards[0].querySelector('[data-add-to-cart]'));
  await settle();
  assert.deepEqual(app.calls.find(call => call.url === '/api/order/cart/items').body, { maSach: 42, soLuong: 1 });
  assert.equal(app.get('wishlistCount').textContent, '2 cuốn');
  app.trigger(soldOutHeart);
  await settle();
  assert.equal(calls(app, 'DELETE')[0].url, '/api/wishlist/43');
  assert.equal(app.get('wishlistCount').textContent, '1 cuốn');
  assert.equal(app.get('wishlistGrid').querySelector('[data-ma-sach="43"]'), null);
});

test('loading the shared script on a page without wishlist hooks makes no wishlist request', async () => {
  const app = appFor('books/catalog.html', () => { throw new Error('Unexpected wishlist request'); });
  await settle();
  assert.equal(calls(app).length, 0);
  assert.equal(app.window.OldBook.wishlist, undefined);
});
