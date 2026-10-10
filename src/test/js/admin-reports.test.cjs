'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { boot, settle } = require('./dom-harness.cjs');

const API = '/api/admin/bao-cao/doanh-thu';
const profile = { hoTen: 'Quản trị viên', email: 'admin@example.com' };

// Báo cáo `days` ngày kết thúc ở `end`; sales: { 'yyyy-MM-dd': [soDon, gmv, hoaHong] }, các ngày còn lại bằng 0.
function reportData(days, sales = {}, end = '2026-10-10') {
  const [year, month, day] = end.split('-').map(Number);
  const points = Array.from({ length: days }, (_, index) => {
    const ngay = new Date(Date.UTC(year, month - 1, day - (days - 1 - index))).toISOString().slice(0, 10);
    const [soDon, gmv, hoaHong] = sales[ngay] || [0, 0, 0];
    return { ngay, soDon, gmv, hoaHong };
  });
  const sum = key => points.reduce((total, point) => total + point[key], 0);
  return { tuNgay: points[0].ngay, denNgay: end, tongSoDon: sum('soDon'), tongGmv: sum('gmv'), tongHoaHong: sum('hoaHong'), points };
}

const weekSales = { '2026-10-10': [2, 200000, 10000], '2026-10-07': [1, 100000, 5000] };

function reports(handler, role = 'QUAN_TRI_VIEN') {
  return boot('admin/reports.html', ['admin/layout.js', 'admin/reports.js'], async call => {
    if (call.url === '/api/profile') { return profile; }
    if (call.url.startsWith(API + '?')) { return handler(call); }
    throw new Error('Unexpected API call: ' + call.url);
  }, role, { pathname: '/admin/reports' });
}

const reportCalls = app => app.calls.filter(call => call.url.startsWith(API));
const range = call => Object.fromEntries(new URL('http://x' + call.url).searchParams);
const daysBetween = ({ tuNgay, denNgay }) => Math.round((Date.parse(denNgay) - Date.parse(tuNgay)) / 86400000) + 1;
const pressed = app => app.get('reportPresets').querySelectorAll('button')
  .filter(button => button.getAttribute('aria-pressed') === 'true').map(button => button.getAttribute('data-preset'));
const text = (app, id) => app.get(id).textContent.replace(/\s+/g, ' ').trim();
const hidden = (app, id) => app.get(id).classList.contains('hidden');

test('the report opens on the last 30 days and shows totals, one chart per metric and the daily table', async () => {
  const app = reports(async () => reportData(7, weekSales));
  await settle();

  assert.equal(reportCalls(app).length, 1);
  assert.equal(daysBetween(range(reportCalls(app)[0])), 30);
  assert.equal(range(reportCalls(app)[0]).denNgay, app.get('reportTo').value);
  assert.deepEqual(pressed(app), ['30d']);

  assert.equal(text(app, 'reportTotalSoDon'), '3');
  assert.equal(text(app, 'reportTotalGmv'), '300.000 đ');
  assert.equal(text(app, 'reportTotalHoaHong'), '15.000 đ');
  assert.equal(text(app, 'reportRange'), 'Từ 04/10/2026 đến 10/10/2026 (7 ngày)');
  assert.equal(text(app, 'reportChartNote'), 'Mỗi cột là một ngày');
  assert.equal(hidden(app, 'reportEmpty'), true);
  assert.equal(hidden(app, 'reportError'), true);

  const heights = id => app.get(id).querySelectorAll('[data-bar]').map(bar => bar.getAttribute('style'));
  // 200.000 là giá trị lớn nhất và cũng là đỉnh trục; ngày không có đơn thì không có cột.
  assert.deepEqual(heights('chartGmv'), ['height: 0.00%', 'height: 0.00%', 'height: 0.00%', 'height: 50.00%', 'height: 0.00%', 'height: 0.00%', 'height: 100.00%']);
  // Số đơn lớn nhất là 2 nên đỉnh trục là 2: các vạch 2, 1, 0 đều là số nguyên.
  assert.match(text(app, 'chartSoDon'), /^2\s*1\s*0/);
  assert.match(text(app, 'chartGmv'), /^200\sN\s*100\sN\s*0/);
  // 7 cột: đủ chỗ ghi nhãn dưới từng cột.
  assert.match(text(app, 'chartGmv'), /04\/10\s*05\/10\s*06\/10\s*07\/10\s*08\/10\s*09\/10\s*10\/10$/);

  const rows = app.get('reportRows').querySelectorAll('tr');
  assert.equal(rows.length, 7);
  assert.deepEqual(rows[6].querySelectorAll('td').map(cell => cell.textContent), ['10/10/2026', '2', '200.000 đ', '10.000 đ']);
  assert.deepEqual(['reportSumSoDon', 'reportSumGmv', 'reportSumHoaHong'].map(id => text(app, id)), ['3', '300.000 đ', '15.000 đ']);
  assert.equal(app.get('reportExport').disabled, false);
});

