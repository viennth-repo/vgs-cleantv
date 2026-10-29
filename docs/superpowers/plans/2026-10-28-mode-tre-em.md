# Mode trẻ em Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Thêm mode trẻ em và mode phụ huynh: Home chỉ hiện app được phép (YouTube luôn có), và khi YouTube ở trước màn hình thì hết số phút phải làm một bài cộng hoặc trừ.

**Architecture:** Luật thuần và cấu hình nằm trong `:khoa`. Dịch vụ trợ năng đếm giờ và phủ bài. Home sạch (`com.gathin.tvhome` và `com.ts.launcher`, cùng một bản vá) chỉ đọc mode để lọc lưới, dock và thư viện. App điện thoại ghi cùng JSON qua ADB. Khoá theo khung giờ không đổi luật.

**Tech Stack:** Kotlin, JUnit trên JVM, Android accessibility overlay, content provider, Java launcher CleanYourTV tại commit `fa1a2f00f2b7b12c83b3bc539d91f32f4b1d4dd8`, Jetpack Compose trên app điện thoại.

## Global Constraints

- Không gộp và không đổi Khoá trẻ em theo khung giờ.
- Không gọi `pm disable` cho mode trẻ em. Không tắt `com.google.android.apps.mediashell`.
- Ngoài lúc bài toán hiện: không nuốt nút app trên remote, Cài đặt, âm lượng, nguồn, nút Home, giọng nói.
- Trong lúc bài hiện: nuốt Back, Home, giọng nói và nút app. Âm lượng và nguồn vẫn trả về `false` trong `onKeyEvent`.
- Chromecast không tính là YouTube. Hàng kênh (`LinkTiles`) không bị lọc. Đồng hồ và thời tiết không đụng.
- `treBat` tách khỏi `bat` của khoá giờ. Không bật `treBat` khi chưa có PIN. Bật thì `treMode` thành `tre`.
- `phutToan` là số nguyên 5–120, mặc định 30. `mucToan` chỉ `10`, `20`, `100`, mặc định `10`.
- Package YouTube: `com.google.android.youtube.tv` và `com.google.android.youtube`. Package nào đang cài mà thiếu trong danh sách thì thêm lại khi đọc.
- Một PIN (`muoi` + `pinBam`). Sai 5 lần thì chờ 60 giây.
- JSON không đọc được thì `treBat` tắt.
- Bộ đếm ghi mỗi nhịp 15 giây. Tắt nguồn không về 0.
- Tên hàm Kotlin tiếng Việt không dấu. Chuỗi UI đi qua `t(vi, en)` trên app điện thoại. KDoc ngắn tiếng Việt trên hàm public của `:khoa`.

---

### Task 1: Luật mode, bài toán và đồng hồ

**Files:**
- Create: `khoa/src/main/java/zone/vgs/khoa/LuatTre.kt`
- Test: `khoa/src/test/java/zone/vgs/khoa/LuatTreTest.kt`

**Interfaces:**
- Consumes: không
- Produces: `LuatTre.CheDo`, `LuatTre.Bai`, `LuatTre.DongHo`, `LuatTre.doc`, `LuatTre.hienThi`, `LuatTre.bat`, `LuatTre.tat`, `LuatTre.sangPhuHuynh`, `LuatTre.veTre`, `LuatTre.locDuoc`, `LuatTre.sinh`, `LuatTre.sinhLoai`, `LuatTre.cong`, `LuatTre.denLuc`, `LuatTre.moBai`, `LuatTre.dungBai`, `LuatTre.docDongHo`, `LuatTre.ghiDongHo`, `LuatTre.gopJson`, `LuatTre.ghiTre`, `LuatTre.YOUTUBE`

- [ ] **Step 1: Write the failing test**

Tạo `khoa/src/test/java/zone/vgs/khoa/LuatTreTest.kt`:

