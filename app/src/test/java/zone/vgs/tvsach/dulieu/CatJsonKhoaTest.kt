package zone.vgs.tvsach.dulieu

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Output `content call` có `appChoPhep` đứng sau mảng `khung` (28/10/2026). */
class CatJsonKhoaTest {
    @Test fun appChoPhepSauKhung_khongBiCatCum() {
        val out = """Bundle[{json={"bat":true,"khung":[{"tu":"21:00","den":"06:00","ngay":[1,2]}],"appChoPhep":["com.google.android.youtube.tv","com.netflix.ninja"],"coPin":true}"""
        val a = out.indexOf("json=")
        val b = out.lastIndexOf("}]")
        val cu = out.substring(a + 5, b)
        assertFalse(cu.contains("com.netflix.ninja"))
        val arr = JSONObject(catJsonKhoa(out)!!).getJSONArray("appChoPhep")
        assertEquals(
            listOf("com.google.android.youtube.tv", "com.netflix.ninja"),
            (0 until arr.length()).map { arr.getString(it) },
        )
    }

    @Test fun bundleDayDu_vanRaHetPackage() {
        val out = """Result: Bundle[{json={"bat":true,"khung":[{"tu":"21:00","den":"06:00","ngay":[1]}],"appChoPhep":["com.google.android.youtube.tv","com.google.android.youtube"],"coPin":true,"dichVuChay":true}}]"""
        val j = JSONObject(catJsonKhoa(out)!!)
        val arr = j.getJSONArray("appChoPhep")
        assertEquals("com.google.android.youtube.tv", arr.getString(0))
        assertEquals("com.google.android.youtube", arr.getString(1))
        assertEquals(true, j.getBoolean("dichVuChay"))
    }

    @Test fun bundleThemTruongSauJson_khongNuotPhanDu() {
        val out = """Bundle[{json={"appChoPhep":["com.a","com.b"]}, extra=1}]"""
        val arr = JSONObject(catJsonKhoa(out)!!).getJSONArray("appChoPhep")
        assertEquals("com.b", arr.getString(1))
    }

    @Test fun khongCoJson() {
        assertNull(catJsonKhoa("Result: null"))
        assertNull(catJsonKhoa("Bundle[{json=null}]"))
    }
}
