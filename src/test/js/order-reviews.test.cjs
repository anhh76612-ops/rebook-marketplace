'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { boot, settle, event } = require('./dom-harness.cjs');
const { File } = require('node:buffer');

const savedReview = { maDanhGia: 20, hoTen: 'Khách hàng', soSao: 4, binhLuan: 'Sách còn tốt.', ngayDanhGia: '2026-10-11T10:00:00' };
function orders() {
  return [{ maDH: 1, storeOrders: [
    { maDHCH: 2, tenCuaHang: 'Hiệu sách', trangThaiDonHang: 'DA_GIAO', items: [
      { maCTDH: 11, maSach: 3, tenSach: 'Sách <hay>', donGia: 50000, soLuong: 1, coTheDanhGia: true, danhGia: null },
      { maCTDH: 12, maSach: 4, tenSach: 'Sách đã đánh giá', donGia: 40000, soLuong: 1, coTheDanhGia: false, danhGia: savedReview },
      { maCTDH: 13, maSach: 5, tenSach: 'Sách không được đánh giá', donGia: 40000, soLuong: 1, coTheDanhGia: false, danhGia: null }
    ] },
    { maDHCH: 3, tenCuaHang: 'Hiệu sách khác', trangThaiDonHang: 'DANG_GIAO', items: [
      { maCTDH: 14, maSach: 6, tenSach: 'Sách đang giao', donGia: 40000, soLuong: 1, coTheDanhGia: true, danhGia: null }
    ] }
  ] }];
}
function reviewApp(handler, fixture = orders()) {
  return boot('orders.html', ['order-tracking.js', 'order-reviews.js'], call => {
    if (call.url === '/api/profile') { return { hoTen: 'Khách hàng' }; }
    if (handler) { return handler(call, fixture); }
    assert.equal(call.url, '/api/order/orders');
    return fixture;
  }, 'KHACH_HANG', { runInline: true, pathname: '/orders' });
}
function action(app, id = 11) { return app.get('ordersList').querySelector('[data-review-order-item="' + id + '"]'); }
function fill(app, stars = 4, comment = 'Sách còn tốt.') {
  app.get('reviewStars' + stars).checked = true;
  app.set('orderReviewComment', comment);
  app.trigger('orderReviewComment', 'input');
}
function submit(app) { app.trigger('orderReviewForm', 'submit'); }
function attach(app, files) {
  app.get('orderReviewFiles').files = files;
  app.trigger('orderReviewFiles', 'change');
}
function image(name = 'book.jpg') { return new File(['image bytes'], name, { type: 'image/jpeg' }); }
function video(name = 'book.mp4') { return new File(['video bytes'], name, { type: 'video/mp4' }); }
const MB = 1024 * 1024;

test('review actions appear only for eligible delivered items in all and delivered tabs', async () => {
  const app = reviewApp();
  await settle();
  assert.ok(action(app));
  for (const id of [12, 13, 14]) { assert.equal(action(app, id), null); }
  app.trigger(app.get('orderTabs').querySelector('[data-tab="DA_GIAO"]'));
  assert.ok(action(app));
  assert.equal(app.get('ordersList').querySelectorAll('[data-review-order-item]').length, 1);
  app.trigger(app.get('orderTabs').querySelector('[data-tab="DANG_GIAO"]'));
  assert.equal(app.get('ordersList').querySelector('[data-review-order-item]'), null);
});

test('existing reviews render escaped comments, accessible stars and a link to the book', async () => {
  const fixture = orders();
  const unsafe = '<img src=x onerror="alert(1)"> & "hay"\n<script>alert(2)</script>';
  fixture[0].storeOrders[0].items[1].danhGia = { ...savedReview, binhLuan: unsafe };
  const app = reviewApp(null, fixture);
  await settle();
  const review = app.get('ordersList').querySelector('.order-review');
  assert.equal(review.querySelector('p').textContent, unsafe);
  assert.equal(review.querySelector('img'), null);
  assert.equal(review.querySelector('script'), null);
  assert.equal(review.querySelector('[role="img"]').getAttribute('aria-label'), '4 trên 5 sao');
  assert.equal(review.querySelector('a').getAttribute('href'), '/books/4#customerReviews');
});