```kotlin
package zone.vgs.khoa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LuatTreTest {
    private val yt = "com.google.android.youtube.tv"

    private fun trongMuc(b: LuatTre.Bai, max: Int) {
        assertTrue(b.a in 0..max); assertTrue(b.b in 0..max); assertTrue(b.dung in 0..max)
        assertEquals(if (b.op == '+') b.a + b.b else b.a - b.b, b.dung)
        assertTrue(b.dung >= 0)
        assertEquals(2, b.sai.toSet().size)
        assertFalse(b.dung in b.sai)
        b.sai.forEach { assertTrue(it in 0..max) }
        assertEquals(3, b.dapAn(1L).toSet().size)
        assertTrue(b.dung in b.dapAn(1L))
    }

    @Test fun muc10_khongVuot10() {
        repeat(30) { s ->
            val b = LuatTre.sinh(10, s.toLong(), null)
            trongMuc(b, 10)
            if (b.op == '+') assertTrue(b.dung <= 10)
        }
    }

    @Test fun muc20_coBaiQua10_vaTrongPhamVi() {
        val qua = LuatTre.sinhLoai(20, "qua10", 1L)
        trongMuc(qua, 20)
        assertTrue(LuatTre.qua10(qua))
        repeat(20) { s -> trongMuc(LuatTre.sinh(20, s.toLong(), null), 20) }
    }

    @Test fun muc100_coNho_vaTrongPhamVi() {
        val nho = LuatTre.sinhLoai(100, "nho", 1L)
        trongMuc(nho, 100)
        assertTrue(LuatTre.coNho(nho))
        repeat(20) { s -> trongMuc(LuatTre.sinh(100, s.toLong(), null), 100) }
    }

    @Test fun chonSai_raPhepKhac() {
        val cu = LuatTre.sinh(10, 1L, null)
        val moi = LuatTre.sinh(10, 2L, cu)
        assertFalse(moi.a == cu.a && moi.b == cu.b && moi.op == cu.op)
    }

    @Test fun doc_hong_thiTat() {
        val c = LuatTre.doc("{")
        assertFalse(c.treBat)
        assertFalse(LuatTre.locDuoc(c.copy(coPin = true)))
    }

    @Test fun chuaPin_khongBat() {
        val c = LuatTre.doc("""{"treBat":false}""").copy(coPin = false)
        assertNull(LuatTre.bat(c))
        assertFalse(LuatTre.locDuoc(c))
    }

    @Test fun bat_vaoModeTre_vaGiuTuyChonDaSua() {
        val c = LuatTre.doc("""{"treBat":false,"phutToan":15,"mucToan":20,"appChoPhep":["$yt","com.netflix.ninja"]}""").copy(coPin = true)
        val bat = LuatTre.bat(c)!!
        assertTrue(bat.treBat)
        assertEquals("tre", bat.treMode)
        assertEquals(15, bat.phutToan)
        assertEquals(20, bat.mucToan)
        assertTrue(LuatTre.locDuoc(bat))
        val phu = LuatTre.sangPhuHuynh(bat)
        assertEquals("phuHuynh", phu.treMode)
        assertFalse(LuatTre.locDuoc(phu))
        assertEquals("phuHuynh", LuatTre.doc(LuatTre.ghiTre(null, phu)).treMode)
        assertEquals("tre", LuatTre.veTre(phu).treMode)
        assertFalse(LuatTre.tat(phu).treBat)
    }

    @Test fun thieuYoutubeDangCai_thiThemLai() {
        val c = LuatTre.doc("""{"appChoPhep":["com.netflix.ninja"]}""")
        val h = LuatTre.hienThi(c, setOf(yt))
        assertTrue(yt in h.app)
        assertTrue("com.netflix.ninja" in h.app)
    }

    @Test fun dongHo_chiTangKhiYoutubeTruoc_vaConSauKhiDoc() {
        var d = LuatTre.DongHo(0, false)
        d = LuatTre.cong(d, youtubeTruoc = false, them = 15)
        assertEquals(0, d.giay)
        d = LuatTre.cong(d, youtubeTruoc = true, them = 15)
        assertEquals(15, d.giay)
        assertFalse(LuatTre.denLuc(d, 30))
        d = LuatTre.cong(d, true, 30 * 60 - 15)
        assertTrue(LuatTre.denLuc(d, 30))
        d = LuatTre.moBai(d)
        assertEquals(30 * 60, LuatTre.cong(d, true, 15).giay)
        val lai = LuatTre.docDongHo(LuatTre.ghiDongHo(d))
        assertEquals(d.giay, lai.giay)
        assertTrue(lai.dangChoBai)
        assertEquals(0, LuatTre.dungBai(d).giay)
        assertFalse(LuatTre.dungBai(d).dangChoBai)
    }

    @Test fun gopJson_giuKhoaTreKhiLuuGio() {
        val cu = """{"bat":true,"treBat":true,"treMode":"tre","phutToan":15,"khung":[]}"""
        val gop = LuatTre.gopJson(cu, """{"bat":false,"khung":[]}""")!!
        assertTrue(LuatTre.doc(gop).treBat)
        assertEquals(15, LuatTre.doc(gop).phutToan)
        assertNull(LuatTre.gopJson(cu, "{"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :khoa:testReleaseUnitTest --tests zone.vgs.khoa.LuatTreTest --console=plain`

Expected: FAIL, unresolved `LuatTre`.

- [ ] **Step 3: Write minimal implementation**

Tạo `khoa/src/main/java/zone/vgs/khoa/LuatTre.kt`:

