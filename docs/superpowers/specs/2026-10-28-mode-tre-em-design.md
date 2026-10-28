# Mode trẻ em và mode phụ huynh

Ngày: 2026-10-28. Trạng thái: thiết kế đã chốt trong hội thoại, chờ duyệt file này trước khi lập kế hoạch triển khai.

## Mục tiêu

Tivi có hai mode:

- Mode phụ huynh: Home sạch như hiện tại, không bài toán.
- Mode trẻ em: Home chỉ hiện app được phép, YouTube luôn có. Khi app YouTube đang ở trước màn hình, hết số phút phụ huynh đặt thì hiện một bài cộng hoặc trừ. Chọn đúng mới được xem tiếp.

Đối tượng bài toán: lớp 1–2 ở Việt Nam.

## Đã chốt

- Hướng triển khai: `:khoa` giữ cấu hình và phủ bài toán. Home sạch chỉ lọc icon. App điện thoại đọc và ghi cùng cấu hình qua ADB.
- Chuyển mode được cả trên tivi (PIN 4 số) và từ app CleanTV trên điện thoại.
- Mode phụ huynh giữ đến khi phụ huynh chủ động về mode trẻ em. Không tự hết giờ.
- Cấu hình (danh sách app, số phút, mức toán) sửa được trên tivi khi đang ở mode phụ huynh, và trên điện thoại. Một bản trên tivi.
- Bài toán dừng hình và tiếng. Ba đáp án. Chọn sai thì đổi bài mới. Vẫn phải đúng một bài mới xem tiếp.
- Mức toán do phụ huynh chọn: trong 10, trong 20, hoặc trong 100 có nhớ.

## Ngoài phạm vi

- Không gộp và không đổi Khoá trẻ em theo khung giờ.
- Không tắt gói nào để làm mode trẻ em.
- Không chặn nút app trên remote, Cài đặt hệ thống, âm lượng, nguồn, Chromecast, hay tìm bằng giọng nói.
- Không hiện bài toán khi đang truyền bằng Chromecast.
- Không ẩn đồng hồ, thời tiết, hay hàng kênh trên Home.

## Kiến trúc

Ba mảnh, một cấu hình nằm trên tivi trong `:khoa`, cạnh cấu hình khoá theo giờ, khác trường dữ liệu.

- `:khoa` lưu mode, PIN, danh sách app, số phút, mức toán, số phút đã xem. Dịch vụ trợ năng nhận lúc YouTube lên trước, đếm giờ, dừng phát, phủ bài toán. Chỉ nuốt phím khi bài đang hiện.
- Home sạch (`com.gathin.tvhome` và bản Xiaomi `com.ts.launcher`) đọc mode và danh sách app, rồi lọc lưới, dock và thư viện app. Không đọc PIN, không ghi cấu hình.
- App CleanTV trên điện thoại ghi cấu hình qua content provider hiện có (`content://zone.vgs.cleantv.khoa`), cùng đường với khoá theo giờ. Điện thoại là chỗ của phụ huynh, nên đổi mode không hỏi lại PIN. Trên tivi thì phải nhập PIN.

Màn cài đặt trên tivi là activity của `:khoa`. Home có mục **Phụ huynh** mở activity đó. Mode phụ huynh không phải một Home thứ hai: vẫn là Home sạch, bỏ lọc app, tắt bài toán.

Khoá theo khung giờ giữ luật riêng. Đang trong khung khoá thì lớp phủ khoá che hết, bài toán không hiện. Hết khung hoặc đang mở tạm bằng PIN của khoá giờ thì mode trẻ em vẫn áp dụng nếu tính năng này đang bật. Nhập PIN mở khoá giờ không sang mode phụ huynh.

Cài Home sạch không tắt `com.google.android.apps.mediashell`. Mode trẻ em cũng không tắt gói này. Chromecast từ máy tính qua mạng nhà vẫn dùng được.

## Cấu hình và chuyển mode

Trường mới, không dùng lại cờ `bat` của khoá theo giờ:

