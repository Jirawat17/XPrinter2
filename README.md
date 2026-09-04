# XPrinterBT – In nhãn qua Bluetooth cho Xprinter XP-428A

Ứng dụng Android (Kotlin) kết nối và in **nhãn (label)** trên máy in nhiệt
**Xprinter XP-428A_UB** qua **Bluetooth (Classic SPP)**, dùng lệnh ESC/POS.

App chỉ hỗ trợ Bluetooth (không hỗ trợ USB) — đây là cách kết nối phổ biến
và tiện nhất trên Android, không cần cáp OTG hay phần cứng USB Host.

Mặc định in nhãn khổ **100mm x 150mm** (chỉnh được trực tiếp trong app), ở
độ phân giải **203 DPI** (chuẩn phổ biến của Xprinter).

## Cấu trúc dự án

```
XPrinterBT/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/xprinterapp/
│       │   ├── MainActivity.kt            # Giao diện + điều phối kết nối/in
│       │   ├── BluetoothPrinterHelper.kt   # Kết nối Bluetooth Classic (SPP)
│       │   └── EscPosCommands.kt           # Sinh lệnh ESC/POS (nhãn dạng ảnh)
│       └── res/...
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## 📦 Cách lấy file .apk để cài thử trên điện thoại

Việc build file `.apk` cần Android SDK (vài GB) nên không thể build trực
tiếp trong môi trường trò chuyện. Dự án đã có sẵn **GitHub Actions** để tự
động build APK miễn phí trên máy chủ GitHub, không cần cài Android Studio:

1. Giải nén file zip vừa tải về.
2. Tạo repository mới (miễn phí) trên https://github.com (**New repository**).
3. Trên trang repository → **Add file → Upload files** → kéo thả toàn bộ
   nội dung đã giải nén vào → **Commit changes**.
4. Vào tab **Actions** → workflow **"Build APK"** tự chạy (nếu chưa, bấm
   **Run workflow**).
5. Đợi khoảng 2–4 phút tới khi có dấu ✔ xanh.
6. Bấm vào lượt chạy đó → mục **Artifacts** → tải file
   **`XPrinterBT-debug-apk.zip`** → giải nén ra được **`app-debug.apk`**.
7. Chuyển file `.apk` vào điện thoại (Google Drive, Zalo, cáp USB dữ
   liệu...) và mở để cài. Nếu Android chặn "cài từ nguồn không xác định",
   vào **Cài đặt** cho phép ứng dụng bạn dùng để mở file (Files, Chrome,
   Zalo...) được cài app.

Nếu có sẵn Android Studio: mở project → **Build → Build Bundle(s)/APK(s)
→ Build APK(s)**, file nằm ở `app/build/outputs/apk/debug/app-debug.apk`.

## Cách dùng

1. Bật máy in, vào **Cài đặt Bluetooth của Android**, ghép nối (pair) với
   máy in trước (Xprinter thường hiện tên dạng `XP-...` hoặc `Printer...`).
   Mật khẩu ghép nối mặc định thường là `0000` hoặc `1234` nếu được hỏi.
2. Mở app **XPrinterBT** → bấm **"Danh sách đã ghép nối"** để tải danh
   sách thiết bị đã pair.
3. Chọn máy in trong danh sách → bấm **"Kết nối"**.
4. Khi trạng thái hiển thị màu xanh "Đã kết nối", nhập nội dung → bấm
   **"IN NHÃN"**.
5. Dùng nút **"Ngắt kết nối"** khi muốn chủ động đóng kết nối; nút
   **"Xuống dòng + Cắt giấy"** để đẩy giấy và cắt thủ công.

## Về kích thước nhãn và tiếng Việt có dấu

- App luôn in nội dung dưới dạng **ảnh bitmap** (`GS v 0`, chia theo từng
  dải nhỏ), vẽ đúng kích thước thật của nhãn (quy đổi mm → dot theo DPI),
  đảm bảo:
  - Hiển thị đúng 100% dấu tiếng Việt (không phụ thuộc bảng mã máy in).
  - Nhãn in ra đúng tỉ lệ khổ đã nhập, không bị co giãn/lệch.
  - In ổn định trên máy in có bộ đệm nhỏ (ảnh lớn được chia thành nhiều
    dải nhỏ thay vì gửi một lệnh khổng lồ).
- 3 ô nhập trực tiếp trong app:
  - **Rộng (mm)** / **Cao (mm)**: kích thước nhãn thật, mặc định `100` x `150`.
  - **DPI**: độ phân giải máy in, mặc định `203` (chuẩn Xprinter phổ biến,
    ~8 dot/mm). Nếu nhãn in ra sai tỉ lệ, thử đổi sang `180` hoặc `300`.
- Checkbox **"Tự động cắt giấy sau khi in nhãn"**: bật nếu máy có dao cắt.

## Xử lý sự cố thường gặp

| Vấn đề | Nguyên nhân thường gặp |
|---|---|
| Không thấy máy in trong danh sách đã ghép nối | Chưa pair trong Cài đặt hệ thống, hoặc máy in chưa bật |
| Kết nối báo lỗi liên tục | Máy in đang kết nối với thiết bị khác, hoặc ngoài tầm sóng |
| Đang in dở báo "Mất kết nối tới máy in" | Máy in tắt nguồn/hết pin giữa chừng, hoặc ra ngoài tầm sóng Bluetooth — kết nối lại rồi in lại |
| Nhãn in ra sai tỉ lệ (to/nhỏ hơn khổ thật) | Sai giá trị DPI — thử đổi 180/203/300 tuỳ máy |
| Chữ tiếng Việt bị mất dấu/lỗi ký tự | Không nên xảy ra vì app luôn in dạng ảnh; nếu vẫn lỗi, kiểm tra lại font hệ thống trên máy Android |
| App báo "Kích thước nhãn/DPI quá lớn" | Giảm DPI hoặc kích thước nhãn — giới hạn đặt ra để tránh crash do hết bộ nhớ |

## 🎨 Giao diện

Giao diện lấy tinh thần từ **fingerprint.to** — nền tối gần đen (`#171717`),
bố cục dạng thẻ (card) viền mảnh thay vì đổ bóng, nhãn trạng thái dạng
"badge" bo tròn (xanh lá khi thành công, đỏ khi lỗi, xanh dương khi đang xử
lý), nút hành động chính nổi bật màu xanh lá. Đây là tham khảo về **màu sắc
và ngôn ngữ thiết kế** — không sao chép logo, nội dung hay bố cục cụ thể của
trang đó.