```kotlin
package zone.vgs.khoa

import org.json.JSONArray
import org.json.JSONObject
import java.util.Random

/** Luật mode trẻ em, bài cộng trừ và đồng hồ xem. Không đụng Android. */
object LuatTre {
    val YOUTUBE = listOf("com.google.android.youtube.tv", "com.google.android.youtube")
    const val HANH_DOI = "zone.vgs.cleantv.TRE_DOI"
    private val KHOA_TRE = listOf("treBat", "treMode", "appChoPhep", "phutToan", "mucToan")

    data class CheDo(
        val treBat: Boolean,
        val treMode: String,
        val coPin: Boolean,
        val app: List<String>,
        val phutToan: Int,
        val mucToan: Int,
    )

    data class Bai(val a: Int, val op: Char, val b: Int, val dung: Int, val sai: List<Int>) {
        fun dapAn(seed: Long): List<Int> {
            val ds = mutableListOf(dung, sai[0], sai[1])
            val r = Random(seed)
            for (i in ds.lastIndex downTo 1) {
                val j = r.nextInt(i + 1)
                val t = ds[i]; ds[i] = ds[j]; ds[j] = t
            }
            return ds
        }
    }

    data class DongHo(val giay: Long, val dangChoBai: Boolean)

    fun macDinh() = CheDo(false, "tre", false, YOUTUBE, 30, 10)

    /** JSON hỏng hoặc thiếu thì tính năng tắt. `coPin` luôn false; caller gán từ PIN đã lưu. */
    fun doc(s: String?): CheDo {
        val o = runCatching { JSONObject(s ?: return macDinh()) }.getOrNull() ?: return macDinh()
        val mucRaw = o.optInt("mucToan", 10)
        val muc = if (mucRaw == 20 || mucRaw == 100) mucRaw else 10
        val phut = o.optInt("phutToan", 30).coerceIn(5, 120)
        val mode = if (o.optString("treMode") == "phuHuynh") "phuHuynh" else "tre"
        val arr = o.optJSONArray("appChoPhep")
        val app = if (arr == null) YOUTUBE else (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        return CheDo(o.optBoolean("treBat"), mode, false, app, phut, muc)
    }

    /** Package YouTube đang cài mà vắng trong danh sách thì thêm lại. */
    fun hienThi(c: CheDo, dangCai: Set<String>): CheDo {
        val them = YOUTUBE.filter { it in dangCai && it !in c.app }
        return if (them.isEmpty()) c else c.copy(app = c.app + them)
    }

    fun locDuoc(c: CheDo) = c.treBat && c.coPin && c.treMode == "tre"

    /** Null khi chưa có PIN. Bật thì vào mode trẻ em, giữ phút / mức / danh sách đã có. */
    fun bat(c: CheDo): CheDo? = if (!c.coPin) null else c.copy(treBat = true, treMode = "tre")

    fun tat(c: CheDo) = c.copy(treBat = false)
    fun sangPhuHuynh(c: CheDo) = if (c.treBat) c.copy(treMode = "phuHuynh") else c
    fun veTre(c: CheDo) = if (c.treBat) c.copy(treMode = "tre") else c

    fun ghiTre(cu: String?, c: CheDo): String {
        val o = runCatching { JSONObject(cu ?: "{}") }.getOrElse { JSONObject() }
        o.put("treBat", c.treBat).put("treMode", c.treMode).put("phutToan", c.phutToan).put("mucToan", c.mucToan)
        o.put("appChoPhep", JSONArray(c.app))
        return o.toString()
    }

    /** Bản ghi giờ khoá không gửi trường mode thì giữ trường mode cũ. JSON mới hỏng thì null. */
    fun gopJson(cu: String?, moi: String): String? {
        val m = runCatching { JSONObject(moi) }.getOrNull() ?: return null
        val c = runCatching { JSONObject(cu ?: "") }.getOrNull()
        if (c != null) for (k in KHOA_TRE) if (!m.has(k) && c.has(k)) m.put(k, c.get(k))
        return m.toString()
    }

    fun ghiDongHo(d: DongHo) = "${d.giay.coerceAtLeast(0)},${if (d.dangChoBai) 1 else 0}"

    fun docDongHo(s: String?): DongHo {
        val p = s?.split(',') ?: return DongHo(0, false)
        val giay = p.getOrNull(0)?.toLongOrNull() ?: return DongHo(0, false)
        if (giay < 0) return DongHo(0, false)
        return DongHo(giay, p.getOrNull(1) == "1")
    }

    fun cong(d: DongHo, youtubeTruoc: Boolean, them: Long): DongHo {
        if (!youtubeTruoc || them <= 0 || d.dangChoBai) return d
        return d.copy(giay = d.giay + them)
    }

    fun denLuc(d: DongHo, phut: Int) = !d.dangChoBai && d.giay >= phut * 60L
    fun moBai(d: DongHo) = d.copy(dangChoBai = true)
    fun dungBai(d: DongHo) = DongHo(0, false)

    fun sinh(muc: Int, seed: Long, truoc: Bai?): Bai {
        val r = Random(seed)
        repeat(12) {
            val b = sinhLoai(muc, if (r.nextBoolean()) "thuong" else if (muc == 100) "nho" else "qua10", seed + it)
            if (truoc == null || b.a != truoc.a || b.b != truoc.b || b.op != truoc.op) return b
        }
        return sinhLoai(muc, "thuong", seed + 99)
    }

    /** `loai` là `thuong`, `qua10` (mức 20) hoặc `nho` (mức 100). */
    fun sinhLoai(muc: Int, loai: String, seed: Long): Bai {
        val r = Random(seed)
        val b = when {
            muc == 100 && loai == "nho" -> baiNho(r)
            muc == 20 && loai == "qua10" -> baiQua10(r)
            muc == 100 -> baiTrong(r, 100)
            muc == 20 -> baiTrong(r, 20)
            else -> baiTrong(r, 10)
        }
        return b.copy(sai = saiGan(b.dung, if (muc == 100) 100 else if (muc == 20) 20 else 10, r))
    }

    fun qua10(b: Bai) = when (b.op) {
        '+' -> b.a <= 9 && b.b <= 9 && b.dung >= 10
        '-' -> b.a > 10 && b.a % 10 < b.b % 10
        else -> false
    }

    fun coNho(b: Bai) = when (b.op) {
        '+' -> b.a % 10 + b.b % 10 >= 10
        '-' -> b.b > 0 && b.a % 10 < b.b % 10
        else -> false
    }

    private fun baiTrong(r: Random, max: Int): Bai {
        val cong = r.nextBoolean()
        if (cong) {
            val tong = r.nextInt(max + 1)
            val a = if (tong == 0) 0 else r.nextInt(tong + 1)
            return Bai(a, '+', tong - a, tong, emptyList())
        }
        val a = r.nextInt(max + 1)
        val b = if (a == 0) 0 else r.nextInt(a + 1)
        return Bai(a, '-', b, a - b, emptyList())
    }

    private fun baiQua10(r: Random): Bai {
        if (r.nextBoolean()) {
            val a = 2 + r.nextInt(8)
            val bMin = 10 - a
            val bMax = minOf(9, 20 - a)
            val b = bMin + r.nextInt(bMax - bMin + 1)
            return Bai(a, '+', b, a + b, emptyList())
        }
        val a = 11 + r.nextInt(10)
        val bMin = a % 10 + 1
        val b = bMin + r.nextInt(a - bMin + 1)
        return Bai(a, '-', b, a - b, emptyList())
    }

    private fun baiNho(r: Random): Bai {
        repeat(20) {
            val cong = r.nextBoolean()
            val a = 10 + r.nextInt(80)
            if (cong) {
                val can = 10 - a % 10
                if (can in 1..9) {
                    val bMax = 100 - a
                    if (bMax >= can) {
                        var b = can + r.nextInt(bMax - can + 1)
                        if (b % 10 < can) b += can - b % 10
                        if (b <= bMax && a + b <= 100 && coNho(Bai(a, '+', b, a + b, emptyList())))
                            return Bai(a, '+', b, a + b, emptyList())
                    }
                }
            } else if (a % 10 != 9) {
                val bMin = a % 10 + 1
                if (bMin <= a) {
                    val b = bMin + r.nextInt(a - bMin + 1)
                    if (coNho(Bai(a, '-', b, a - b, emptyList()))) return Bai(a, '-', b, a - b, emptyList())
                }
            }
        }
        return Bai(27, '+', 15, 42, emptyList())
    }

    private fun saiGan(dung: Int, max: Int, r: Random): List<Int> {
        val pool = mutableListOf<Int>()
        for (v in listOf(dung - 1, dung + 1, dung - 2, dung + 2, dung - 10, dung + 10)) {
            if (v in 0..max && v != dung && v !in pool) pool += v
        }
        var i = 0
        while (pool.size < 2 && i <= max) {
            if (i != dung && i !in pool) pool += i
            i++
        }
        val first = pool.removeAt(r.nextInt(pool.size))
        val second = pool[r.nextInt(pool.size)]
        return listOf(first, second)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :khoa:testReleaseUnitTest --tests zone.vgs.khoa.LuatTreTest --tests zone.vgs.khoa.LuatTest --console=plain`

