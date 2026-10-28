package zone.vgs.khoa

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import org.json.JSONArray
import org.json.JSONObject

/**
 * CleanTV đặt / đọc cấu hình qua ADB:
 *   content call --uri content://zone.vgs.cleantv.khoa --method dat --arg '<json>'
 *   content call --uri content://zone.vgs.cleantv.khoa --method doc
 *   content call --uri content://zone.vgs.cleantv.khoa --method mo-tam --arg 60   (phút; 0 = khoá lại ngay)
 *   content call --uri content://zone.vgs.cleantv.khoa --method dat-tre --arg '<json>'
 *   content call --uri content://zone.vgs.cleantv.khoa --method doc-home
 * Đặt giờ khoá và `dat-tre` chỉ từ shell, root hoặc chính app. `doc-home` thêm Home sạch.
 */
class CauHinhProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val uid = Binder.getCallingUid()
        if (method != "doc-home" && !laShell(uid)) throw SecurityException("chỉ ADB")
        val kho = Kho(context!!)
        val kq = Bundle()
        when (method) {
            "dat" -> {
                // Không gửi PIN mới (chỉ đổi giờ / tắt) thì giữ PIN cũ.
                val moi = runCatching { JSONObject(arg ?: "") }.getOrNull() ?: return kq.apply { putString("loi", "cau hinh sai") }
                if (moi.optString("pinBam").isBlank()) kho.cauHinh?.let { cu -> moi.put("muoi", cu.muoi).put("pinBam", cu.pinBam) }
                val gop = LuatTre.gopJson(kho.json, moi.toString()) ?: return kq.apply { putString("loi", "cau hinh sai") }
                val c = Luat.docJson(gop) ?: return kq.apply { putString("loi", "cau hinh sai") }
                kho.json = gop
                kho.sai = 0; kho.chanDen = 0
                if (!c.bat) kho.moTamDen = 0
                kq.putString("ok", "1")
            }
            "mo-tam" -> {
                val phut = arg?.toIntOrNull() ?: 60
                kho.moTamDen = if (phut <= 0) 0 else System.currentTimeMillis() + phut * 60_000L
                kq.putString("ok", "1")
            }
            "doc" -> {
                val c = kho.cauHinh
                val o = JSONObject(kho.json ?: "{}").apply { remove("pinBam"); remove("muoi") }
                o.put("coPin", c?.pinBam?.isNotBlank() == true)
                o.put("moTamDen", kho.moTamDen)
                o.put("dangKhoa", KhoaDichVu.dangKhoa)
                o.put("dichVuChay", KhoaDichVu.dangChay != null)
                kq.putString("json", o.toString())
            }
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
            else -> kq.putString("loi", "khong biet $method")
        }
        KhoaDichVu.dangChay?.capNhatSau()
        if (method == "dat" || method == "dat-tre") {
            val i = Intent(LuatTre.HANH_DOI).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            context!!.sendBroadcast(Intent(i).setPackage("com.gathin.tvhome"))
            context!!.sendBroadcast(Intent(i).setPackage("com.ts.launcher"))
        }
        return kq
    }

    private fun laShell(uid: Int) = uid == Process.SHELL_UID || uid == 0 || uid == Process.myUid()

    private fun laHome(uid: Int): Boolean {
        val ps = context!!.packageManager.getPackagesForUid(uid) ?: return false
        return ps.any { it == "com.gathin.tvhome" || it == "com.ts.launcher" }
    }

    override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?) = null
    override fun getType(u: Uri): String? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}
