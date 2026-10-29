# AGENTS.md

This file provides guidance to Cursor Agent (and other agents.md-compatible tools) when working with code in this repository.

Project: **CleanTV** (`vgs-cleantv`, Gradle root `tv-sach`) — app Android (Kotlin + Jetpack Compose) dọn quảng cáo / theo dõi / app rác trên Android TV và Google TV qua ADB Wi-Fi, rồi thay Home bằng launcher sạch. Không root, chỉ tắt gói (không gỡ). Module `:app` (`applicationId` `zone.vgs.cleantv`, namespace và mã `zone.vgs.tvsach`) và `:khoa` (khoá trẻ em cài lên tivi, `zone.vgs.khoa`). Định danh trong code tiếng Việt.

---

## Context protocol — ĐỌC TRƯỚC (bắt buộc)

Chuẩn đầy đủ cho mọi AI agent: **[`.cursor/templates/ai-agent-generic-rule.md`](.cursor/templates/ai-agent-generic-rule.md)**. Tóm tắt bắt buộc:

**Session-start (đúng thứ tự, dừng sớm nhất có thể):**
1. `Read` **`.contexts/INDEX.md`** (gitignore — `Glob`/`Grep` không thấy). Khớp Keywords §2 → `Read` đúng 1 file. Đủ → **dừng**. Không mở `docs/`.
2. Miss → `Read` **`docs/INDEX.md`** thôi. Cấm `ls` / `Grep` cả `docs/`. Khớp §2 → `Read` đúng 1 file. Đủ → **dừng**.
3. Miss cả hai → §1 của index vừa đọc, `ls` **một** folder. Vẫn miss → `README.md` / source. Khám phá rộng → **Task tool (subagent)**.
4. **Stale** → sửa file cache + cột `Verified` trên đúng INDEX.

**Session-end:** fact dùng chung repo → `docs/contexts/<domain>/<topic>.md` + **1 hàng** `docs/INDEX.md` §2. Fact chỉ máy này → `.contexts/contexts/<domain>/<topic>.md` (cross-cutting → `_shared/`) + **1 hàng** `.contexts/INDEX.md` §2. Cùng template `.cursor/templates/context-file-template.md`. Spec/plan dài không nhét vào §2; INDEX chỉ ghi khi nào được mở.

**Prompt-cache hygiene:** không re-read file đã có trong context; kết quả trung gian ghi ra file thay vì lặp trong hội thoại. Chi tiết cơ chế prefix-cache: generic rule §6.

Router: `.contexts/INDEX.md` rồi `docs/INDEX.md`. Mô tả sản phẩm và cách dựng: `README.md`.

---

## Cursor session hygiene (tool-specific)

Phần generic (prefix layer, metric cache-read/cache-write): generic rule §6. Riêng Cursor:

- Cursor xếp context theo layer: **System prompt → AGENTS.md + rules + MCP → Conversation**; đổi 1 byte ở prefix → rebuild toàn bộ phần sau.
- **Chat mới tại ranh giới task**, không giữa task; 2 task không liên quan → chat mới (đừng nhồi tiếp).
- Sửa `AGENTS.md` / `.cursor/rules/` **không có hiệu lực giữa session** (đọc 1 lần lúc start) → **mở Agent chat mới**. Rule có `globs` chỉ load khi file khớp được mở/làm việc.
- Không đổi model/mode, không bật/tắt MCP server giữa task — mỗi cái rebuild toàn bộ cache; **chốt từ đầu session**.
- Khám phá tốn token (grep diện rộng, đọc nhiều file) → **Task tool (subagent)**: context riêng, chỉ trả summary về parent.

Enforced by rule: `.cursor/rules/session-efficiency.mdc`, `.cursor/rules/context-first.mdc`

---

## Generic conventions