Expected: PASS cả `LuatTreTest` và `LuatTest`.

- [ ] **Step 5: Commit**

```bash
git add khoa/src/main/java/zone/vgs/khoa/LuatTre.kt khoa/src/test/java/zone/vgs/khoa/LuatTreTest.kt
git commit -m "$(cat <<'EOF'
Thêm luật mode trẻ em, bài cộng trừ và đồng hồ xem để test không cần tivi.

EOF
)"
```

---

### Task 2: Lưu cấu hình và cho Home đọc

**Files:**
- Modify: `khoa/src/main/java/zone/vgs/khoa/Kho.kt`
- Modify: `khoa/src/main/java/zone/vgs/khoa/CauHinhProvider.kt`
- Modify: `khoa/src/main/java/zone/vgs/khoa/KhoiDong.kt` hàm `CanhGac.damBao`

**Interfaces:**
- Consumes: `LuatTre.doc`, `LuatTre.hienThi`, `LuatTre.gopJson`, `LuatTre.ghiTre`, `LuatTre.bat`, `LuatTre.tat`, `LuatTre.sangPhuHuynh`, `LuatTre.veTre`, `LuatTre.docDongHo`, `LuatTre.ghiDongHo`, `LuatTre.YOUTUBE`, `Luat.docJson`, `Luat.dungPin`
- Produces: `Kho.dongHo`, `Kho.cheDo()`, `CauHinhProvider` method `doc-home` và `dat-tre`, broadcast `LuatTre.HANH_DOI`

- [ ] **Step 1: Extend Kho**

Trong `Kho`, thêm dưới `chanDen`:

```kotlin
var dongHo: LuatTre.DongHo
    get() = LuatTre.docDongHo(p.getString("dong_ho", null))
    set(v) = p.edit().putString("dong_ho", LuatTre.ghiDongHo(v)).apply()

/** Mode trẻ em. `coPin` lấy từ PIN khoá giờ. YouTube đang cài được thêm lại. */
fun cheDo(): LuatTre.CheDo {
    val coPin = cauHinh?.pinBam?.isNotBlank() == true
    return LuatTre.hienThi(LuatTre.doc(json).copy(coPin = coPin), dangCai())
}

fun dangCai(): Set<String> = app.packageManager.getInstalledApplications(0).map { it.packageName }.toSet()
```

Đổi đầu class thành `class Kho(ctx: Context)` với `private val app = ctx.applicationContext` và `private val p = app.getSharedPreferences("khoa", Context.MODE_PRIVATE)`. Không giữ `Activity`.

- [ ] **Step 2: Giữ trường mode khi ghi giờ khoá, thêm hai method**

Trong `CauHinhProvider.call`, nhánh `"dat"`: trước `kho.json = moi.toString()`, đổi thành:

```kotlin
val gop = LuatTre.gopJson(kho.json, moi.toString()) ?: return kq.apply { putString("loi", "cau hinh sai") }
val c = Luat.docJson(gop) ?: return kq.apply { putString("loi", "cau hinh sai") }
kho.json = gop
```

Xoá dòng `val c = Luat.docJson(moi.toString())` cũ để không ghi đè mất `treBat`. Giữ đoạn chép PIN khi `pinBam` trống, chạy trên `moi` trước khi `gopJson`.

Thêm hai nhánh:

```kotlin
"doc-home" -> {
    if (!laHome(uid) && !laShell(uid)) throw SecurityException("chỉ Home hoặc ADB")
    val c = kho.cheDo()
    val o = JSONObject().put("treBat", c.treBat).put("treMode", c.treMode).put("appChoPhep", JSONArray(c.app))
    kq.putString("json", o.toString())
}
"dat-tre" -> {
    if (!laShell(uid)) throw SecurityException("chỉ ADB")
    val moi = runCatching { JSONObject(arg ?: "") }.getOrNull() ?: return kq.apply { putString("loi", "cau hinh sai") }
    var c = kho.cheDo()
    if (moi.has("phutToan")) c = c.copy(phutToan = moi.getInt("phutToan").coerceIn(5, 120))
    if (moi.has("mucToan")) {
        val m = moi.getInt("mucToan")
        c = c.copy(mucToan = if (m == 20 || m == 100) m else 10)
    }
    if (moi.has("appChoPhep")) {
        val a = moi.getJSONArray("appChoPhep")
        c = c.copy(app = (0 until a.length()).map { a.getString(it) })
    }
    c = LuatTre.hienThi(c, kho.dangCai())
    when (moi.optString("lenh")) {
        "bat" -> c = LuatTre.bat(c) ?: return kq.apply { putString("loi", "chua co pin") }
        "tat" -> { c = LuatTre.tat(c); kho.dongHo = LuatTre.dungBai(kho.dongHo) }
        "phuHuynh" -> c = LuatTre.sangPhuHuynh(c)
        "tre" -> c = LuatTre.veTre(c)
    }
    kho.json = LuatTre.ghiTre(kho.json, c)
    kq.putString("ok", "1")
}
```

Helper trong provider:

```kotlin
private fun laShell(uid: Int) = uid == Process.SHELL_UID || uid == 0 || uid == Process.myUid()

private fun laHome(uid: Int): Boolean {
    val ps = context!!.packageManager.getPackagesForUid(uid) ?: return false
    return ps.any { it == "com.gathin.tvhome" || it == "com.ts.launcher" }
}
```

Cuối `call`, sau `KhoaDichVu.dangChay?.capNhatSau()`, nếu method là `dat` hoặc `dat-tre` thì:

```kotlin
val i = Intent(LuatTre.HANH_DOI).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
context!!.sendBroadcast(Intent(i).setPackage("com.gathin.tvhome"))
context!!.sendBroadcast(Intent(i).setPackage("com.ts.launcher"))
```

`doc` hiện xoá `pinBam` và `muoi`. Không xoá trường `tre*`. Chúng đi cùng JSON nên điện thoại đọc được qua `doc`.

- [ ] **Step 3: Bật lại dịch vụ khi mode trẻ em đang bật**

`CanhGac.damBao`: đổi `if (!c.bat) return` thành:

```kotlin
val tre = LuatTre.doc(Kho(ctx).json)
if (!c.bat && !tre.treBat) return
```

`c` lấy từ `Kho(ctx).cauHinh`. Nếu `cauHinh` null (JSON giờ khoá hỏng) nhưng `tre.treBat` đúng thì vẫn bật dịch vụ. Tách:

```kotlin
val kho = Kho(ctx)
val c = kho.cauHinh
val tre = LuatTre.doc(kho.json)
if (c?.bat != true && !tre.treBat) return
```

- [ ] **Step 4: Run unit tests**

Run: `./gradlew :khoa:testReleaseUnitTest --tests zone.vgs.khoa.LuatTreTest --tests zone.vgs.khoa.LuatTest --console=plain`

Expected: PASS. Provider không có test JVM vì cần Android. Luật `gopJson` đã được Task 1.

- [ ] **Step 5: Commit**

```bash
git add khoa/src/main/java/zone/vgs/khoa/Kho.kt khoa/src/main/java/zone/vgs/khoa/CauHinhProvider.kt khoa/src/main/java/zone/vgs/khoa/KhoiDong.kt
git commit -m "$(cat <<'EOF'
Lưu mode trẻ em cạnh khoá giờ và cho Home sạch chỉ đọc danh sách app.

EOF
)"
```

---

### Task 3: Phủ bài toán khi YouTube ở trước

**Files:**
- Modify: `khoa/src/main/java/zone/vgs/khoa/KhoaDichVu.kt`

**Interfaces:**
- Consumes: `Kho.cheDo`, `Kho.dongHo`, `LuatTre.locDuoc`, `LuatTre.cong`, `LuatTre.denLuc`, `LuatTre.moBai`, `LuatTre.dungBai`, `LuatTre.sinh`, `LuatTre.YOUTUBE`, `Luat.trongGio`
- Produces: lớp phủ bài trên `KhoaDichVu`; `onKeyEvent` trả `false` cho âm lượng và nguồn khi đang có bài

- [ ] **Step 1: Nhận app đang trước**

`onAccessibilityEvent` đổi từ thân rỗng thành:

```kotlin
override fun onAccessibilityEvent(e: AccessibilityEvent?) {
    if (e?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
    val pkg = e.packageName?.toString() ?: return
    if (pkg == packageName) return
    tay.post { doiMat(pkg) }
}
```

Thêm field: `private var mat: String? = null`, `private var bai: LuatTre.Bai? = null`, `private var lopBai: View? = null`, `private var moc = android.os.SystemClock.elapsedRealtime()`, `private var chonBai = 0`.

```kotlin
private fun laYoutube(pkg: String?) = pkg in LuatTre.YOUTUBE

private fun doiMat(pkg: String) {
    if (pkg == mat) return
    ghiNhip()
    mat = pkg
    moc = android.os.SystemClock.elapsedRealtime()
}
```

- [ ] **Step 2: Đếm trong nhịp 15 giây, không đè lớp khoá giờ**

Trong `capNhat()`, đầu hàm, gọi `ghiNhip()` rồi xét bài chỉ khi lớp khoá giờ không hiện (`lop == null`):

```kotlin
private fun ghiNhip() {
    val nay = android.os.SystemClock.elapsedRealtime()
    val them = ((nay - moc) / 1000).coerceIn(0, 20)
    moc = nay
    val c = kho.cheDo()
    if (!LuatTre.locDuoc(c)) {
        if (lopBai != null) dongBai(phat = true)
        return
    }
    var d = LuatTre.cong(kho.dongHo, laYoutube(mat), them)
    if (d.dangChoBai && laYoutube(mat) && lopBai == null && lop == null) hienBai(c.mucToan)
    else if (LuatTre.denLuc(d, c.phutToan) && laYoutube(mat) && lop == null) {
        d = LuatTre.moBai(d)
        kho.dongHo = d
        dungPhat()
        hienBai(c.mucToan)
        return
    }
    kho.dongHo = d
}
```

`com.google.android.apps.mediashell` không nằm trong `YOUTUBE`, nên cast không đếm và không hiện bài.

Lớp khoá giờ (`Luat.trongGio` như code cũ) vẫn ưu tiên: khi `lop != null`, không gọi `hienBai`.

- [ ] **Step 3: Lớp phủ và phím**

`dungPhat()` gửi `KEYCODE_MEDIA_PAUSE` qua `AudioManager.dispatchMediaKeyEvent` (ACTION_DOWN rồi ACTION_UP). `phatLai()` gửi `KEYCODE_MEDIA_PLAY` và gọi `imTieng(false)`.

`hienBai(muc)`: nếu `lopBai` đã có thì return. `bai = LuatTre.sinh(muc, System.nanoTime(), bai)`. Dựng `LinearLayout` chữ phép `${bai.a} ${bai.op} ${bai.b}` cỡ 48sp và ba `TextView` đáp án từ `bai.dapAn(System.nanoTime())`. Nền `#FF0A0718` giống lớp khoá. Thêm overlay `TYPE_ACCESSIBILITY_OVERLAY` cờ `FLAG_NOT_FOCUSABLE` như `hienKhoa`. Rồi `imTieng(true)` để hình bị che và tiếng tắt kể cả khi lệnh tạm dừng không ăn.