test('review form requires a valid rating, a nonblank comment, and at most 2000 characters', async () => {
  const app = reviewApp();
  await settle();
  app.trigger(action(app));
  assert.equal(app.get('orderReviewBookTitle').textContent, 'Sách <hay>');
  assert.equal(app.document.activeElement.id, 'reviewStars1');
  app.set('orderReviewComment', 'Nội dung');
  submit(app);
  assert.match(app.get('orderReviewError').textContent, /1 đến 5 sao/);
  fill(app, 3, ' \n\t ');
  submit(app);
  assert.match(app.get('orderReviewError').textContent, /nhập bình luận/);
  assert.equal(app.document.activeElement.id, 'orderReviewComment');
  app.set('orderReviewComment', 'x'.repeat(2001));
  submit(app);
  assert.match(app.get('orderReviewError').textContent, /2000 ký tự/);
  app.get('reviewStars3').value = '6';
  app.set('orderReviewComment', 'Nội dung');
  submit(app);
  assert.match(app.get('orderReviewError').textContent, /1 đến 5 sao/);
  assert.equal(app.calls.filter(call => call.method === 'POST').length, 0);
});

test('saving sends trimmed review once, locks dismissal, and updates both tabs after success', async () => {
  let finish;
  let post;
  const app = reviewApp((call, fixture) => {
    if (call.method === 'GET') { return fixture; }
    post = call;
    return new Promise(resolve => { finish = () => {
      fixture[0].storeOrders[0].items[0].danhGia = { ...savedReview, soSao: 5, binhLuan: 'Rất hay <3' };
      fixture[0].storeOrders[0].items[0].coTheDanhGia = false;
      resolve(fixture[0].storeOrders[0].items[0].danhGia);
    }; });
  });
  await settle();
  app.trigger(action(app));
  fill(app, 5, '  Rất hay <3\n ');
  assert.equal(app.get('orderReviewCounter').textContent, '14/2000 ký tự');
  submit(app);
  submit(app);
  await settle();
  assert.equal(app.calls.filter(call => call.method === 'POST').length, 1);
  assert.equal(post.url, '/api/reviews/order-items/11');
  assert.deepEqual(post.body, { soSao: 5, binhLuan: 'Rất hay <3' });
  assert.match(post.headers.Authorization, /^Bearer /);
  assert.equal(app.get('orderReviewSubmit').disabled, true);
  assert.equal(app.get('orderReviewComment').disabled, true);
  assert.equal(app.get('orderReviewForm').getAttribute('aria-busy'), 'true');
  app.window.bootstrap.Modal.getOrCreateInstance(app.get('orderReviewModal')).hide();
  assert.equal(app.get('orderReviewModal').classList.contains('show'), true);
  finish();
  await settle();
  assert.equal(app.get('orderReviewModal').classList.contains('show'), false);
  assert.equal(app.get('orderReviewSubmit').disabled, false);
  assert.equal(action(app), null);
  assert.match(app.get('ordersList').textContent, /Rất hay <3/);
  assert.equal(app.get('ordersAlert').classList.contains('alert-success'), true);
  assert.equal(app.calls.filter(call => call.url === '/api/order/orders').length, 2);
  app.trigger(app.get('orderTabs').querySelector('[data-tab="DA_GIAO"]'));
  assert.equal(action(app), null);
  assert.match(app.get('ordersList').textContent, /Rất hay <3/);
});

for (const httpStatus of [400, 404, 409, 500]) {
  test('HTTP ' + httpStatus + ' keeps the draft and allows a retry with an escaped error', async () => {
    let attempts = 0;
    const message = '<b>Chưa thể gửi đánh giá</b>';
    const app = reviewApp((call, fixture) => {
      if (call.method === 'GET') { return fixture; }
      if (++attempts === 1) { return { httpStatus, message }; }
      fixture[0].storeOrders[0].items[0].danhGia = savedReview;
      fixture[0].storeOrders[0].items[0].coTheDanhGia = false;
      return savedReview;
    });
    await settle();
    app.trigger(action(app));
    fill(app);
    submit(app);
    await settle();
    assert.equal(app.get('orderReviewModal').classList.contains('show'), true);
    assert.equal(app.get('orderReviewComment').value, 'Sách còn tốt.');
    assert.equal(app.get('reviewStars4').checked, true);
    assert.equal(app.get('orderReviewError').textContent, message);
    assert.equal(app.get('orderReviewError').querySelector('b'), null);
    assert.equal(app.get('orderReviewSubmit').disabled, false);
    submit(app);
    await settle();
    assert.equal(attempts, 2);
    assert.equal(app.get('orderReviewModal').classList.contains('show'), false);
  });
}

test('order refresh and tab changes preserve an open review draft', async () => {
  const app = reviewApp();
  await settle();
  app.trigger(action(app));
  fill(app, 2, 'Bản nháp đang viết');
  app.trigger('btnRefresh');
  await settle();
  app.trigger(app.get('orderTabs').querySelector('[data-tab="DA_GIAO"]'));
  assert.equal(app.get('orderReviewModal').classList.contains('show'), true);
  assert.equal(app.get('orderReviewComment').value, 'Bản nháp đang viết');
  assert.equal(app.get('reviewStars2').checked, true);
});

