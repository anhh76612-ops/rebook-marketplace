'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { File } = require('node:buffer');
const { boot, settle } = require('./dom-harness.cjs');

const BASE = '/api/store/me/sach';
const imageUrl = 'https://res.cloudinary.com/oldbook/image/upload/books/cover.jpg';
const page = books => ({ content: books, page: 0, size: 20, totalElements: books.length, totalPages: books.length ? 1 : 0 });
const existingBook = {
  maSach: 41, maDM: 3, tenDanhMuc: 'Văn học', tenSach: 'Sách hiện có', giaBanCu: 75000,
  hinhAnhUrl: '/images/existing.jpg', soLuongTon: 5, trangThaiDuyet: 'DA_DUYET', trangThaiBan: 'DANG_BAN'
};

async function seller(handler, book = null) {
  const app = boot('store-books.html', ['store.js'], async call => {
    if (call.url === '/api/profile') { return { hoTen: 'Chủ cửa hàng' }; }
    if (call.url === '/api/store/me') { return { maCH: 7, tenCuaHang: 'Cửa hàng', trangThaiDuyet: 'DA_DUYET' }; }
    if (call.url.startsWith(BASE + '?')) { return page(book ? [book] : []); }
    if (call.url === '/api/sach/danh-muc') { return [{ maDM: 3, tenDanhMuc: 'Văn học' }]; }
    if (book && call.url === BASE + '/41' && call.method === 'GET') { return book; }
    return handler(call);
  }, 'CHU_CUA_HANG');
  await settle();
  return app;
}

async function openNew(app) {
  app.trigger('addBook');
  await settle();
  app.set('bookTitle', 'Sách mới');
  app.set('bookCategory', '3');
  app.set('bookSalePrice', '50000');
}

function selectFile(app, file) {
  app.set('bookImageFile', 'C:\\fakepath\\' + file.name);
  app.get('bookImageFile').files = [file];
  app.trigger('bookImageFile', 'change');
}

function writes(app) { return app.calls.filter(call => call.method !== 'GET'); }

test('seller uploads multipart with JWT before saving a book and blocks dismissal and duplicate submissions', async () => {
  let finishUpload;
  const pendingUpload = new Promise(resolve => { finishUpload = resolve; });
  const app = await seller(async call => {
    if (call.url === BASE + '/hinh-anh') { await pendingUpload; return { hinhAnhUrl: imageUrl }; }
    if (call.url === BASE) { return { maSach: 50, ...call.body }; }
    throw new Error('Unexpected API: ' + call.url);
  });
  await openNew(app);
  const file = new File(['cover'], 'cover.jpg', { type: 'image/jpeg' });
  selectFile(app, file);
  const preview = app.get('bookImagePreview').getAttribute('src');
  assert.match(preview, /^blob:/);
  app.trigger('sellerBookForm', 'submit');
  app.trigger('sellerBookForm', 'submit');
  assert.equal(writes(app).length, 1);
  assert.equal(writes(app)[0].url, BASE + '/hinh-anh');
  assert.ok(writes(app)[0].body instanceof FormData);
  assert.equal(writes(app)[0].body.get('file'), file);
  assert.match(writes(app)[0].headers.Authorization, /^Bearer /);
  assert.equal(Object.hasOwn(writes(app)[0].headers, 'Content-Type'), false, 'browser sets multipart boundary');
  assert.equal(app.get('saveBook').disabled, true);
  assert.equal(app.get('bookFields').disabled, true);
  assert.equal(app.get('bookImageUploading').classList.contains('d-none'), false);
  new app.window.bootstrap.Modal(app.get('sellerBookModal')).hide();
  assert.equal(app.get('sellerBookModal').classList.contains('show'), true);
  finishUpload();
  await settle();
  assert.deepEqual(writes(app).map(call => call.url), [BASE + '/hinh-anh', BASE]);
  assert.equal(writes(app)[1].headers['Content-Type'], 'application/json');
  assert.equal(writes(app)[1].body.hinhAnhUrl, imageUrl);
  assert.equal(writes(app)[1].body.soLuongTon, 0);
  assert.equal(app.get('sellerBookModal').classList.contains('show'), false);
  assert.deepEqual(app.revokedPreviewUrls, [preview]);
  assert.equal(app.get('bookImageFile').files.length, 0);
});