`dongBai(phat: Boolean)` gỡ view, `lopBai = null`. Nếu `phat` thì `kho.dongHo = LuatTre.dungBai(kho.dongHo)` và `phatLai()`. Nếu không `phat` (chọn sai) thì giữ `dangChoBai`.

Chọn đúng: `dongBai(true)`. Chọn sai: gỡ ba nhãn, `bai = LuatTre.sinh(muc, System.nanoTime(), bai)`, vẽ lại. Không ghi đáp án đúng lên màn.

`onKeyEvent`: nếu `lop != null` giữ nguyên (nuốt hết, kể cả âm lượng). Nếu `lopBai != null`:

```kotlin
if (e.keyCode == KeyEvent.KEYCODE_VOLUME_UP || e.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
    e.keyCode == KeyEvent.KEYCODE_VOLUME_MUTE || e.keyCode == KeyEvent.KEYCODE_POWER) return false
if (e.action == KeyEvent.ACTION_DOWN) tay.post { phimBai(e.keyCode) }
return true
```

`phimBai`: trái/phải đổi `chonBai` trong 0..2, `KEYCODE_DPAD_CENTER` hoặc `ENTER` chọn. Không xử lý Back thành đóng bài.

- [ ] **Step 4: Compile**

Run: `./gradlew :khoa:assembleRelease --console=plain`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add khoa/src/main/java/zone/vgs/khoa/KhoaDichVu.kt
git commit -m "$(cat <<'EOF'
Hiện bài cộng trừ khi YouTube ở trước màn hình ở mode trẻ em.

EOF
)"
```

---

### Task 4: Màn phụ huynh trên tivi

**Files:**
- Create: `khoa/src/main/java/zone/vgs/khoa/ManPhuHuynh.kt`
- Modify: `khoa/src/main/AndroidManifest.xml`
- Modify: `khoa/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `Kho.cheDo`, `Kho.json`, `Luat.dungPin`, `Luat.bam`, `LuatTre.bat`, `LuatTre.tat`, `LuatTre.veTre`, `LuatTre.ghiTre`, `LuatTre.hienThi`, `LuatTre.HANH_DOI`, `KhoaDichVu.capNhatSau`
- Produces: activity `zone.vgs.khoa.ManPhuHuynh` exported, intent `zone.vgs.khoa.PHU_HUYNH`

- [ ] **Step 1: Khai báo activity**

Trong `AndroidManifest.xml`, trong `<application>`:

```xml
<activity android:name=".ManPhuHuynh" android:exported="true" android:excludeFromRecents="true">
    <intent-filter>
        <action android:name="zone.vgs.khoa.PHU_HUYNH" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

- [ ] **Step 2: Activity**

`ManPhuHuynh : Activity` dựng UI bằng `TextView` / `LinearLayout` giống `KhoaDichVu` (tivi không có Compose trong module này).

Luồng:

- Chưa có PIN (`cheDo().coPin` false): hai lần nhập 4 số. Khác nhau thì báo và nhập lại. Giống nhau thì tạo `muoi` 16 ký tự, `pinBam = Luat.bam(muoi, pin)`, ghi vào JSON hiện có cùng `muoi`/`pinBam`, không bật `treBat` cho đến khi bấm Bật.
- Đang `locDuoc`: bàn phím PIN. Đúng thì `LuatTre.sangPhuHuynh` và ghi JSON, rồi hiện cài đặt. Sai: tăng `kho.sai`; đủ 5 thì `kho.chanDen = now + 60_000` và `kho.sai = 0`, cùng chữ với lớp khoá.
- Đang mode phụ huynh hoặc tính năng tắt (đã có PIN): vào thẳng cài đặt. Back gọi `finish()`, không đổi `treMode`.

Cài đặt:

- Nút Bật: `LuatTre.bat`. Null thì không ghi (chưa PIN).
- Nút Tắt: `LuatTre.tat` và `kho.dongHo = LuatTre.dungBai(kho.dongHo)`.
- Nút Về mode trẻ em: `LuatTre.veTre`.
- Ba chip mức 10 / 20 / 100 và số phút 5–120 (nút trừ/cộng 5). Đổi phút hoặc mức không đóng bài đang hiện; dịch vụ đọc mức mới ở lần `sinh` sau.
- Danh sách app có `ACTION_MAIN` + `LEANBACK_LAUNCHER`, rồi `LAUNCHER` nếu chưa có package. Ô YouTube (`LuatTre.YOUTUBE`) luôn chọn, không bỏ được. Ô khác bật/tắt được.
- Lưu bằng `kho.json = LuatTre.ghiTre(kho.json, LuatTre.hienThi(c, kho.dangCai()))`, `KhoaDichVu.dangChay?.capNhatSau()`, và hai `sendBroadcast` `LuatTre.HANH_DOI` như provider.

- [ ] **Step 3: Compile và commit**

Run: `./gradlew :khoa:assembleRelease --console=plain`

Expected: BUILD SUCCESSFUL.

```bash
git add khoa/src/main/java/zone/vgs/khoa/ManPhuHuynh.kt khoa/src/main/AndroidManifest.xml khoa/src/main/res/values/strings.xml
git commit -m "$(cat <<'EOF'
Thêm màn phụ huynh trên tivi để đặt PIN, danh sách app và mức toán.