test('closing restores visible focus after refresh and clears the cancelled draft on reopening', async () => {
  const app = reviewApp();
  await settle();
  app.trigger(action(app));
  fill(app, 2, 'Bản nháp không gửi');
  app.trigger('btnRefresh');
  await settle();
  app.trigger(app.get('orderReviewModal').querySelector('[data-review-close]'));
  assert.equal(app.get('orderReviewModal').classList.contains('show'), false);
  assert.equal(app.document.activeElement.id, 'ordersTitle');
  app.trigger(action(app));
  assert.equal(app.get('orderReviewComment').value, '');
  assert.equal(app.get('orderReviewCounter').textContent, '0/2000 ký tự');
  assert.equal(app.get('orderReviewForm').querySelectorAll('input[name="reviewStars"]').some(radio => radio.checked), false);
});

test('a stale order request cannot restore the review action after successful submission', async () => {
  let releaseOldLoad;
  let getCount = 0;
  const app = reviewApp((call, fixture) => {
    if (call.method === 'GET') {
      getCount++;
      if (getCount === 2) {
        const oldOrders = JSON.parse(JSON.stringify(fixture));
        return new Promise(resolve => { releaseOldLoad = () => resolve(oldOrders); });
      }
      return fixture;
    }
    fixture[0].storeOrders[0].items[0].danhGia = savedReview;
    fixture[0].storeOrders[0].items[0].coTheDanhGia = false;
    return savedReview;
  });
  await settle();
  app.trigger(action(app));
  fill(app);
  app.trigger('btnRefresh');
  await settle();
  submit(app);
  await settle();
  assert.equal(action(app), null);
  releaseOldLoad();
  await settle();
  assert.equal(action(app), null);
  assert.match(app.get('ordersList').textContent, /Sách còn tốt/);
});

test('attachment selection previews locally, supports more selections and removal, and never uploads before submit', async () => {
  const app = reviewApp();
  await settle();
  app.trigger(action(app));
  const photo = image('book <front>.jpg');
  const clip = video();
  attach(app, [photo]);
  attach(app, [clip]);
  const previews = app.get('orderReviewPreviews');
  assert.equal(app.get('orderReviewMediaCounter').textContent, '2/5 tệp đã chọn');
  assert.equal(previews.querySelectorAll('img').length, 1);
  assert.equal(previews.querySelectorAll('video').length, 1);
  assert.equal(previews.querySelector('img').getAttribute('alt'), photo.name);
  assert.equal(previews.querySelector('video').hasAttribute('controls'), true);
  assert.equal(previews.querySelector('video').hasAttribute('autoplay'), false);
  assert.equal(app.get('orderReviewFiles').files.length, 0);
  assert.equal(app.calls.filter(call => call.method === 'POST').length, 0);
  assert.deepEqual(app.previewUrls.map(entry => entry.file), [photo, clip]);
  app.trigger(previews.querySelector('[data-review-remove="0"]'));
  assert.equal(previews.querySelector('img'), null);
  assert.equal(app.get('orderReviewMediaCounter').textContent, '1/5 tệp đã chọn');
  assert.deepEqual(app.revokedPreviewUrls, [app.previewUrls[0].url]);
  app.trigger(app.get('orderReviewModal').querySelector('[data-review-close]'));
  assert.deepEqual(app.revokedPreviewUrls, app.previewUrls.map(entry => entry.url));
  assert.equal(app.get('orderReviewPreviews').classList.contains('d-none'), true);
  app.trigger(action(app));
  assert.equal(app.get('orderReviewMediaCounter').textContent, '0/5 tệp đã chọn');
  attach(app, [image()]);
  app.window.dispatchEvent(event('pagehide'));
  assert.deepEqual(app.revokedPreviewUrls, app.previewUrls.map(entry => entry.url));
  assert.equal(app.get('orderReviewMediaCounter').textContent, '0/5 tệp đã chọn');
});