test('failed upload preserves selected file and entered data for retry without saving a book', async () => {
  let attempts = 0;
  const app = await seller(async call => {
    if (call.url === BASE + '/hinh-anh') {
      attempts++;
      return attempts === 1 ? { httpStatus: 502, message: 'Không tải được ảnh' } : { hinhAnhUrl: imageUrl };
    }
    if (call.url === BASE) { return { maSach: 50 }; }
    throw new Error('Unexpected API: ' + call.url);
  });
  await openNew(app);
  const file = new File(['cover'], 'cover.png', { type: 'image/png' });
  selectFile(app, file);
  const preview = app.get('bookImagePreview').getAttribute('src');
  app.trigger('sellerBookForm', 'submit');
  await settle();
  assert.deepEqual(writes(app).map(call => call.url), [BASE + '/hinh-anh']);
  assert.match(app.get('bookFormError').textContent, /Không tải được ảnh/);
  assert.equal(app.get('bookTitle').value, 'Sách mới');
  assert.equal(app.get('bookImageFile').files[0], file);
  assert.equal(app.get('bookImagePreview').getAttribute('src'), preview);
  assert.equal(app.get('sellerBookModal').classList.contains('show'), true);
  assert.equal(app.get('saveBook').disabled, false);
  app.trigger('sellerBookForm', 'submit');
  await settle();
  assert.equal(attempts, 2);
  assert.equal(writes(app).at(-1).body.hinhAnhUrl, imageUrl);
});

test('retry after a book save failure reuses the successful upload and saves corrected data', async () => {
  let saves = 0;
  const app = await seller(async call => {
    if (call.url === BASE + '/hinh-anh') { return { hinhAnhUrl: imageUrl }; }
    if (call.url === BASE) {
      saves++;
      return saves === 1 ? { httpStatus: 400, message: 'Hãy sửa thông tin sách' } : { maSach: 50 };
    }
    throw new Error('Unexpected API: ' + call.url);
  });
  await openNew(app);
  selectFile(app, new File(['cover'], 'cover.webp', { type: 'image/webp' }));
  app.trigger('sellerBookForm', 'submit');
  await settle();
  assert.match(app.get('bookFormError').textContent, /Hãy sửa thông tin sách/);
  assert.equal(app.get('sellerBookModal').classList.contains('show'), true);
  app.set('bookTitle', 'Tên đã sửa');
  app.trigger('sellerBookForm', 'submit');
  await settle();
  assert.equal(writes(app).filter(call => call.url === BASE + '/hinh-anh').length, 1);
  assert.equal(saves, 2);
  assert.equal(writes(app).at(-1).body.tenSach, 'Tên đã sửa');
  assert.equal(writes(app).at(-1).body.hinhAnhUrl, imageUrl);
});

test('seller rejects invalid types, empty images and files over 10 MiB before any upload', async () => {
  const app = await seller(async call => { throw new Error('Invalid image must not write: ' + call.url); });
  await openNew(app);
  for (const [file, expected] of [
    [new File(['svg'], 'cover.svg', { type: 'image/svg+xml' }), /JPEG, PNG hoặc WebP/],
    [new File([], 'empty.jpg', { type: 'image/jpeg' }), /không được để trống/],
    [new File([new Uint8Array(10 * 1024 * 1024 + 1)], 'large.png', { type: 'image/png' }), /10 MB/]
  ]) {
    selectFile(app, file);
    app.trigger('sellerBookForm', 'submit');
    await settle();
    assert.match(app.get('bookImageError').textContent, expected);
    assert.equal(writes(app).length, 0);
  }
  selectFile(app, new File([new Uint8Array(10 * 1024 * 1024)], 'limit.png', { type: 'image/png' }));
  assert.equal(app.get('bookImageError').textContent, '');
  assert.equal(app.get('bookImageFile').customValidity, '');
  assert.match(app.get('bookImagePreview').getAttribute('src'), /^blob:/);
  selectFile(app, new File(['invalid'], 'invalid.gif', { type: 'image/gif' }));
  assert.equal(app.get('bookImagePreviewPanel').classList.contains('d-none'), false, 'invalid selection can be cleared');
  app.trigger('removeBookImage');
  assert.equal(app.get('bookImageFile').customValidity, '');
  assert.equal(app.get('bookImageError').textContent, '');
});

