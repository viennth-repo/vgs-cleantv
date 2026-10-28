package zone.vgs.khoa

import android.content.Context

/** Trạng thái lưu trên tivi: cấu hình, mở tạm tới lúc nào, số lần nhập sai. */
class Kho(ctx: Context) {
    private val app = ctx.applicationContext
    private val p = app.getSharedPreferences("khoa", Context.MODE_PRIVATE)
    var json: String? get() = p.getString("cau_hinh", null); set(v) = p.edit().putString("cau_hinh", v).apply()
    var moTamDen: Long get() = p.getLong("mo_tam_den", 0); set(v) = p.edit().putLong("mo_tam_den", v).apply()
    var sai: Int get() = p.getInt("sai", 0); set(v) = p.edit().putInt("sai", v).apply()
    var chanDen: Long get() = p.getLong("chan_den", 0); set(v) = p.edit().putLong("chan_den", v).apply()
    var dongHo: LuatTre.DongHo
        get() = LuatTre.docDongHo(p.getString("dong_ho", null))
        set(v) = p.edit().putString("dong_ho", LuatTre.ghiDongHo(v)).apply()

    /** Mode trẻ em. `coPin` lấy từ PIN khoá giờ. YouTube đang cài được thêm lại. */
    fun cheDo(): LuatTre.CheDo {
        val coPin = cauHinh?.pinBam?.isNotBlank() == true
        return LuatTre.hienThi(LuatTre.doc(json).copy(coPin = coPin), dangCai())
    }

    /** Tên package đang cài, để thêm YouTube còn thiếu vào danh sách cho phép. */
    fun dangCai(): Set<String> = app.packageManager.getInstalledApplications(0).map { it.packageName }.toSet()

    val cauHinh: CauHinh? get() = Luat.docJson(json)
}
