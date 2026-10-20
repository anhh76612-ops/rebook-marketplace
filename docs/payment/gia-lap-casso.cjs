// Giả lập Casso báo "tiền đã về" khi thử thanh toán chuyển khoản trên máy local.
// Casso thật chỉ gọi được địa chỉ HTTPS công khai, nên trên máy mình script này đóng vai Casso:
// nó gửi một yêu cầu có chữ ký hợp lệ vào webhook của ứng dụng đang chạy.
//
// Chạy từ thư mục gốc của dự án (nơi có file .env):
//   node docs/payment/gia-lap-casso.cjs <mã thanh toán> <số tiền> [cổng]
// Ví dụ:
//   node docs/payment/gia-lap-casso.cjs OB7K2M9QXA 275000
//
// Khóa ký lấy từ dòng payment.casso.checksum-key trong .env. Cổng mặc định là 8080 (server.port trong application.properties).

const crypto = require('crypto');
const fs = require('fs');

function docEnv() {
  const env = {};
  if (!fs.existsSync('.env')) { return env; }
  for (const dong of fs.readFileSync('.env', 'utf8').split(/\r?\n/)) {
    const khop = /^\s*([A-Za-z0-9_.-]+)\s*=(.*)$/.exec(dong);
    if (khop) { env[khop[1]] = khop[2].trim(); }
  }
  return env;
}

const env = docEnv();
const [ma, soTien, cong = '8080'] = process.argv.slice(2);
const khoa = env['payment.casso.checksum-key'] || process.env.PAYMENT_CASSO_CHECKSUM_KEY;

if (!ma || !/^\d+$/.test(soTien || '')) {
  console.error('Cách dùng: node docs/payment/gia-lap-casso.cjs <mã thanh toán> <số tiền> [cổng]');
  process.exit(1);
}
if (!khoa) {
  console.error('Chưa có payment.casso.checksum-key trong .env (hãy chạy lệnh từ thư mục gốc của dự án).');
  process.exit(1);
}

// Mỗi lần chạy là một giao dịch mới; gửi lại đúng một giao dịch thì ứng dụng sẽ bỏ qua.
const id = Date.now() % 1000000000;
const noiDung = JSON.stringify({
  error: 0,
  data: {
    id,
    reference: 'FT' + id,
    description: 'MBVCB.' + id + '. ' + ma + ' thanh toán đơn sách cũ',
    amount: Number(soTien),
    runningBalance: 25000000,
    transactionDateTime: new Date().toISOString().slice(0, 19).replace('T', ' '),
    accountNumber: env['payment.bank.account-no'] || '',
    bankName: 'MBBank',
    bankAbbreviation: 'MB',
    virtualAccountNumber: '',
    virtualAccountName: '',
    counterAccountName: 'NGUYEN VAN KHACH',
    counterAccountNumber: '8888888888',
    counterAccountBankId: '970415',
    counterAccountBankName: 'VietinBank'
  }
});

// Chữ ký Webhook V2 của Casso: HMAC-SHA512 trên "<thời gian>.<JSON đã sắp khóa theo thứ tự chữ cái>".
const sapXep = giaTri => giaTri && typeof giaTri === 'object' && !Array.isArray(giaTri)
  ? Object.fromEntries(Object.keys(giaTri).sort().map(k => [k, sapXep(giaTri[k])]))
  : giaTri;
const thoiGian = Date.now();
const chuKy = crypto.createHmac('sha512', khoa)
  .update(thoiGian + '.' + JSON.stringify(sapXep(JSON.parse(noiDung))))
  .digest('hex');

fetch('http://localhost:' + cong + '/api/payment/casso/webhook', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', 'X-Casso-Signature': 't=' + thoiGian + ',v1=' + chuKy },
  body: noiDung
}).then(async phanHoi => {
  console.log('HTTP ' + phanHoi.status + ' ' + await phanHoi.text());
  if (phanHoi.status === 200) {
    console.log('Ứng dụng đã nhận giao dịch ' + id + '. Xem kết quả ở trang quét mã, hoặc ở Quản lý thanh toán > Giao dịch ngân hàng.');
  } else if (phanHoi.status === 401) {
    console.log('Sai chữ ký: khóa trong .env khác khóa ứng dụng đang dùng. Khởi động lại ứng dụng sau khi sửa .env.');
  }
}, loi => {
  console.error('Không gọi được ứng dụng ở cổng ' + cong + ': ' + loi.message);
  process.exit(1);
});
