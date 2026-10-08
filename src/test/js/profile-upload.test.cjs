'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { File } = require('node:buffer');
const { boot, settle } = require('./dom-harness.cjs');

const initialProfile = {
  maND: 8, email: 'reader@example.com', hoTen: 'Người đọc',
  soDienThoai: '0912345678', tieuSu: 'Thích đọc sách',
  avatarUrl: 'https://res.cloudinary.com/oldbook/image/upload/saved.png'
};
const uploadedUrl = 'https://res.cloudinary.com/oldbook/image/upload/new-avatar.png';
const mutations = app => app.calls.filter(call => call.method !== 'GET');

function profileApp(handler) {
  let saved = { ...initialProfile };
  return boot('profile.html', ['profile.js'], async call => {
    if (handler) {
      const result = await handler(call);
      if (result !== undefined) { return result; }
    }
    if (call.url === '/api/profile' && call.method === 'GET') { return saved; }
    if (call.url === '/api/profile/avatar') { return { avatarUrl: uploadedUrl }; }
    if (call.url === '/api/profile' && call.method === 'PUT') {
      saved = { ...saved, ...call.body };
      return saved;
    }
    throw new Error('Unexpected request ' + call.method + ' ' + call.url);
  });
}

function selectAvatar(app, file = new File(['image bytes'], 'avatar.png', { type: 'image/png' })) {
  app.get('profileAvatarFile').files = [file];
  app.trigger('profileAvatarFile', 'change');
  return file;
}

test('avatar previews locally, uploads multipart with JWT, and only then saves the returned URL', async () => {
  let finishUpload;
  const app = profileApp(call => call.url === '/api/profile/avatar'
    ? new Promise(resolve => { finishUpload = resolve; }) : undefined);
  await settle();
  assert.equal(app.get('profileAvatarFile').getAttribute('type'), 'file');
  assert.equal(app.get('profileAvatarUrl'), null);
  const file = selectAvatar(app);
  assert.equal(app.get('profileAvatar').src, app.previewUrls[0].url);
  assert.equal(app.previewUrls[0].file, file);
  assert.equal(mutations(app).length, 0, 'selection does not upload before saving');

  app.trigger('profileForm', 'submit');
  app.trigger('profileForm', 'submit');
  await settle();
  const upload = mutations(app)[0];
  assert.equal(mutations(app).length, 1, 'duplicate submission is ignored while uploading');
  assert.equal(upload.method, 'POST');
  assert.equal(upload.url, '/api/profile/avatar');
  assert.ok(upload.body instanceof FormData);
  assert.deepEqual([...upload.body.keys()], ['file']);
  assert.equal(upload.body.get('file').name, 'avatar.png');
  assert.equal(await upload.body.get('file').text(), await file.text());
  assert.equal(upload.headers.Authorization, 'Bearer ' + app.window.OldBook.auth.getToken());
  assert.equal(upload.headers['Content-Type'], undefined, 'the browser supplies the multipart boundary');
  assert.equal(app.get('profileForm').getAttribute('aria-busy'), 'true');
  assert.ok(app.get('profileForm').elements.every(control => control.disabled));

  finishUpload({ avatarUrl: uploadedUrl });
  await settle();
  assert.deepEqual(mutations(app).map(call => [call.method, call.url]), [
    ['POST', '/api/profile/avatar'], ['PUT', '/api/profile']
  ]);
  assert.equal(mutations(app)[1].body.avatarUrl, uploadedUrl);
  assert.equal(app.get('profileAvatar').src, uploadedUrl);
  assert.deepEqual(app.get('profileAvatarFile').files, []);
  assert.deepEqual(app.revokedPreviewUrls, [app.previewUrls[0].url]);
  assert.equal(app.get('profileSaveBtn').disabled, false);
  assert.equal(app.get('profileEmail').hasAttribute('readonly'), true);
  assert.equal(app.get('profileEmail').disabled, false);
  assert.equal(app.get('profileForm').getAttribute('aria-busy'), 'false');
});

test('saving profile text without selecting a file preserves the stored avatar', async () => {
  const app = profileApp();
  await settle();
  app.set('profileHoTen', 'Tên mới');
  app.trigger('profileForm', 'submit');
  await settle();
  assert.equal(mutations(app).length, 1);
  assert.equal(mutations(app)[0].method, 'PUT');
  assert.equal(mutations(app)[0].body.avatarUrl, initialProfile.avatarUrl);
  assert.equal(mutations(app)[0].body.hoTen, 'Tên mới');
});

test('cancel restores saved values after previewing or removing the avatar and releases local previews', async () => {
  const app = profileApp();
  await settle();
  selectAvatar(app);
  app.set('profileHoTen', 'Chưa lưu');
  app.trigger('profileCancelBtn');
  assert.equal(app.get('profileHoTen').value, initialProfile.hoTen);
  assert.equal(app.get('profileAvatar').src, initialProfile.avatarUrl);
  assert.deepEqual(app.get('profileAvatarFile').files, []);
  assert.deepEqual(app.revokedPreviewUrls, [app.previewUrls[0].url]);

  app.trigger('profileAvatarRemoveBtn');
  assert.ok(app.get('profileAvatar').classList.contains('d-none'));
  app.trigger('profileCancelBtn');
  assert.equal(app.get('profileAvatar').src, initialProfile.avatarUrl);
  assert.equal(app.get('profileAvatar').classList.contains('d-none'), false);
  assert.equal(mutations(app).length, 0);
});

