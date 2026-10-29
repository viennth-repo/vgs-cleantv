# Docs router

> Đọc file này khi `.contexts/INDEX.md` không khớp. Không `ls` / `Grep` cả `docs/`.
> Khớp cột Keywords → `Read` đúng 1 path rồi dừng. Plan dài chỉ mở khi cột Đọc khi nói vậy.

## 1. Bucket map

| Kind | Path | Khi nào |
|---|---|---|
| Distilled cache | `contexts/<domain>/` | fact bền, commit cùng repo |
| Specs | `superpowers/specs/` | quyết định sản phẩm đã chốt |
| Plans | `superpowers/plans/` | đang làm lần lượt từng task trong plan |

Domain: `_shared`, `adb`, `dulieu`, `launcher`, `khoa`, `web`. Domain mới chỉ khi ≥ 3 file sẽ sống ở đó.

## 2. Lookup

Quét cột Keywords. INDEX dài hơn ~1.5 trang → gộp hàng trùng, cắt hàng stale. Một task một file.

### khoa

| Keywords | File | Đọc khi | Verified |
|---|---|---|---|
| mode trẻ em, mode phụ huynh, treBat, treMode, appChoPhep, phutToan, quiz YouTube | superpowers/specs/2026-10-28-mode-tre-em-design.md | Cần quyết định đã chốt. Không mở plan. | 2026-10-28 |
| plan mode trẻ em, task triển khai | superpowers/plans/2026-10-28-mode-tre-em.md | Đang làm hoặc đối chiếu một task trong plan. | 2026-10-28 |