test('choosing a different file after save failure uploads the replacement instead of using the cached URL', async () => {
  const app = await seller(async call => {
    if (call.url === BASE + '/hinh-anh') { return { hinhAnhUrl: imageUrl.replace('cover.jpg', call.body.get('file').name) }; }
    if (call.url === BASE) { return { httpStatus: 400, message: 'Hãy sửa thông tin sách' }; }
    throw new Error('Unexpected API: ' + call.url);
  });
  await openNew(app);
  selectFile(app, new File(['first'], 'first.jpg', { type: 'image/jpeg' }));
  const firstPreview = app.get('bookImagePreview').getAttribute('src');
  app.trigger('sellerBookForm', 'submit');
  await settle();
  selectFile(app, new File(['second'], 'second.png', { type: 'image/png' }));
  app.trigger('sellerBookForm', 'submit');
  await settle();
  assert.equal(writes(app).filter(call => call.url.endsWith('/hinh-anh')).length, 2);
  assert.match(writes(app).at(-1).body.hinhAnhUrl, /second\.png$/);
  assert.ok(app.revokedPreviewUrls.includes(firstPreview));
});

test('editing keeps the existing cover unless removed and resets file state between books', async () => {
  const app = await seller(async call => {
    if (call.url === BASE + '/41' && call.method === 'PUT') { return { ...existingBook, ...call.body }; }
    if (call.url === BASE) { return { maSach: 50 }; }
    throw new Error('Unexpected API: ' + call.url);
  }, existingBook);
  app.trigger(app.get('sellerBooksRows').querySelector('[data-book-action="edit"]'));
  await settle();
  assert.equal(app.get('bookImagePreview').getAttribute('src'), existingBook.hinhAnhUrl);
  app.trigger('sellerBookForm', 'submit');
  await settle();
  assert.equal(writes(app).at(-1).body.hinhAnhUrl, existingBook.hinhAnhUrl);
  assert.equal(writes(app).filter(call => call.url.endsWith('/hinh-anh')).length, 0);

  app.trigger(app.get('sellerBooksRows').querySelector('[data-book-action="edit"]'));
  await settle();
  selectFile(app, new File(['replacement'], 'replacement.jpg', { type: 'image/jpeg' }));
  const preview = app.get('bookImagePreview').getAttribute('src');
  app.trigger('removeBookImage');
  assert.equal(app.get('bookImagePreview').getAttribute('src'), null);
  assert.equal(app.get('bookImageFile').files.length, 0);
  assert.ok(app.revokedPreviewUrls.includes(preview));
  app.trigger('sellerBookForm', 'submit');
  await settle();
  assert.equal(writes(app).at(-1).body.hinhAnhUrl, null);

  await openNew(app);
  selectFile(app, new File(['discarded'], 'discarded.png', { type: 'image/png' }));
  const discarded = app.get('bookImagePreview').getAttribute('src');
  new app.window.bootstrap.Modal(app.get('sellerBookModal')).hide();
  assert.ok(app.revokedPreviewUrls.includes(discarded));
  await openNew(app);
  assert.equal(app.get('bookImageFile').files.length, 0);
  assert.equal(app.get('bookImagePreview').getAttribute('src'), null);
  app.trigger('sellerBookForm', 'submit');
  await settle();
  assert.equal(writes(app).at(-1).body.hinhAnhUrl, null);
  assert.equal(writes(app).filter(call => call.url.endsWith('/hinh-anh')).length, 0);
});