for (const example of [
  { name: 'unsupported SVG image', files: [{ name: 'book.svg', type: 'image/svg+xml', size: 100 }], error: /JPG, PNG, WEBP/ },
  { name: 'image with mismatched extension', files: [{ name: 'book.exe', type: 'image/jpeg', size: 100 }], error: /JPG, PNG, WEBP/ },
  { name: 'unsupported MOV video', files: [{ name: 'book.mov', type: 'video/quicktime', size: 100 }], error: /MP4, WEBM/ },
  { name: 'empty file', files: [{ name: 'book.png', type: 'image/png', size: 0 }], error: /không được trống/ },
  { name: 'image larger than 10 MB', files: [{ name: 'book.jpg', type: 'image/jpeg', size: 10 * MB + 1 }], error: /10 MB/ },
  { name: 'video larger than 50 MB', files: [{ name: 'book.webm', type: 'video/webm', size: 50 * MB + 1 }], error: /50 MB/ },
  { name: 'more than 5 combined attachments', files: Array.from({ length: 6 }, (_, i) => image(i + '.jpg')), error: /tối đa 5/ },
  { name: 'combined attachments larger than 60 MB', files: [
    { name: 'one.mp4', type: 'video/mp4', size: 50 * MB },
    { name: 'two.jpg', type: 'image/jpeg', size: 10 * MB },
    { name: 'three.webp', type: 'image/webp', size: 1 }
  ], error: /60 MB/ }
]) {
  test('attachment validation rejects ' + example.name + ' and preserves earlier selections', async () => {
    const app = reviewApp();
    await settle();
    app.trigger(action(app));
    attach(app, [image()]);
    attach(app, example.files);
    assert.match(app.get('orderReviewError').textContent, example.error);
    assert.equal(app.get('orderReviewMediaCounter').textContent, '1/5 tệp đã chọn');
    assert.equal(app.get('orderReviewPreviews').querySelectorAll('img').length, 1);
    assert.equal(app.previewUrls.length, 1);
    assert.equal(app.revokedPreviewUrls.length, 0);
    assert.equal(app.calls.filter(call => call.method === 'POST').length, 0);
  });
}

test('attachment limits accept exact image, video, batch sizes and all supported formats', async () => {
  const app = reviewApp();
  await settle();
  app.trigger(action(app));
  attach(app, [
    { name: 'book.JPG', type: 'image/jpeg', size: 10 * MB },
    { name: 'book.mp4', type: 'video/mp4', size: 50 * MB }
  ]);
  assert.equal(app.get('orderReviewMediaCounter').textContent, '2/5 tệp đã chọn');
  assert.equal(app.get('orderReviewError').classList.contains('d-none'), true);
  app.trigger(app.get('orderReviewModal').querySelector('[data-review-close]'));
  app.trigger(action(app));
  attach(app, [
    { name: 'book.jpeg', type: 'image/jpeg', size: 1 },
    { name: 'book.png', type: 'image/png', size: 1 },
    { name: 'book.webp', type: 'image/webp', size: 1 },
    { name: 'book.mp4', type: 'video/mp4', size: 1 },
    { name: 'book.webm', type: 'video/webm', size: 1 }
  ]);
  assert.equal(app.get('orderReviewMediaCounter').textContent, '5/5 tệp đã chọn');
  assert.equal(app.get('orderReviewError').classList.contains('d-none'), true);
  assert.equal(app.get('orderReviewPreviews').querySelectorAll('img').length, 3);
  assert.equal(app.get('orderReviewPreviews').querySelectorAll('video').length, 2);
});

test('media submission sends a single authenticated multipart review, locks attachments, and displays the saved media', async () => {
  let finish;
  let post;
  const attachments = [
    { maMedia: 1, loaiMedia: 'IMAGE', mediaUrl: 'https://res.cloudinary.com/demo/image/upload/book.jpg' },
    { maMedia: 2, loaiMedia: 'VIDEO', mediaUrl: 'https://res.cloudinary.com/demo/video/upload/book.mp4' }
  ];
  const app = reviewApp((call, fixture) => {
    if (call.method === 'GET') { return fixture; }
    post = call;
    return new Promise(resolve => { finish = () => {
      const review = { ...savedReview, media: attachments };
      fixture[0].storeOrders[0].items[0].danhGia = review;
      fixture[0].storeOrders[0].items[0].coTheDanhGia = false;
      resolve(review);
    }; });
  });
  await settle();
  app.trigger(action(app));
  fill(app, 4, '  Sách còn tốt. ');
  const photo = image();
  const clip = video();
  attach(app, [photo, clip]);
  submit(app);
  submit(app);
  await settle();
  assert.ok(post.body instanceof FormData);
  assert.equal(post.body.get('soSao'), '4');
  assert.equal(post.body.get('binhLuan'), 'Sách còn tốt.');
  assert.deepEqual(post.body.getAll('files'), [photo, clip]);
  assert.match(post.headers.Authorization, /^Bearer /);
  assert.equal(post.headers['Content-Type'], undefined);
  assert.equal(app.calls.filter(call => call.method === 'POST').length, 1);
  assert.equal(app.get('orderReviewFiles').disabled, true);
  assert.equal(app.get('orderReviewPreviews').querySelector('button').disabled, true);
  app.trigger(app.get('orderReviewPreviews').querySelector('[data-review-remove="0"]'));
  assert.equal(app.get('orderReviewMediaCounter').textContent, '2/5 tệp đã chọn');
  app.window.bootstrap.Modal.getOrCreateInstance(app.get('orderReviewModal')).hide();
  assert.equal(app.get('orderReviewModal').classList.contains('show'), true);
  finish();
  await settle();
  assert.equal(app.get('orderReviewModal').classList.contains('show'), false);
  assert.deepEqual(app.revokedPreviewUrls, app.previewUrls.map(entry => entry.url));
  const review = app.get('ordersList').querySelector('.order-review');
  assert.equal(review.querySelector('img').getAttribute('src'), attachments[0].mediaUrl);
  assert.equal(review.querySelector('video').getAttribute('src'), attachments[1].mediaUrl);
  assert.equal(review.querySelector('video').hasAttribute('controls'), true);
  assert.equal(review.querySelector('video').hasAttribute('autoplay'), false);
});