- **Match existing code** — theo style/idiom của file đang sửa; không convert/refactor code không liên quan.
- **Minimal scope** — diff nhỏ nhất đúng việc; không sửa dạo (drive-by).
- **Context before code** — đọc cache/docs trước khi khám phá sâu (Context protocol ở đầu file).

Enforced by rule: `.cursor/rules/coding-conventions.mdc`, `.cursor/rules/context-first.mdc`

### Comment format — logic non-UI

Khi **generate hoặc sửa** code non-UI (domain/use-case, repository/impl, mapper, data source, service, adapter, API client) → thêm docblock theo ngôn ngữ (KDoc/JSDoc/docstring) **chỉ trên** function/interface/class public (và helper private nếu logic phức tạp) + `const`/config. **Không** comment inline trong thân hàm, **không** comment từng field trong data class/interface/type.

UI của repo này: `@Composable` trong `app/src/main/java/zone/vgs/tvsach/ui/` (`UngDung.kt`, `PhienLamViec.kt`) và `ManHinh.kt`. Bỏ qua docblock 4 mục cho các file đó.

Kotlin (`adb/`, `dulieu/`, `khoa/`) giữ KDoc ngắn tiếng Việt như code hiện có. Rule `vietnamese-docblock.mdc` chỉ glob `*.{ts,js,py,go,rs}` — không ép khối 4 mục lên `*.kt`.

Tiếng Việt, đúng 4 mục (bỏ mục không áp dụng) — áp dụng cho file khớp glob của rule:

```ts
/**
 * Mục đích: <tại sao tồn tại / business intent>
 * Input:   <tham số, shape, nguồn dữ liệu>
 * Output:  <giá trị trả về, side effect, event emit>
 * Rules:   <invariant, edge case, hợp đồng với caller/downstream>
 */
```

Enforced by rule: `.cursor/rules/vietnamese-docblock.mdc`

---

## Repo-local AI assets (optional)

- `.cursor/rules/` — project rules (always-apply + file-scoped) · `.cursor/commands/` — slash commands (user-facing) · `.cursor/skills/` — orchestration skills (building block nội bộ) · `.cursor/agents/` — sub-agent definitions (planner, implementer, verifier, …)
- `.cursor/templates/` — template tái sử dụng (file này, generic rule, context file, `contexts-INDEX.md`)
- Lệnh user-facing: `/plan`, `/plan-strict`, `/review-code`, `/review-sync`, `/figma-ui`, `/hunt-issues`, `/auto-feature`. Chi tiết: `.cursor/commands/README.md`.

---

## Commands

JDK 17 · Android Gradle Plugin 8.7.2 · Kotlin 2.0.21 · Gradle 8.9 · `compileSdk`/`targetSdk` 35 · `minSdk` 26. Cần `local.properties` (Android SDK, gitignored). `ky.properties` + keystore (gitignored): thiếu thì `assembleRelease` ký bằng khoá debug.

```bash
scripts/dung-launcher.sh                 # tải CleanYourTV, áp patches/launcher-vgs.patch, dựng Home sạch + dữ liệu mẫu vào assets
scripts/dung-khoa.sh                     # dựng APK khoá trẻ em (:khoa) để app đóng vào assets
./gradlew assembleRelease                # APK release (:app). Assets tvhome.apk và du-lieu.json đã có sẵn — chỉ dựng app thì bỏ bước launcher
./gradlew test                           # unit test mọi module
./gradlew :app:testReleaseUnitTest       # unit test :app
./gradlew :khoa:testReleaseUnitTest      # unit test :khoa
./gradlew lint                           # Android lint
scripts/so-ban-dung.sh CleanTV-x.y.z.apk # dựng lại từ tag rồi so từng file trong APK (bỏ chữ ký)
```

Thiếu SDK: tạo `local.properties` với `sdk.dir`. Bản release công khai phải tái tạo được: giữ `dependenciesInfo.includeInApk = false` và `vcsInfo.include = false` trong `app/build.gradle.kts` và `khoa/build.gradle.kts`. Gỡ lỗi sâu: `README.md` (mục Dựng từ mã nguồn, Kiểm tra bản tải về).