- `treBat`: tính năng đang bật hay không.
- `treMode`: `tre` hoặc `phuHuynh`. Chỉ có nghĩa khi `treBat` đúng.
- PIN 4 số: dùng chung `muoi` và `pinBam` với khoá theo giờ. Một mã cho cả hai. Chưa có mã thì màn Phụ huynh trên tivi đặt mã hai lần.
- `appChoPhep`: danh sách package. YouTube không bỏ được. Hai package `com.google.android.youtube.tv` và `com.google.android.youtube` đều tính là YouTube khi package đó đang cài. Khi đọc, package nào đang cài mà thiếu trong danh sách thì thêm lại.
- `phutToan`: số nguyên từ 5 đến 120. Mặc định 30.
- `mucToan`: `10`, `20` hoặc `100`. Mặc định `10`.

Mặc định khi chưa bật: không lọc Home, không bài toán. Không bật được khi chưa có PIN. Bật tính năng thì vào mode trẻ em ngay, danh sách chỉ YouTube, 30 phút, mức 10, trừ khi phụ huynh đã sửa các mục đó trước lúc bật.

Tắt tính năng chỉ từ mode phụ huynh (tivi sau PIN, hoặc điện thoại). Tivi trở lại Home không lọc và không bài toán. PIN giữ nguyên.

Mục **Phụ huynh** luôn có trên Home. Đang mode trẻ em thì hỏi PIN rồi mới vào mode phụ huynh và mở cài đặt. Đang mode phụ huynh thì mở thẳng cài đặt. Nút Back đóng màn cài đặt, không đổi mode. Về mode trẻ em chỉ bằng nút **Về mode trẻ em** trên tivi hoặc trên điện thoại.

PIN sai: 5 lần thì chờ 60 giây, đúng luật khoá hiện có.

Home được phép gọi hàm đọc chỉ gồm `treBat`, `treMode` và `appChoPhep`. Ghi cấu hình vẫn chỉ từ ADB (shell) hoặc từ chính `:khoa`. Khi mode hoặc danh sách đổi, `:khoa` báo Home vẽ lại.

Danh sách app để chọn là app có biểu tượng trên tivi. Ô YouTube luôn chọn và không bỏ.

## Home ở hai mode

Mode phụ huynh: Home sạch như hôm nay, kể cả các app phụ huynh đã giấu trong Nâng cao (`hidden_apps`). Không bài toán.

Mode trẻ em:

- Lưới app, dock và thư viện mọi app chỉ còn package nằm trong `appChoPhep`. YouTube luôn còn.
- Đồng hồ, thời tiết, hàng kênh giữ nguyên.
- Không gọi `pm disable`. Nút app trên remote vẫn mở app đó. Cài đặt hệ thống vẫn mở. Âm lượng, nguồn, nút Home, giọng nói và Chromecast giữ nguyên.
- Cùng luật lọc cho `com.gathin.tvhome` và `com.ts.launcher`.

## Bài toán trên YouTube

Chỉ khi `treBat` và `treMode` là `tre`, và một trong hai package YouTube ở trên đang ở trước màn hình, gồm cả menu YouTube. Rời YouTube thì ngừng đếm, quay lại thì đếm tiếp. Trả lời đúng thì bộ đếm về 0. Bài hiện khi số giây đã xem đạt `phutToan` × 60.

Đếm theo thời gian app YouTube ở trước, không theo đồng hồ tường. Giá trị đã xem ghi trong `:khoa` mỗi nhịp dịch vụ (15 giây) và khi rời YouTube. Tắt nguồn hoặc khởi động lại không đưa bộ đếm về 0. Mất tối đa một nhịp 15 giây nếu tiến trình chết giữa hai lần ghi.

Đủ `phutToan` thì ghi cờ `dangChoBai`, gửi lệnh tạm dừng, phủ bài toán. Lớp phủ có phép tính và 3 đáp án. Đúng một đáp án đúng. Vị trí đáp án đúng được xáo. Hai đáp án sai khác đáp án đúng, khác nhau, và nằm trong phạm vi của mức. Không hiện đáp án đúng khi chọn sai.

Chọn bằng trái, phải và OK. Chọn đúng thì xoá `dangChoBai`, đóng lớp phủ, gửi lệnh phát, bộ đếm về 0. Chọn sai thì đổi một phép khác ngay, không lặp lại đúng phép vừa sai.

