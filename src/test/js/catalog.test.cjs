'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { boot, settle } = require('./dom-harness.cjs');

function catalogApp(books, options = {}, role = null) {
  return boot('books/catalog.html', ['catalog.js', 'cart.js'], async call => {
    if (call.url === '/api/profile') { return { hoTen: 'Người mua' }; }
    if (call.url === '/api/order/cart' && call.method === 'GET') { return { soMuc: 0 }; }
    if (call.url === '/api/order/cart/items') { return { soMuc: 1 }; }
    if (call.url.startsWith('/api/sach')) { return books; }
    throw new Error('Unexpected API: ' + call.url);
  }, role, { pathname: '/', ...options });
}

function book(id, overrides = {}) {
  return {
    maSach: id, tenSach: 'Sách ' + id, tacGia: 'Tác giả', tenCuaHang: 'Cửa hàng',
    tenDanhMuc: 'Văn học', soLuongTon: 5, giaBanCu: id * 10000,
    ngayTao: '2026-10-01T10:00:00', ...overrides
  };
}

function ids(app, grid) {
  return app.get(grid).querySelectorAll('.book-title a').map(link => Number(link.getAttribute('href').split('/').pop()));
}

test('home shows the index sections with affordable, newest and book-set grids', async () => {
  const books = Array.from({ length: 10 }, (_, i) => book(i + 1, {
    tenDanhMuc: i >= 3 ? 'Bộ sách' : 'Văn học'
  }));
  books[0].soLuongTon = 0;
  books[1].ngayTao = '2026-10-03T10:00:00';
  const app = catalogApp(books);
  await settle();

  assert.equal(app.get('catalogTitle').textContent, 'Sách mới');
  assert.equal(app.get('catalogHomeIntro').classList.contains('d-none'), false);
  assert.equal(app.get('catalogHomeOutro').classList.contains('d-none'), false);
  assert.equal(app.get('catalogSeeAll').classList.contains('d-none'), false);
  assert.deepEqual(ids(app, 'cheapBookGrid'), [2, 3, 4, 5]);
  assert.deepEqual(ids(app, 'bookGrid'), [2, 10, 9, 8, 7, 6, 5, 4]);
  assert.deepEqual(ids(app, 'bookSetGrid'), [10, 9, 8, 7, 6, 5]);
  assert.equal(app.get('cheapBookGrid').children[0].className, 'col-6 col-md-3');
  assert.equal(app.get('bookGrid').children[0].className, 'col-6 col-md-4 col-lg-3');
  assert.equal(app.get('bookSetGrid').children[0].className, 'col-6 col-md-4 col-lg-2');
  assert.equal(app.get('catalogLoading').classList.contains('d-none'), true);
  assert.equal(app.calls.filter(call => call.url.startsWith('/api/sach')).length, 1);
});

test('all-books and filtered routes retain the complete catalog and API query', async () => {
  const books = Array.from({ length: 10 }, (_, i) => book(i + 1));
  for (const options of [
    { pathname: '/books' },
    { pathname: '/books', search: '?q=Tr%E1%BA%BB&category=V%C4%83n%20h%E1%BB%8Dc' },
    { pathname: '/', search: '?category=V%C4%83n%20h%E1%BB%8Dc' }
  ]) {
    const app = catalogApp(books, options);
    await settle();

    assert.equal(ids(app, 'bookGrid').length, 10);
    assert.equal(app.get('catalogHomeIntro').classList.contains('d-none'), true);
    assert.equal(app.get('catalogHomeOutro').classList.contains('d-none'), true);
    const api = new URL(app.calls.find(call => call.url.startsWith('/api/sach')).url, 'http://localhost');
    const query = new URLSearchParams(options.search || '');
    assert.equal(api.searchParams.get('q'), query.get('q'));
    assert.equal(api.searchParams.get('category'), query.get('category'));
    if (query.has('q')) {
      assert.equal(app.get('catalogTitle').textContent, 'Kết quả cho "Trẻ" trong Văn học');
    }
  }
});

test('empty sections and catalog failures finish loading and show their messages', async () => {
  const empty = catalogApp([]);
  await settle();
  for (const id of ['catalogEmpty', 'cheapBooksEmpty', 'bookSetsEmpty']) {
    assert.equal(empty.get(id).classList.contains('d-none'), false);
  }
  assert.equal(empty.get('catalogLoading').classList.contains('d-none'), true);

  const filtered = catalogApp([], { pathname: '/books', search: '?q=missing' });
  await settle();
  assert.equal(filtered.get('catalogEmptyText').textContent, 'Không tìm thấy sách phù hợp.');

  const failed = catalogApp({ httpStatus: 500, message: 'Không thể tải sách', data: null });
  await settle();
  assert.equal(failed.get('catalogLoading').classList.contains('d-none'), true);
  assert.equal(failed.get('catalogAlert').classList.contains('d-none'), false);
  assert.equal(failed.get('catalogAlert').textContent, 'Không thể tải sách');
});

test('home cards escape content, reject unsafe images and retain add-to-cart actions', async () => {
  const app = catalogApp([book(42, {
    tenSach: '<script>unsafe()</script>', tenDanhMuc: 'Bộ sách',
    hinhAnhUrl: 'javascript:unsafe()', tenCuaHang: '<img src=x onerror=unsafe()>'
  })], {}, 'KHACH_HANG');
  await settle();

  for (const id of ['cheapBookGrid', 'bookGrid', 'bookSetGrid']) {
    const grid = app.get(id);
    assert.equal(grid.querySelector('script'), null);
    assert.equal(grid.querySelector('img'), null);
    assert.equal(grid.querySelector('.book-title').textContent, '<script>unsafe()</script>');
    app.trigger(grid.querySelector('[data-add-to-cart]'));
    await settle();
  }
  const additions = app.calls.filter(call => call.url === '/api/order/cart/items');
  assert.equal(additions.length, 3);
  assert.ok(additions.every(call => call.body.maSach === 42 && call.body.soLuong === 1));
});