test('removing a selected avatar saves null without uploading the discarded file', async () => {
  const app = profileApp();
  await settle();
  selectAvatar(app);
  app.trigger('profileAvatarRemoveBtn');
  assert.deepEqual(app.get('profileAvatarFile').files, []);
  assert.equal(app.get('profileAvatarRemoveBtn').disabled, true);
  app.trigger('profileForm', 'submit');
  await settle();
  assert.equal(mutations(app).length, 1);
  assert.equal(mutations(app)[0].url, '/api/profile');
  assert.equal(mutations(app)[0].body.avatarUrl, null);
  assert.ok(app.get('profileAvatar').classList.contains('d-none'));
});

test('failed upload retains the file and preview for retry and never saves an incomplete profile', async () => {
  let attempts = 0;
  const app = profileApp(call => {
    if (call.url === '/api/profile/avatar' && ++attempts === 1) {
      return { httpStatus: 502, message: 'Upload unavailable' };
    }
  });
  await settle();
  const file = selectAvatar(app);
  app.trigger('profileForm', 'submit');
  await settle();
  assert.equal(mutations(app).length, 1);
  assert.equal(app.get('profileAvatarFile').files[0], file);
  assert.equal(app.get('profileAvatar').src, app.previewUrls[0].url);
  assert.deepEqual(app.revokedPreviewUrls, []);
  assert.equal(app.get('profileSaveBtn').disabled, false);

  app.trigger('profileForm', 'submit');
  await settle();
  assert.deepEqual(mutations(app).map(call => call.method), ['POST', 'POST', 'PUT']);
  assert.equal(mutations(app)[2].body.avatarUrl, uploadedUrl);
});

test('profile save retry reuses a successful upload rather than sending the same image twice', async () => {
  let saves = 0;
  const app = profileApp(call => {
    if (call.url === '/api/profile' && call.method === 'PUT' && ++saves === 1) {
      return { httpStatus: 500, message: 'Profile temporarily unavailable' };
    }
  });
  await settle();
  const file = selectAvatar(app);
  app.trigger('profileForm', 'submit');
  await settle();
  assert.equal(app.get('profileAvatarFile').files[0], file);
  assert.equal(app.get('profileSaveBtn').disabled, false);
  assert.deepEqual(app.revokedPreviewUrls, []);

  app.set('profileHoTen', 'Tên sau khi thử lại');
  app.trigger('profileForm', 'submit');
  await settle();
  assert.deepEqual(mutations(app).map(call => call.method), ['POST', 'PUT', 'PUT']);
  assert.equal(mutations(app)[2].body.avatarUrl, uploadedUrl);
  assert.equal(mutations(app)[2].body.hoTen, 'Tên sau khi thử lại');
});

test('invalid file types, empty images and images larger than 10 MiB are rejected before preview or upload', async () => {
  for (const file of [
    new File(['<svg/>'], 'image.svg', { type: 'image/svg+xml' }),
    new File([], 'empty.png', { type: 'image/png' }),
    new File([new Uint8Array(10 * 1024 * 1024 + 1)], 'large.jpg', { type: 'image/jpeg' })
  ]) {
    const app = profileApp();
    await settle();
    selectAvatar(app, file);
    assert.ok(app.get('profileAvatarFile').classList.contains('is-invalid'), file.name);
    assert.ok(app.get('profileAvatarError').textContent.length > 0, file.name);
    assert.deepEqual(app.get('profileAvatarFile').files, []);
    assert.equal(app.get('profileAvatar').src, initialProfile.avatarUrl);
    assert.deepEqual(app.previewUrls, []);
    assert.equal(mutations(app).length, 0);
  }
});

test('a pending avatar removal survives canceling the chooser or choosing an invalid file', async () => {
  const app = profileApp();
  await settle();
  app.trigger('profileAvatarRemoveBtn');
  app.get('profileAvatarFile').files = [];
  app.trigger('profileAvatarFile', 'change');
  assert.ok(app.get('profileAvatar').classList.contains('d-none'));
  selectAvatar(app, new File(['<svg/>'], 'invalid.svg', { type: 'image/svg+xml' }));
  assert.ok(app.get('profileAvatar').classList.contains('d-none'));
  app.trigger('profileForm', 'submit');
  await settle();
  assert.equal(mutations(app).length, 1);
  assert.equal(mutations(app)[0].body.avatarUrl, null);
});

test('a missing, non-string or insecure upload URL never reaches the profile save', async () => {
  for (const result of [null, {}, { avatarUrl: 42 }, { avatarUrl: 'http://example.com/avatar.png' }]) {
    const app = profileApp(call => call.url === '/api/profile/avatar' ? result : undefined);
    await settle();
    const file = selectAvatar(app);
    app.trigger('profileForm', 'submit');
    await settle();
    assert.equal(mutations(app).length, 1);
    assert.equal(mutations(app)[0].method, 'POST');
    assert.equal(app.get('profileAvatarFile').files[0], file);
    assert.equal(app.get('profileSaveBtn').disabled, false);
  }
});