EOF
)"
```

---

### Task 5: Home sạch lọc app và có mục Phụ huynh

Nguồn launcher không nằm trong repo. Sửa bằng cách thêm hunk vào `patches/launcher-vgs.patch` trên cây `upstream` ở commit `fa1a2f00` (file `app/src/main/assets/upstream-commit.txt`). `scripts/dung-launcher.sh` áp patch rồi dựng cả `com.gathin.tvhome` và `com.ts.launcher`.

**Files:**
- Create in upstream, via the patch: `launcher/app/src/main/java/com/gathin/tvhome/ChildMode.java`
- Modify via the patch: `launcher/app/src/main/java/com/gathin/tvhome/AppAdapter.java` (`reload` quanh dòng kiểm `LibraryApps.visible`, và `launch`)
- Modify via the patch: `launcher/app/src/main/java/com/gathin/tvhome/AppLibraryDialog.java` (`load`, chỗ `unique.put`)
- Modify via the patch: `launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java` (`onStart` / `onStop`)
- Modify via the patch: `launcher/app/src/main/AndroidManifest.xml` chỉ nếu cần quyền không có sẵn. Content call sang app khác không cần permission mới.

**Interfaces:**
- Consumes: `content://zone.vgs.cleantv.khoa` method `doc-home`, action `zone.vgs.khoa.PHU_HUYNH`, broadcast `zone.vgs.cleantv.TRE_DOI`
- Produces: lưới, dock và thư viện chỉ còn `appChoPhep` khi `treBat && treMode == tre`. Hàng `LinkTiles` giữ nguyên.

- [ ] **Step 1: ChildMode.java**

```java
package com.gathin.tvhome;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Đọc mode trẻ em từ Khoá trẻ em. Null nghĩa là không lọc (tắt, mode phụ huynh, hoặc chưa cài). */
final class ChildMode {
    private ChildMode() {}

    static Set<String> allow(Context ctx) {
        try {
            Bundle b = ctx.getContentResolver().call(
                    Uri.parse("content://zone.vgs.cleantv.khoa"), "doc-home", null, null);
            if (b == null) return null;
            JSONObject o = new JSONObject(b.getString("json", "{}"));
            if (!o.optBoolean("treBat") || !"tre".equals(o.optString("treMode"))) return null;
            JSONArray a = o.optJSONArray("appChoPhep");
            Set<String> s = new HashSet<>();
            if (a != null) for (int i = 0; i < a.length(); i++) s.add(a.optString(i));
            return Collections.unmodifiableSet(s);
        } catch (Exception e) {
            return null;
        }
    }
}
```

`ContentResolver.call` trả `Bundle`.

- [ ] **Step 2: Lọc lưới và dock, không lọc kênh**

Trong `AppAdapter.reload`, ngay trước vòng `for (ResolveInfo ri : found)`, `Set<String> tre = ChildMode.allow(ctx);`.

Chỗ điều kiện `LibraryApps.visible`, thêm:

```java
if (tre != null && !tre.contains(pkg)) continue;
```

Đặt trước `continue` hiện có. Không thêm điều kiện này vào vòng `LinkTiles.load` trong patch VGS (hàng kênh giữ nguyên).

Sau khi dựng `fresh`, luôn thêm một `Entry` loại mới `Entry.PHU_HUYNH = 5` (LINK đang là 4), nhãn `Phụ huynh` khi `Locale` là `vi`, không thì `Parents`, component `new ComponentName("zone.vgs.khoa", "zone.vgs.khoa.ManPhuHuynh")`. Ô này không đi qua `tre.contains`.

Trong `launch`, nếu `e.kind == Entry.PHU_HUYNH` thì `startActivity(new Intent("zone.vgs.khoa.PHU_HUYNH").setPackage("zone.vgs.cleantv.khoa").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))`.

- [ ] **Step 3: Lọc thư viện**

Trong `AppLibraryDialog.load`, sau khi có `pkg`, nếu `ChildMode.allow(activity)` khác null và không chứa `pkg` thì `continue` trước `unique.put`.

- [ ] **Step 4: Vẽ lại khi mode đổi**

`MainActivity` đăng ký `BroadcastReceiver` ở `onStart` (dòng 930), huỷ ở `onStop`, lọc action `zone.vgs.cleantv.TRE_DOI`. Receiver chạy trên main thread và gọi `reloadApps(false, true, false)`. Hàm đó gọi `reloadTiles()` (dòng 1104), và `reloadTiles` gọi `adapter.reload` (dòng 1118). `onResume` đi qua `reloadApps` khi tile đổi, nên về Home cũng lọc lại nếu broadcast tới lúc activity đang stop.

- [ ] **Step 5: Dựng lại asset**

Run: `scripts/dung-launcher.sh`

Expected: dòng `Xong: launcher` và exit 0. Patch phải `git apply --check` được trên upstream sạch. Nếu upstream đã áp patch, script bỏ qua apply; khi đó sửa trực tiếp file trong `upstream/launcher` rồi `git -C upstream diff > patches/launcher-vgs.patch` chỉ khi diff mới nằm trong các file của task này. Không commit thư mục `upstream/` (gitignore).

Commit patch và apk asset nếu script cập nhật `app/src/main/assets/tvhome.apk`, `tvhome-xiaomi.apk`, `upstream-commit.txt`:

```bash
git add patches/launcher-vgs.patch app/src/main/assets/tvhome.apk app/src/main/assets/tvhome-xiaomi.apk app/src/main/assets/upstream-commit.txt
git commit -m "$(cat <<'EOF'
Home sạch ở mode trẻ em chỉ hiện app được phép và có mục Phụ huynh.

EOF
)"
```

Chỉ `git add` file nào thực sự đổi.

---

### Task 6: Cấu hình từ điện thoại

**Files:**
- Modify: `app/src/main/java/zone/vgs/tvsach/adb/ThaoTacTv.kt` cạnh `docKhoa` (khoảng dòng 1156)
- Modify: `app/src/main/java/zone/vgs/tvsach/ui/PhienLamViec.kt` khối khoá trẻ em (khoảng dòng 752)
- Modify: `app/src/main/java/zone/vgs/tvsach/ui/UngDung.kt` `ManKhoaTre` (khoảng dòng 1875) và dòng nút menu khoảng 717
- Modify: `app/src/main/assets/khoa.apk` qua `scripts/dung-khoa.sh`

