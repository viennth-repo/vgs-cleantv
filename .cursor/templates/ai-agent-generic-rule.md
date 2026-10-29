# AI Agent Generic Rule — Chuẩn context & cache context

> **Phạm vi:** MỌI AI agent / model (Claude Code, Cursor, Copilot, Gemini CLI, Codex, Aider…) làm việc trong repo này. Các quy tắc là **bắt buộc tuyệt đối** trừ khi user chỉ định khác trong prompt của task.
>
> **Single source of truth:** file rule của từng tool (`CLAUDE.md`, `AGENTS.md`, `GEMINI.md`, `.cursorrules`, `.github/copilot-instructions.md`…) chỉ được **tóm tắt + trỏ về đây**, không copy nguyên nội dung.
>
> **Cập nhật:** 2026-10-29

## 0. Nguyên tắc nền

- Context window là **tài nguyên hữu hạn** — hiệu năng model giảm khi context đầy (context rot). Mục tiêu mỗi task: **tập token nhỏ nhất có tín hiệu cao nhất**.
- **Just-in-time retrieval:** giữ pointer (đường dẫn, index) và chỉ load dữ liệu khi thực sự cần — không load trước "cho chắc".
- Thứ tự ưu tiên nguồn: **cache distilled → doc gốc → source code**. Chỉ đọc nguồn đắt hơn khi nguồn rẻ hơn không đủ.

## 1. Session-start protocol (đúng thứ tự, dừng sớm nhất có thể)

1. `Read` **`.contexts/INDEX.md`** (local-only, gitignored). `Glob`/`Grep` **cấm** dùng để tìm trong `.contexts`. Khớp Keywords §2 → `Read` đúng 1 path. Đủ → **DỪNG**. Không mở `docs/`.
2. Miss → `Read` **`docs/INDEX.md`** thôi. Cấm `ls` / `Grep` cả `docs/`. Khớp §2 → `Read` đúng 1 path. Đủ → **DỪNG**.
3. Miss cả hai → §1 của index vừa đọc, `ls` **một** folder. Vẫn miss → `README.md` / source. Khám phá rộng → subagent.
4. **Cache stale** (fact trong cache ≠ code hiện tại) → sửa file cache + cột `Verified` trên đúng INDEX, rồi tiếp tục task.
5. Task lớn / mơ hồ → **plan trước, implement sau**. Diff mô tả được trong 1 câu → implement thẳng, bỏ qua plan.

## 2. Retrieval & exploration

- Khám phá tốn token (grep diện rộng, đọc nhiều file, research web) → **delegate cho subagent / process riêng** nếu tool hỗ trợ; chỉ nhận về summary ngắn.
- Kết quả trung gian → ghi ra **file** (scratchpad, cache file), không lặp lại nội dung dài trong hội thoại.
- Một hướng điều tra không ra kết quả sau vài lần thử → dừng, đổi hướng ("infinite exploration" là failure pattern).
- Task dài: giữ context utilization ~40–60% — tách phase research / plan / implement; mỗi phase có thể chạy trong context mới với artifact bàn giao (plan file, analysis doc).

## 3. Chuẩn file cache (`.contexts/contexts/<domain>/`)

- Path: `.contexts/contexts/<domain>/<topic>.md` (kebab-case, **không** suffix `-context`). Cross-cutting → `.contexts/contexts/_shared/`. Domain mới chỉ khi ≥ 3 file sẽ sống ở đó.
- Frontmatter bắt buộc: `topic`, `aliases`, `domain`, `verified`, `read_when`. Body **~1 trang**, chỉ fact đã xác minh kèm `path:line`. Dài hơn → tách topic hoặc overflow sang `impl/`.
- File mới hoặc đổi path → **1 hàng** vào `.contexts/INDEX.md` §2 (Keywords | File | Đọc khi | Verified). Spec/plan/issue/audit **không** vào §2 trừ khi đã distill thành cache bền.
- `.contexts/` là **local-only, gitignored** — không commit; mỗi máy/dev tự distill và tái dùng.
- Fact dùng chung cả repo: `docs/contexts/<domain>/<topic>.md` + **1 hàng** `docs/INDEX.md` §2. Cùng frontmatter và độ dài ~1 trang. `docs/INDEX.md` cũng trỏ spec/plan đã commit; không đọc plan khi hàng spec đã đủ.
- **Không** chứa: suy đoán chưa verify, nội dung copy nguyên từ source, thông tin đổi thường xuyên, plan/audit/trace one-off.

## 4. Session-end — distill-back (bắt buộc khi có khám phá mới)