test('failed media submission preserves files, previews, and review text for retry', async () => {
  let attempts = 0;
  let retried;
  const app = reviewApp((call, fixture) => {
    if (call.method === 'GET') { return fixture; }
    if (++attempts === 1) { return { httpStatus: 500, message: 'Chưa tải được tệp, vui lòng thử lại.' }; }
    retried = call;
    fixture[0].storeOrders[0].items[0].danhGia = savedReview;
    fixture[0].storeOrders[0].items[0].coTheDanhGia = false;
    return savedReview;
  });
  await settle();
  app.trigger(action(app));
  fill(app);
  const photo = image();
  attach(app, [photo]);
  submit(app);
  await settle();
  assert.equal(app.get('orderReviewModal').classList.contains('show'), true);
  assert.equal(app.get('orderReviewComment').value, 'Sách còn tốt.');
  assert.equal(app.get('reviewStars4').checked, true);
  assert.equal(app.get('orderReviewMediaCounter').textContent, '1/5 tệp đã chọn');
  assert.equal(app.get('orderReviewPreviews').querySelector('img').getAttribute('src'), app.previewUrls[0].url);
  assert.equal(app.get('orderReviewFiles').disabled, false);
  assert.equal(app.get('orderReviewPreviews').querySelector('button').disabled, false);
  assert.equal(app.revokedPreviewUrls.length, 0);
  submit(app);
  await settle();
  assert.deepEqual(retried.body.getAll('files'), [photo]);
  assert.equal(attempts, 2);
  assert.deepEqual(app.revokedPreviewUrls, app.previewUrls.map(entry => entry.url));
});

test('removing all attachments restores the existing JSON review request', async () => {
  let post;
  const app = reviewApp((call, fixture) => {
    if (call.method === 'GET') { return fixture; }
    post = call;
    return savedReview;
  });
  await settle();
  app.trigger(action(app));
  fill(app);
  attach(app, [image()]);
  app.trigger(app.get('orderReviewPreviews').querySelector('button'));
  submit(app);
  await settle();
  assert.deepEqual(post.body, { soSao: 4, binhLuan: 'Sách còn tốt.' });
  assert.equal(post.headers['Content-Type'], 'application/json');
});

test('saved media rejects non-HTTPS, non-Cloudinary, credentials, and unknown media types', async () => {
  const fixture = orders();
  const invalidUrls = [
    'javascript:alert(1)', 'http://res.cloudinary.com/demo/image/upload/book.jpg',
    'https://res.cloudinary.com.evil.test/demo/book.jpg', '//res.cloudinary.com/demo/book.jpg',
    'https://username@res.cloudinary.com/demo/image/upload/book.jpg'
  ];
  const app = reviewApp(null, fixture);
  await settle();
  for (const url of invalidUrls) {
    const html = app.window.OldBook.reviews.render({ ...fixture[0].storeOrders[0].items[1],
      danhGia: { ...savedReview, media: [{ loaiMedia: 'IMAGE', mediaUrl: url }] }
    }, 'DA_GIAO');
    assert.equal(html.includes('<img'), false);
    assert.equal(html.includes('review-media'), false);
  }
  const html = app.window.OldBook.reviews.render({ ...fixture[0].storeOrders[0].items[1],
    danhGia: { ...savedReview, media: [{ loaiMedia: 'SCRIPT', mediaUrl: 'https://res.cloudinary.com/demo/book.jpg' }] }
  }, 'DA_GIAO');
  assert.equal(html.includes('review-media'), false);
});
