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
        /** Xáo ba đáp án theo seed để UI hiện thứ tự ổn định. */
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

    /** Mode tắt, YouTube mặc định, 30 phút, mức 10. */
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

    /** Lọc app khi mode trẻ em đang bật và đã có PIN. */
    fun locDuoc(c: CheDo) = c.treBat && c.coPin && c.treMode == "tre"

    /** Null khi chưa có PIN. Bật thì vào mode trẻ em, giữ phút / mức / danh sách đã có. */
    fun bat(c: CheDo): CheDo? = if (!c.coPin) null else c.copy(treBat = true, treMode = "tre")

    /** Tắt mode trẻ em, giữ các tuỳ chọn khác. */
    fun tat(c: CheDo) = c.copy(treBat = false)

    /** Vào mode phụ huynh nếu tính năng đang bật. */
    fun sangPhuHuynh(c: CheDo) = if (c.treBat) c.copy(treMode = "phuHuynh") else c

    /** Quay lại mode trẻ em nếu tính năng đang bật. */
    fun veTre(c: CheDo) = if (c.treBat) c.copy(treMode = "tre") else c

    /** Ghi các trường mode trẻ em vào JSON cấu hình. */
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

    /** Chuỗi `giây,0|1` để lưu đồng hồ xem. */
    fun ghiDongHo(d: DongHo) = "${d.giay.coerceAtLeast(0)},${if (d.dangChoBai) 1 else 0}"

    /** Đọc đồng hồ; chuỗi hỏng thì về 0 giây, không chờ bài. */
    fun docDongHo(s: String?): DongHo {
        val p = s?.split(',') ?: return DongHo(0, false)
        val giay = p.getOrNull(0)?.toLongOrNull() ?: return DongHo(0, false)
        if (giay < 0) return DongHo(0, false)
        return DongHo(giay, p.getOrNull(1) == "1")
    }

    /** Chỉ cộng giây khi trước đó đang YouTube, chưa chờ bài, và `them` > 0. */
    fun cong(d: DongHo, youtubeTruoc: Boolean, them: Long): DongHo {
        if (!youtubeTruoc || them <= 0 || d.dangChoBai) return d
        return d.copy(giay = d.giay + them)
    }

    /** Đến lúc hỏi bài khi đã đủ phút và chưa đang chờ bài. */
    fun denLuc(d: DongHo, phut: Int) = !d.dangChoBai && d.giay >= phut * 60L

    /** Đánh dấu đang chờ trẻ làm bài; đồng hồ ngừng tăng. */
    fun moBai(d: DongHo) = d.copy(dangChoBai = true)

    /** Làm xong bài: về 0 giây, không còn chờ. */
    fun dungBai(d: DongHo) = DongHo(0, false)

    /** Sinh bài trong mức; tránh trùng phép với bài trước nếu có. */
    fun sinh(muc: Int, seed: Long, truoc: Bai?): Bai {
        val r = Random(seed)
        r.nextInt()
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

    /** Cộng hai số ≤ 9 ra ≥ 10, hoặc trừ mượn hàng đơn vị khi số bị trừ > 10. */
    fun qua10(b: Bai) = when (b.op) {
        '+' -> b.a <= 9 && b.b <= 9 && b.dung >= 10
        '-' -> b.a > 10 && b.a % 10 < b.b % 10
        else -> false
    }

    /** Cộng/trừ có nhớ (tổng hàng đơn vị ≥ 10 hoặc trừ mượn). */
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
        r.nextInt()
        if (r.nextBoolean()) {
            val a = 2 + r.nextInt(8)
            val bMin = 10 - a
            val bMax = minOf(9, 20 - a)
            val b = bMin + r.nextInt(bMax - bMin + 1)
            return Bai(a, '+', b, a + b, emptyList())
        }
        val a = 11 + r.nextInt(8)
        val bMin = a % 10 + 1
        val b = bMin + r.nextInt(9 - bMin + 1)
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