- Học được fact tái dùng (kiến trúc, wiring, quy ước ngầm, gotcha) mà cache chưa có → fact dùng chung repo ghi `docs/contexts/<domain>/` + 1 hàng `docs/INDEX.md` §2; fact chỉ máy này ghi `.contexts/contexts/<domain>/` + 1 hàng `.contexts/INDEX.md` §2.
- Task này sửa code làm cache cũ sai → cập nhật cache + `Verified` trong cùng task.
- **Không** distill: chi tiết chỉ dùng một lần, hoặc thứ đọc được trực tiếp từ code trong <1 phút.

## 5. Chuẩn file rule (CLAUDE.md / AGENTS.md / GEMINI.md / .cursorrules)

- **≤ 200 dòng.** File rule càng dài, tỉ lệ agent tuân thủ càng thấp.
- Litmus test từng dòng: *"Xóa dòng này thì agent có làm sai không?"* — không → xóa.
- Rule phải **cụ thể & verify được**: "chạy `./gradlew test` trước khi commit" — không phải "hãy test kỹ".
- **Không** chứa: điều suy ra được từ code, convention mặc định của ngôn ngữ, mô tả file-by-file, platitude ("write clean code"), **danh sách từng file context** (anti-pattern — dùng INDEX).
- Escalation: rule khuyến nghị → rule file · việc phải xảy ra 100% → hook/script (deterministic) · kiến thức domain thỉnh thoảng dùng → doc riêng + pointer.
- Agent lặp cùng một lỗi 2 lần → thêm 1 rule. Rule mâu thuẫn nhau → agent chọn tùy ý → phải prune định kỳ.

## 6. Prompt-cache hygiene (tool có prefix caching)

**Agent tự kiểm soát:**
- Không re-read file đã có trong context; không lặp nội dung dài trong reply.
- Dùng subagent cho exploration → prefix cache của phiên chính không bị đổi.

**Thuộc về user (agent chỉ khuyến nghị khi liên quan):**
- Prefix xếp layer System → rule file → conversation; đổi 1 byte ở prefix → rebuild toàn bộ phần sau.
- Invalidate cache: đổi model/effort/mode, connect/disconnect MCP, deny nguyên tool → **chốt từ đầu session**.
- Sửa rule file giữa session **không có hiệu lực** cho tới khi reset context hoặc restart session.
- Nén context tại ranh giới task (không giữa task) · quay lui checkpoint để bỏ hướng sai (trúng cache, rẻ hơn nén) · reset hẳn giữa 2 task không liên quan · sửa 2 lần cùng một lỗi không xong → reset + viết prompt tốt hơn.
- Metric: token *cache-read* cao = tốt; token *cache-write* cao liên tục = prefix bị churn mỗi turn.

> Lệnh cụ thể để nén/quay-lui/reset là **tool-specific** → xem file wrapper của tool (Claude Code: `/compact`·`/rewind`·`/clear` — `CLAUDE.md`; Cursor: new Agent chat — `.cursor/rules/session-efficiency.mdc`).

## 7. Cross-agent portability

- File này là chuẩn dùng chung — **không** chứa tính năng riêng của một tool; phần riêng tool (hooks, skills, plan-mode…) đặt trong file wrapper của tool đó.
- Mỗi tool đọc file entry riêng của nó; tất cả chỉ là **pointer mỏng** trỏ về file này:
  - `AGENTS.md` (root) — Cursor, Codex, Copilot, Gemini CLI (chuẩn agents.md).
  - `CLAUDE.md` (root) — Claude Code (+ global `~/.claude/CLAUDE.md`).
  - `GEMINI.md` (root) — Gemini CLI (nếu không dựa vào `AGENTS.md`).
  - `.github/copilot-instructions.md` — GitHub Copilot.
  - `.cursor/rules/*.mdc` (`alwaysApply: true`) — Cursor.

## 8. Checklist trước khi kết thúc task

- [ ] Đã `Read` `.contexts/INDEX.md`, và khi miss thì `docs/INDEX.md`, trước khi đọc source? (nếu không — vì sao?)
- [ ] Fact mới tái dùng đã distill vào `docs/contexts/` hoặc `.contexts/contexts/` + đúng 1 hàng INDEX §2?
- [ ] Cache đã chạm tới có stale không — đã cập nhật `Verified`?
- [ ] Diff nhỏ nhất đúng việc, không sửa dạo?

---

Nguồn: [Claude Code best practices](https://code.claude.com/docs/en/best-practices) · [Memory/CLAUDE.md](https://code.claude.com/docs/en/memory) · [Prompt caching in Claude Code](https://code.claude.com/docs/en/prompt-caching) · [Effective context engineering for AI agents (Anthropic)](https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents) · [Advanced Context Engineering (HumanLayer)](https://www.humanlayer.dev/blog/advanced-context-engineering) · [agents.md (Linux Foundation)](https://agents.md/)