- 3 thẻ (card) tách biệt: **Kết nối máy in**, **Kích thước nhãn**, **Nội
  dung in** — dễ quét mắt hơn so với bản liệt kê phẳng trước đây.
- Trạng thái kết nối hiển thị dạng badge màu (● Đã kết nối / ● Đang kết
  nối... / ● Lỗi), thay cho dòng chữ đơn sắc.
- Ô chọn thiết bị Bluetooth dùng dropdown kiểu Material (Exposed Dropdown
  Menu) thay cho Spinner cũ.
- Công tắc "Tự động cắt giấy" dùng Switch thay cho Checkbox.
- Thanh tiến trình mảnh (Material Linear Progress) màu xanh lá accent.

## 🔧 Ghi chú các lần rà soát & sửa lỗi

- **Gỡ toàn bộ chức năng USB**, chỉ giữ Bluetooth — phù hợp hơn với đa số
  điện thoại Android (không cần OTG/USB Host), giảm độ phức tạp code.
- Đổi tên ứng dụng thành **XPrinterBT**.
- Sửa crash tiềm ẩn: xin quyền Bluetooth trước khi đọc danh sách thiết bị
  đã ghép nối khi mở app lần đầu (có thể crash trên Android 12+).
- Sửa treo giao diện (ANR): dựng ảnh nhãn và chuyển sang lệnh in chạy hoàn
  toàn ở luồng nền, có thanh tiến trình.
- Tối ưu tốc độ xử lý ảnh: `getPixels()` hàng loạt thay vì `getPixel()`
  từng điểm.
- Chống tràn bộ đệm máy in: ảnh nhãn tự động chia thành nhiều dải nhỏ.
- Chống rò rỉ tài nguyên: tự ngắt kết nối cũ trước khi kết nối mới.
- Đơn giản hoá quản lý trạng thái kết nối (dùng `connectedDevice` +
  `bluetoothHelper.isConnected` làm nguồn sự thật duy nhất) — tự động dọn
  trạng thái và báo rõ khi mất kết nối giữa chừng lúc đang in.
- Giới hạn an toàn kích thước nhãn/DPI để tránh crash do hết bộ nhớ.
- Chặn bấm nút liên tục khi đang kết nối/in.
