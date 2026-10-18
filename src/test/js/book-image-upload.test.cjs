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

function selectFiles(app, ...files) {
  app.set('bookImageFile', 'C:\\fakepath\\' + files[0].name);
  app.get('bookImageFile').files = files;
  app.trigger('bookImageFile', 'change');
}

const thumbs = app => [...app.get('bookImageList').querySelectorAll('img')];
const imageAction = (app, action, index) =>
  app.get('bookImageList').querySelector(`[data-image-action="${action}"][data-image-index="${index}"]`);

function writes(app) { return app.calls.filter(call => call.method !== 'GET'); }
const uploadsOf = app => writes(app).filter(call => call.url === BASE + '/hinh-anh');
const jpg = name => new File([name], name + '.jpg', { type: 'image/jpeg' });

test('seller uploads several images in order before saving and blocks dismissal and duplicate submissions', async () => {
  let finishUpload;
  const pendingUpload = new Promise(resolve => { finishUpload = resolve; });
  const app = await seller(async call => {
    if (call.url === BASE + '/hinh-anh') { await pendingUpload; return { hinhAnhUrl: imageUrl.replace('cover', call.body.get('file').name.replace('.jpg', '')) }; }
    if (call.url === BASE) { return { maSach: 50, ...call.body }; }
    throw new Error('Unexpected API: ' + call.url);
  });
  await openNew(app);
  const first = jpg('one');
  const second = jpg('two');
  selectFiles(app, first, second);
  assert.equal(thumbs(app).length, 2);
  thumbs(app).forEach(img => assert.match(img.getAttribute('src'), /^blob:/));
  assert.equal(app.get('bookImageFile').files.length, 0);
  assert.equal(app.get('bookImageList').textContent.includes('Ảnh bìa'), true);
  app.trigger('sellerBookForm', 'submit');
  app.trigger('sellerBookForm', 'submit');
  await settle();
  assert.equal(uploadsOf(app).length, 1);
  assert.equal(uploadsOf(app)[0].body.get('file'), first);
  assert.match(uploadsOf(app)[0].headers.Authorization, /^Bearer /);
  assert.equal(Object.hasOwn(uploadsOf(app)[0].headers, 'Content-Type'), false, 'browser sets multipart boundary');
  assert.equal(app.get('saveBook').disabled, true);
  assert.equal(app.get('bookFields').disabled, true);
  assert.equal(app.get('bookImageUploading').classList.contains('d-none'), false);
  new app.window.bootstrap.Modal(app.get('sellerBookModal')).hide();
  assert.equal(app.get('sellerBookModal').classList.contains('show'), true);
  finishUpload();
  await settle();
  assert.deepEqual(writes(app).map(call => call.url), [BASE + '/hinh-anh', BASE + '/hinh-anh', BASE]);
  assert.equal(writes(app)[1].body.get('file'), second);
  const saved = writes(app)[2].body;
  assert.equal(writes(app)[2].headers['Content-Type'], 'application/json');
  assert.deepEqual(saved.hinhAnhUrls, [imageUrl.replace('cover', 'one'), imageUrl.replace('cover', 'two')]);
  assert.equal(saved.hinhAnhUrl, saved.hinhAnhUrls[0]);
  assert.equal(saved.soLuongTon, 0);
  assert.equal(app.get('sellerBookModal').classList.contains('show'), false);
  assert.equal(app.revokedPreviewUrls.length, 2);
});