**Interfaces:**
- Consumes: content method `dat-tre` và `doc` (đã trả trường `tre*`, `coPin`, `dichVuChay`)
- Produces: khối Mode trẻ em trên màn Khoá trẻ em

- [ ] **Step 1: Đóng APK khoa vào assets**

Run: `scripts/dung-khoa.sh`

Expected: `Xong: khoa.apk` và unit test `:khoa` PASS.

- [ ] **Step 2: ADB**

Trong `ThaoTacTv`:

```kotlin
fun datTre(json: String) {
    require(!json.contains('\'')) { "json" }
    val kq = sh("content call --uri $khoaUri --method dat-tre --arg '$json'")
    if (!kq.contains("ok=1")) error(t("Tivi không nhận mode trẻ em: ${kq.trim()}", "The TV rejected kids mode: ${kq.trim()}"))
}
```

`docKhoa()` đã trả JSON. Parser cắt bằng `json=` và `}]` có thể cụt nếu mảng `appChoPhep` đứng sau `khung`. Đổi chỗ lấy JSON: từ `json=` đến hết dòng output của bundle, `trim` dấu `Bundle[...]`. Nếu parser cũ làm rơi `appChoPhep`, sửa để lấy nguyên chuỗi sau `json=` đến trước `, ` cuối hoặc đến hết. Kiểm tra bằng một chuỗi mẫu có cả `khung` và `appChoPhep`.

- [ ] **Step 3: Trạng thái và lưu**

Trong `PhienLamViec`, cạnh `khoaBat`: `treBat`, `trePhut` (mặc định 30), `treMuc` (mặc định 10), `treApp: mutableStateListOf<String>`, `treMode`. `moKhoaTre` đọc các field từ `docKhoa()`.

`luuTre()`:

- Chưa `coPin` và PIN đang gõ không đủ 4 số hai lần giống nhau thì báo lỗi như `luuKhoaTre`.
- Nếu cần đặt PIN, gọi `dat` hiện có (giữ khung giờ) để ghi `pinBam` trước.
- Rồi `datTre` với JSON `lenh` = `bat` hoặc `tat`, `phutToan`, `mucToan`, `appChoPhep`. YouTube không xoá khỏi list trước khi gửi; provider cũng thêm lại.
- Nút mode: `lenh` `phuHuynh` hoặc `tre`.
- Sau khi đọc lại, nếu `treBat` và `dichVuChay` false, `khoaBao` = `t("Đã lưu nhưng dịch vụ chưa chạy, nên chưa hiện bài toán. Tắt tivi bật lại.", "Saved, but the service is not running, so the quiz will not show. Restart the TV.")`.

Menu ở `UngDung.kt` khoảng dòng 717: đổi chữ nút thành `t("Khoá trẻ em và mode trẻ em", "Kids lock and kids mode")`.

Trong `ManKhoaTre`, thêm một `The` "Mode trẻ em": công tắc bật, ba chip mức 10/20/100, số phút, danh sách package lấy từ `trangThai?.caiDat` giao với app có biểu tượng nếu đã có sẵn trong phiên (cùng nguồn màn chọn gói đang dùng: `trangThai.coBieuTuong`). Ô YouTube hiện đã chọn và không gọi xoá. Nút "Mode phụ huynh" / "Về mode trẻ em" gọi `datTre` với `lenh` tương ứng. Không hỏi PIN trên điện thoại.

- [ ] **Step 4: Unit test app vẫn qua chỗ parser nếu sửa `docKhoa`**

Run: `./gradlew :app:testReleaseUnitTest --tests zone.vgs.tvsach.dulieu.KeHoachTest --console=plain`

Expected: PASS. Không có test UI. Nếu tách hàm cắt chuỗi `docKhoa` thành hàm thuần trong `dulieu` để test được thì thêm một test: chuỗi bundle có `appChoPhep` sau `khung` vẫn ra đủ package. Không bắt buộc nếu parser cũ đã lấy đủ; khi đó ghi trong commit là parser không đổi.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/zone/vgs/tvsach/adb/ThaoTacTv.kt app/src/main/java/zone/vgs/tvsach/ui/PhienLamViec.kt app/src/main/java/zone/vgs/tvsach/ui/UngDung.kt app/src/main/assets/khoa.apk
git commit -m "$(cat <<'EOF'
Đặt mode trẻ em, số phút và mức toán từ app trên điện thoại.

EOF
)"
```

---

## Kiểm tra tay trên tivi

Sau Task 6, cài bản app mới lên một tivi đã có Home sạch:

- Chưa PIN: không lọc, không bài. Đặt PIN rồi bật: Home chỉ còn YouTube và mục Phụ huynh. Hàng kênh còn.
- PIN trên tivi vào mode phụ huynh: mọi app hiện lại, Back không tự về mode trẻ em. Nút Về mode trẻ em thì lọc lại.
- YouTube đủ số phút: hình và tiếng dừng, chọn sai ra bài khác, chọn đúng thì xem tiếp. Giọng nói vẫn mở YouTube. Cast từ Chrome trên máy tính không bị chặn và không hiện bài.
- Tắt nguồn giữa chừng: số phút đã xem không về 0.
- Khoá theo khung giờ vẫn che màn trong giờ khoá; hết giờ thì mode trẻ em còn.

## Self-review

- Spec "lọc thư viện": Task 5 Step 3.
- Spec "không lọc kênh": Task 5 Step 2 nói rõ không sửa vòng `LinkTiles`.
- Spec "JSON hỏng thì tắt": `LuatTre.doc`.
- Spec "không bật khi chưa PIN": `LuatTre.bat` và `dat-tre` lệnh `bat`.
- Spec "tắt nguồn không xoá phút": `Kho.dongHo` + `ghiNhip`.
- Spec "lệnh tạm dừng thất bại vẫn che và tắt tiếng": `hienBai` luôn `imTieng(true)`.
- Khoá giờ và mode trẻ em: `capNhat` không hiện bài khi `lop != null`.
