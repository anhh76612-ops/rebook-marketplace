/*
 * Hành vi dùng chung của layout admin: chặn truy cập, danh tính trên sidebar/header,
 * thông báo (#adminNotification) và hộp thoại (#adminModal).
 * Nạp sau auth.js và trước script riêng của từng trang.
 */
(function () {
    'use strict';

    var OB = window.OldBook;
    if (!OB || window.AdminUI) {
        return;
    }

    var Auth = OB.auth;
    var ADMIN_ROLE = 'QUAN_TRI_VIEN';

    var $ = function (id) { return document.getElementById(id); };

    // =========================================================
    // THÔNG BÁO
    // =========================================================

    var NOTICE_STYLES = {
        info: { icon: 'info', color: 'text-primary' },
        success: { icon: 'check_circle', color: 'text-secondary' },
        error: { icon: 'error', color: 'text-error' }
    };

    var noticeTimer = null;

    function hideNotice() {
        var box = $('adminNotification');
        if (box) {
            box.classList.add('hidden');
        }
        window.clearTimeout(noticeTimer);
    }

    function notify(message, type, duration) {
        var box = $('adminNotification');
        if (!box) {
            return;
        }

        var style = NOTICE_STYLES[type] || NOTICE_STYLES.info;
        var icon = $('adminNotificationIcon');
        icon.textContent = style.icon;
        icon.className = 'material-symbols-outlined ' + style.color;
        $('adminNotificationMessage').textContent = message;
        box.classList.remove('hidden');

        window.clearTimeout(noticeTimer);
        noticeTimer = window.setTimeout(hideNotice, duration || (type === 'error' ? 7000 : 4000));
    }

    // =========================================================
    // HỘP THOẠI
    // =========================================================

    var BUTTON_STYLES = {
        primary: 'bg-primary text-on-primary hover:bg-primary-container',
        danger: 'bg-mahogany-danger text-on-error hover:opacity-90',
        neutral: 'bg-surface-cream text-primary hover:bg-surface-container'
    };

    var modalResolve = null;
    var modalOpener = null;

    function closeModal(result) {
        var modal = $('adminModal');
        if (!modal || modal.classList.contains('hidden')) {
            return;
        }

        modal.classList.add('hidden');
        modal.classList.remove('flex');

        if (modalOpener && modalOpener.focus) {
            modalOpener.focus();
        }

        var resolve = modalResolve;
        modalResolve = null;
        modalOpener = null;
        if (resolve) {
            resolve(result === undefined ? null : result);
        }
    }

    /*
     * options.title   : tiêu đề
     * options.body    : chuỗi (luôn hiển thị dạng văn bản thuần) hoặc một phần tử DOM
     * options.actions : [{ key, label, style: 'primary' | 'danger' | 'neutral' }]
     *
     * Trả về Promise nhận key của nút được bấm, hoặc null nếu hộp thoại bị đóng.
     */
    function openModal(options) {
        var modal = $('adminModal');
        if (!modal) {
            return Promise.resolve(null);
        }

        closeModal(null);
        options = options || {};

        $('adminModalTitle').textContent = options.title || '';

        var body = $('adminModalBody');
        body.textContent = '';
        if (options.body && options.body.nodeType) {
            body.appendChild(options.body);
        } else {
            body.textContent = options.body || '';
        }

        var actions = options.actions || [{ key: 'close', label: 'Đóng', style: 'neutral' }];
        var footer = $('adminModalFooter');
        footer.textContent = '';
        actions.forEach(function (action) {
            var button = document.createElement('button');
            button.type = 'button';
            button.className = 'rounded-lg px-4 py-2.5 font-label-lg text-label-lg transition-colors ' +
                (BUTTON_STYLES[action.style] || BUTTON_STYLES.neutral);
            button.textContent = action.label;
            button.addEventListener('click', function () { closeModal(action.key); });
            footer.appendChild(button);
        });

        modalOpener = document.activeElement;
        modal.classList.remove('hidden');
        modal.classList.add('flex');

        // Nút đầu tiên là nút an toàn (Hủy/Đóng), nên Enter không vô tình xác nhận thao tác nguy hiểm.
        if (footer.firstChild) {
            footer.firstChild.focus();
        }

        return new Promise(function (resolve) { modalResolve = resolve; });
    }

    // Trả về Promise<boolean>: true khi người dùng bấm xác nhận.
    function confirm(options) {
        options = options || {};

        return openModal({
            title: options.title || 'Xác nhận',
            body: options.message,
            actions: [
                { key: 'cancel', label: options.cancelLabel || 'Hủy', style: 'neutral' },
                { key: 'ok', label: options.confirmLabel || 'Xác nhận', style: options.danger ? 'danger' : 'primary' }
            ]
        }).then(function (key) { return key === 'ok'; });
    }

    // =========================================================
    // QUYỀN TRUY CẬP
    // =========================================================

    function showDenied() {
        var main = document.querySelector('main');
        if (!main) {
            return;
        }

        main.innerHTML =
            '<div class="px-6 py-10">' +
            '<div class="mx-auto max-w-xl rounded-2xl border border-line-border bg-surface-white p-8 text-center shadow-sm">' +
            '<span class="material-symbols-outlined text-5xl text-text-muted">admin_panel_settings</span>' +
            '<h1 class="mt-3 font-headline-md text-headline-md text-primary">Bạn không có quyền truy cập khu vực quản trị</h1>' +
            '<p class="mt-2 font-body-md text-body-md text-text-muted">Chức năng này chỉ dành cho Quản trị viên.</p>' +
            '<a href="/" class="mt-5 inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2.5 font-label-lg text-label-lg text-on-primary">Về trang chủ</a>' +
            '</div>' +
            '</div>';
    }

    /*
     * Chỉ là lớp giao diện: trang HTML admin được phục vụ công khai vì JWT nằm trong localStorage.
     * Dữ liệu được bảo vệ thật sự ở /api/admin/** (SecurityConfig).
     */
    function guard() {
        if (!Auth.isLoggedIn()) {
            Auth.redirectToLogin();
            return false;
        }
        if (!Auth.hasRole(ADMIN_ROLE)) {
            showDenied();
            return false;
        }
        return true;
    }

    // true nếu lỗi đã được xử lý xong: 401 → về trang đăng nhập, 403 → màn hình từ chối.
    function handleApiError(err) {
        if (Auth.handleAuthError(err, ADMIN_ROLE)) {
            return true;
        }
        if (err && err.status === 403) {
            showDenied();
            return true;
        }
        return false;
    }

    // =========================================================
    // DANH TÍNH ADMIN
    // =========================================================

    function renderIdentity(profile) {
        if (!profile) {
            return;
        }

        var email = $('adminSidebarEmail');
        if (email) {
            email.textContent = profile.email || '';
        }

        if (!profile.avatarUrl) {
            return;
        }

        var holders = document.querySelectorAll('[data-admin-avatar]');
        for (var i = 0; i < holders.length; i++) {
            var image = document.createElement('img');
            image.src = profile.avatarUrl;
            image.alt = 'Ảnh đại diện';
            image.className = 'h-full w-full object-cover';
            holders[i].textContent = '';
            holders[i].appendChild(image);
        }
    }

    function init() {
        var closeNotice = $('btnCloseNotification');
        if (closeNotice) {
            closeNotice.addEventListener('click', hideNotice);
        }

        var modal = $('adminModal');
        if (modal) {
            $('btnCloseAdminModal').addEventListener('click', function () { closeModal(null); });
            modal.addEventListener('click', function (event) {
                if (event.target === modal) {
                    closeModal(null);
                }
            });
            document.addEventListener('keydown', function (event) {
                if (event.key === 'Escape') {
                    closeModal(null);
                }
            });
        }

        if (Auth.isLoggedIn() && Auth.hasRole(ADMIN_ROLE)) {
            // auth.js đã tự điền tên vào [data-user-name]; email và ảnh đại diện thì nó không giữ lại.
            OB.request('GET', '/api/profile').then(renderIdentity, function () { });
        }
    }

    window.AdminUI = {
        ROLE: ADMIN_ROLE,
        notify: notify,
        openModal: openModal,
        closeModal: closeModal,
        confirm: confirm,
        guard: guard,
        handleApiError: handleApiError
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