test('failed upload keeps selected images and retry only uploads the images that are still missing', async () => {
  let secondAttempts = 0;
  const app = await seller(async call => {
    if (call.url === BASE + '/hinh-anh') {
      const name = call.body.get('file').name;
      if (name === 'two.jpg' && ++secondAttempts === 1) { return { httpStatus: 502, message: 'Không tải được ảnh' }; }
      return { hinhAnhUrl: imageUrl.replace('cover', name.replace('.jpg', '')) };
    }
    if (call.url === BASE) { return { maSach: 50 }; }
    throw new Error('Unexpected API: ' + call.url);
  });
  await openNew(app);
  selectFiles(app, jpg('one'), jpg('two'));
  app.trigger('sellerBookForm', 'submit');
  await settle();
  assert.deepEqual(writes(app).map(call => call.url), [BASE + '/hinh-anh', BASE + '/hinh-anh']);
  assert.match(app.get('bookFormError').textContent, /Không tải được ảnh/);
  assert.equal(app.get('bookTitle').value, 'Sách mới');
  assert.equal(thumbs(app).length, 2);
  assert.equal(app.get('sellerBookModal').classList.contains('show'), true);
  assert.equal(app.get('saveBook').disabled, false);
  app.trigger('sellerBookForm', 'submit');
  await settle();
  assert.equal(uploadsOf(app).length, 3, 'only the failed image is uploaded again');
  assert.equal(writes(app).at(-1).url, BASE);
  assert.equal(writes(app).at(-1).body.hinhAnhUrls.length, 2);
});

test('retry after a book save failure reuses the successful uploads and saves corrected data', async () => {
  let saves = 0;
  const app = await seller(async call => {
    if (call.url === BASE + '/hinh-anh') { return { hinhAnhUrl: imageUrl.replace('cover', call.body.get('file').name.replace('.jpg', '')) }; }
    if (call.url === BASE) {
      saves++;
      return saves === 1 ? { httpStatus: 400, message: 'Hãy sửa thông tin sách' } : { maSach: 50 };
    }
    throw new Error('Unexpected API: ' + call.url);
  });
  await openNew(app);
  selectFiles(app, jpg('one'), jpg('two'));
  app.trigger('sellerBookForm', 'submit');
  await settle();
  assert.match(app.get('bookFormError').textContent, /Hãy sửa thông tin sách/);
  app.set('bookTitle', 'Tên đã sửa');
  app.trigger('sellerBookForm', 'submit');
  await settle();
  assert.equal(uploadsOf(app).length, 2);
  assert.equal(saves, 2);
  assert.equal(writes(app).at(-1).body.tenSach, 'Tên đã sửa');
  assert.equal(writes(app).at(-1).body.hinhAnhUrls.length, 2);
});

test('seller rejects invalid types, empty images and files over 10 MiB but keeps valid ones, up to 10 images', async () => {
  const app = await seller(async call => { throw new Error('Selecting images must not write: ' + call.url); });
  await openNew(app);
  for (const [file, expected] of [
    [new File(['svg'], 'cover.svg', { type: 'image/svg+xml' }), /JPEG, PNG hoặc WebP/],
    [new File([], 'empty.jpg', { type: 'image/jpeg' }), /không được để trống/],
    [new File([new Uint8Array(10 * 1024 * 1024 + 1)], 'large.png', { type: 'image/png' }), /10 MB/]
  ]) {
    selectFiles(app, file);
    assert.match(app.get('bookImageError').textContent, expected);
    assert.equal(thumbs(app).length, 0);
  }
  selectFiles(app, new File(['bad'], 'bad.gif', { type: 'image/gif' }), jpg('ok'));
  assert.match(app.get('bookImageError').textContent, /JPEG, PNG hoặc WebP/);
  assert.equal(thumbs(app).length, 1);
  selectFiles(app, jpg('a'));
  assert.equal(app.get('bookImageError').textContent, '');
  selectFiles(app, ...Array.from({ length: 12 }, (_, i) => jpg('m' + i)));
  assert.equal(thumbs(app).length, 10);
  assert.match(app.get('bookImageError').textContent, /tối đa 10 ảnh/);
  assert.equal(writes(app).length, 0);
});