Trong lúc bài hiện: nuốt Back, Home, giọng nói và nút app trên remote, để không bỏ qua bài. Âm lượng và nguồn vẫn dùng được. Ngoài lúc bài hiện, nút Home và nút app trên remote hoạt động bình thường.

Đổi số phút hoặc mức toán áp dụng cho lần sau. Bài đang hiện thì làm nốt bài đó.

Sang mode phụ huynh hoặc tắt tính năng trong lúc bài đang hiện: đóng bài, xoá `dangChoBai`, gửi lệnh phát.

Ba mức, chỉ số nguyên, không ra số âm:

- **10.** Số hạng và kết quả trong 0–10. Phép cộng không vượt 10, nên không có nhớ.
- **20.** Số hạng và kết quả trong 0–20. Bộ sinh tạo được cả bài qua 10 (ví dụ 8 + 5, 14 − 6) và bài không qua 10. Không bắt mọi bài phải qua 10.
- **100.** Số hạng và kết quả trong 0–100. Bộ sinh tạo được bài có nhớ hoặc mượn (ví dụ 27 + 15, 40 − 16). Không bắt mọi bài phải có nhớ.

Khởi động lại giữa chừng mà `dangChoBai` còn: lần YouTube lên trước sau đó hiện bài mới ngay, hình vẫn dừng đến khi chọn đúng. Không tự phát tiếp.

## Khi có lỗi

Lệnh tạm dừng không dừng YouTube thì vẫn phủ kín hình và tắt tiếng. Chọn đúng thì bật tiếng lại và gửi lệnh phát.

Dịch vụ trợ năng tắt hoặc chưa cài `:khoa`: app điện thoại báo mode trẻ em không hiện được bài toán. Home vẫn lọc nếu đọc được cấu hình.

JSON cấu hình không đọc được: coi `treBat` là tắt. Không kẹt ở mode trẻ em vì file hỏng.

Chromecast (`com.google.android.apps.mediashell`) không tính là YouTube đang mở. Truyền từ máy tính không bị chặn và không hiện bài toán. Giọng nói mở đúng app YouTube thì đồng hồ chạy.

## Kiểm tra

Luật thuần, test JVM, không cần tivi, cùng kiểu `LuatTest`:

- Mức 10: mọi số trong 0–10, phép cộng có tổng không quá 10, phép trừ không âm.
- Mức 20: mọi số trong 0–20, và có cách sinh ra bài qua 10.
- Mức 100: mọi số trong 0–100, và có cách sinh ra bài có nhớ hoặc mượn.
- Đúng một đáp án đúng. Hai đáp án sai khác nhau, khác đáp án đúng, nằm trong phạm vi mức.
- Chọn sai ra phép khác phép vừa sai.
- Đồng hồ chỉ tăng khi YouTube ở trước. Rời app thì không tăng. Đúng một bài thì về 0. Đọc lại bộ nhớ vẫn còn số đã xem.
- `treBat` tắt hoặc chưa có PIN: không lọc, không bài. Bật thì `treMode` là `tre`. Mode phụ huynh giữ đến khi có lệnh về mode trẻ em. JSON hỏng thì `treBat` tắt. Danh sách thiếu YouTube thì khi đọc có lại YouTube.

Trên tivi, làm tay một lượt: vào mode phụ huynh bằng PIN, Home chỉ còn app được phép khi về mode trẻ em, YouTube đủ phút, chọn sai rồi chọn đúng, giọng nói vẫn mở YouTube, Chromecast từ máy tính không bị chặn.

## Tiêu chí xong

- Phụ huynh bật tính năng, đặt PIN, chọn mức và số phút từ điện thoại hoặc từ tivi.
- Mode trẻ em chỉ còn app được phép trên lưới, dock và thư viện. YouTube còn.
- Đúng số phút YouTube ở trước thì bài hiện, hình và tiếng dừng, chọn đúng mới xem tiếp, chọn sai thì bài khác.
- Mode phụ huynh bỏ lọc và không bài, cho đến khi bấm về mode trẻ em.
- Tắt nguồn không xoá số phút đã xem.
- Khoá theo khung giờ vẫn đúng luật cũ.