## Big-picture architecture

Hai application module Gradle, không phụ thuộc nguồn lẫn nhau. `:app` chạy trên điện thoại (hoặc trên tivi); `:khoa` là APK riêng cài lên tivi khi bật khoá trẻ em. Launcher Home sạch không nằm trong source: upstream CleanYourTV + `patches/launcher-vgs.patch`, script `scripts/dung-launcher.sh` nhét APK vào `app/src/main/assets/`.

Luồng một phiên dọn: `MainActivity` → Compose `ui/UngDung` / `ManHinh` → `ui/PhienLamViec` → `adb/KetNoiAdb` (libadb-android, Wi-Fi) → `adb/ThaoTacTv` (sao lưu, tắt gói, cài Home, thử nút Home, khôi phục). Kế hoạch gói: `dulieu/KeHoach` đọc danh sách mẫu `dulieu/DuLieu` (`assets/du-lieu.json`, ~341 mẫu). Map chi tiết khi đã distill: `.contexts/INDEX.md` hoặc `docs/INDEX.md`.

| Kênh | Khi dùng |
|---|---|
| `ui/` → `adb/` + `dulieu/` | Mọi thao tác tivi và luật chọn gói |
| `scripts/dung-khoa.sh` → assets → cài bằng ADB | Đưa `:khoa` lên tivi. Không `implementation(project(":khoa"))` |
| `patches/` + `scripts/dung-launcher.sh` | Sửa Home sạch. Không viết launcher vào `app/src` |

Đúng: tắt gói qua `ThaoTacTv`, backup trước (`dulieu/SaoLuu`). Sai: `pm uninstall` gói hệ thống, hoặc đổi namespace `zone.vgs.tvsach` cho khớp `applicationId` `zone.vgs.cleantv`.

### Trạng thái hiện tại / gotchas

- Chỉ **tắt**, không gỡ. Không đụng HDMI, remote, bàn phím, CH Play, gói giữ Wi-Fi, gói lõi Android, Home đang dùng. Google Home / cast và tìm kiếm giọng nói không bao giờ tích sẵn.
- Đang thay Home mà mất kết nối: tivi tự bật lại Home gốc sau 3 phút.
- Xiaomi bản Trung Quốc dùng APK Home tên khác (`tvhome-xiaomi.apk`). Google TV còn trình cài đặt, TCL, hộp nhà mạng: không tắt được Home gốc — Home sạch mở đè.
- `app/.../assets/upstream-commit.txt` ghi commit CleanYourTV đã vá. Sửa launcher thì vá `patches/launcher-vgs.patch` rồi dựng lại, không sửa APK tay.

## Testing

Unit test JUnit trên JVM, không cần thiết bị hay emulator (`testOptions.unitTests.isReturnDefaultValues = true`). Ưu tiên luật gói và kịch bản khôi phục: `KeHoachTest`, `GoiLoiTest`, `NenGiuTest`, `KichBanKhoiPhucTest`, `NangCaoTest`, `XaPhuongTest`, `MauGanGiongTest`, `WifiChuTest`, `khoa` `LuatTest`.

Một lớp: `./gradlew :app:testReleaseUnitTest --tests zone.vgs.tvsach.dulieu.KeHoachTest`

## Project conventions

- Tên hàm, biến, file Kotlin tiếng Việt không dấu (`KeHoach`, `ThaoTacTv`). Chuỗi UI đi qua `NgonNgu.kt` (`t(vi, en)`), song ngữ Việt / Anh.
- `applicationId` (`zone.vgs.cleantv`) khác namespace/package (`zone.vgs.tvsach`). Giữ tách biệt.
- Đếm ẩn danh: `Dem.kt` và worker `dem/`. Nút gửi danh sách gói chỉ chạy khi người dùng bấm. Không thêm telemetry danh sách app, IP, vị trí.