test('seller can remove an image and promote another one to cover before saving', async () => {
  const app = await seller(async call => {
    if (call.url === BASE + '/hinh-anh') { return { hinhAnhUrl: imageUrl.replace('cover', call.body.get('file').name.replace('.jpg', '')) }; }
    if (call.url === BASE) { return { maSach: 50 }; }
    throw new Error('Unexpected API: ' + call.url);
  });
  await openNew(app);
  selectFiles(app, jpg('one'), jpg('two'), jpg('three'));
  const removedPreview = thumbs(app)[0].getAttribute('src');
  app.trigger(imageAction(app, 'remove', 0));
  assert.equal(thumbs(app).length, 2);
  assert.ok(app.revokedPreviewUrls.includes(removedPreview));
  app.trigger(imageAction(app, 'cover', 1));
  app.trigger('sellerBookForm', 'submit');
  await settle();
  assert.deepEqual(uploadsOf(app).map(call => call.body.get('file').name), ['three.jpg', 'two.jpg']);
  assert.deepEqual(writes(app).at(-1).body.hinhAnhUrls, [imageUrl.replace('cover', 'three'), imageUrl.replace('cover', 'two')]);
  assert.equal(writes(app).at(-1).body.hinhAnhUrl, imageUrl.replace('cover', 'three'));
});

test('editing loads every existing image, saves the edited list and resets state between books', async () => {
  const book = { ...existingBook, hinhAnhUrl: '/images/a.jpg', hinhAnhUrls: ['/images/a.jpg', '/images/b.jpg', '/images/c.jpg'] };
  const app = await seller(async call => {
    if (call.url === BASE + '/41' && call.method === 'PUT') { return { ...book, ...call.body }; }
    if (call.url === BASE + '/hinh-anh') { return { hinhAnhUrl: imageUrl }; }
    if (call.url === BASE) { return { maSach: 50 }; }
    throw new Error('Unexpected API: ' + call.url);
  }, book);
  app.trigger(app.get('sellerBooksRows').querySelector('[data-book-action="edit"]'));
  await settle();
  assert.deepEqual(thumbs(app).map(img => img.getAttribute('src')), book.hinhAnhUrls);
  app.trigger('sellerBookForm', 'submit');
  await settle();
  assert.deepEqual(writes(app).at(-1).body.hinhAnhUrls, book.hinhAnhUrls);
  assert.equal(uploadsOf(app).length, 0);

  app.trigger(app.get('sellerBooksRows').querySelector('[data-book-action="edit"]'));
  await settle();
  app.trigger(imageAction(app, 'remove', 0));
  selectFiles(app, jpg('new'));
  app.trigger(imageAction(app, 'cover', 2));
  app.trigger('sellerBookForm', 'submit');
  await settle();
  assert.equal(uploadsOf(app).length, 1);
  assert.deepEqual(writes(app).at(-1).body.hinhAnhUrls, [imageUrl, '/images/b.jpg', '/images/c.jpg']);
  assert.equal(writes(app).at(-1).body.hinhAnhUrl, imageUrl);

  app.trigger(app.get('sellerBooksRows').querySelector('[data-book-action="edit"]'));
  await settle();
  app.trigger(imageAction(app, 'remove', 0));
  app.trigger(imageAction(app, 'remove', 0));
  app.trigger(imageAction(app, 'remove', 0));
  app.trigger('sellerBookForm', 'submit');
  await settle();
  assert.deepEqual(writes(app).at(-1).body.hinhAnhUrls, []);
  assert.equal(writes(app).at(-1).body.hinhAnhUrl, null);

  await openNew(app);
  selectFiles(app, jpg('discarded'));
  const discarded = thumbs(app)[0].getAttribute('src');
  new app.window.bootstrap.Modal(app.get('sellerBookModal')).hide();
  assert.ok(app.revokedPreviewUrls.includes(discarded));
  await openNew(app);
  assert.equal(thumbs(app).length, 0);
  assert.equal(app.get('bookImagePreviewPanel').classList.contains('d-none'), true);
});

test('a legacy book with only a cover URL is shown as a single image', async () => {
  const app = await seller(async call => { throw new Error('Unexpected API: ' + call.url); }, existingBook);
  app.trigger(app.get('sellerBooksRows').querySelector('[data-book-action="edit"]'));
  await settle();
  assert.deepEqual(thumbs(app).map(img => img.getAttribute('src')), [existingBook.hinhAnhUrl]);
});