test('quick periods and a custom range each reload the whole report for exactly that range', async () => {
  const app = reports(async () => reportData(7, weekSales));
  await settle();

  app.trigger(app.get('reportPresets').querySelector('[data-preset="7d"]'));
  await settle();
  assert.equal(daysBetween(range(reportCalls(app)[1])), 7);
  assert.deepEqual(pressed(app), ['7d']);

  app.trigger(app.get('reportPresets').querySelector('[data-preset="last-month"]'));
  await settle();
  const lastMonth = range(reportCalls(app)[2]);
  assert.match(lastMonth.tuNgay, /-01$/);
  assert.equal(lastMonth.tuNgay.slice(0, 7), lastMonth.denNgay.slice(0, 7));
  const now = new Date();
  const firstOfThisMonth = now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0') + '-01';
  assert.ok(lastMonth.denNgay < firstOfThisMonth, 'last month ends before this month starts');
  assert.deepEqual(pressed(app), ['last-month']);

  app.set('reportFrom', '2026-08-01');
  app.set('reportTo', '2026-08-15');
  app.trigger('reportFilter', 'submit');
  await settle();
  assert.deepEqual(range(reportCalls(app)[3]), { tuNgay: '2026-08-01', denNgay: '2026-08-15' });
  assert.deepEqual(pressed(app), [], 'a hand-typed range is not one of the quick periods');

  // Khoảng ngày ngược thì báo lỗi ngay, không gọi API.
  app.set('reportFrom', '2026-08-20');
  app.trigger('reportFilter', 'submit');
  await settle();
  assert.equal(reportCalls(app).length, 4);
  assert.equal(hidden(app, 'reportError'), false);
  assert.equal(text(app, 'reportErrorText'), 'Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.');
});

test('a period longer than 92 days charts one bar per month while the table stays daily', async () => {
  const sales = { '2026-07-15': [4, 400000, 20000], '2026-07-20': [1, 100000, 5000], '2026-10-01': [2, 250000, 12500] };
  const app = reports(async () => reportData(120, sales));
  await settle();

  // 120 ngày kết thúc 10/10/2026 trải qua tháng 6, 7, 8, 9, 10.
  assert.equal(text(app, 'reportChartNote'), 'Mỗi cột là một tháng, vì kỳ dài hơn 92 ngày');
  const bars = app.get('chartSoDon').querySelectorAll('[data-bar]').map(bar => bar.getAttribute('style'));
  assert.equal(bars.length, 5);
  // Tháng 7 có 5 đơn (đỉnh trục 10), tháng 10 có 2 đơn.
  assert.deepEqual(bars, ['height: 0.00%', 'height: 50.00%', 'height: 0.00%', 'height: 0.00%', 'height: 20.00%']);
  assert.match(text(app, 'chartSoDon'), /06\/2026\s*07\/2026\s*08\/2026\s*09\/2026\s*10\/2026$/);
  assert.equal(app.get('reportRows').querySelectorAll('tr').length, 120);
});

test('export writes the report on screen as CSV with ISO dates, plain numbers and a total row', async () => {
  const app = reports(async () => reportData(7, weekSales));
  await settle();

  const files = [];
  app.window.AdminUI.downloadFile = (name, content, type) => files.push({ name, content, type });
  app.trigger('reportExport');

  assert.equal(files.length, 1);
  assert.equal(files[0].name, 'doanh-thu-san_2026-10-04_2026-10-10.csv');
  assert.match(files[0].type, /^text\/csv/);
  const lines = files[0].content.split('\r\n');
  assert.equal(lines.length, 9);
  assert.equal(lines[0], '"Ngày giao","Số đơn giao thành công","GMV (đ)","Hoa hồng sàn (đ)"');
  assert.equal(lines[4], '"2026-10-07","1","100000","5000"');
  assert.equal(lines[7], '"2026-10-10","2","200000","10000"');
  assert.equal(lines[8], '"Tổng cộng","3","300000","15000"');
});

test('a period without delivered orders shows the empty state and nothing to export', async () => {
  const app = reports(async () => reportData(30));
  await settle();

  assert.equal(text(app, 'reportTotalSoDon'), '0');
  assert.equal(text(app, 'reportTotalHoaHong'), '0 đ');
  assert.equal(hidden(app, 'reportEmpty'), false);
  assert.equal(hidden(app, 'reportCharts'), true);
  assert.equal(hidden(app, 'reportTable'), true);
  assert.equal(app.get('reportExport').disabled, true);
});

test('a failed reload keeps the previous report on screen and shows the server message', async () => {
  let response = reportData(7, weekSales);
  const app = reports(async () => response);
  await settle();

  response = { httpStatus: 400, data: null, message: 'Kỳ báo cáo tối đa 366 ngày.' };
  app.set('reportFrom', '2024-01-01');
  app.trigger('reportFilter', 'submit');
  await settle();

  assert.equal(hidden(app, 'reportError'), false);
  assert.equal(text(app, 'reportErrorText'), 'Kỳ báo cáo tối đa 366 ngày.');
  assert.equal(text(app, 'reportTotalGmv'), '300.000 đ');
  assert.equal(text(app, 'reportRange'), 'Từ 04/10/2026 đến 10/10/2026 (7 ngày)');
  assert.equal(app.get('reportBody').classList.contains('opacity-50'), false);
  assert.equal(app.get('reportSubmit').disabled, false);
});

test('the report never queries the API for guests or other roles', async () => {
  const fail = async () => { throw new Error('Must not query the report API'); };

  const guest = reports(fail, null);
  await settle();
  assert.deepEqual(guest.redirects, ['/login?redirect=%2Fadmin%2Freports']);
  assert.equal(guest.calls.length, 0);

  const manager = reports(fail, 'QUAN_LY');
  await settle();
  assert.equal(reportCalls(manager).length, 0);
  assert.match(manager.document.querySelector('main').textContent, /Bạn không có quyền truy cập khu vực quản trị/);
});
