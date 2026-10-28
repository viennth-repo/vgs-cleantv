package zone.vgs.tvsach.dulieu

/**
 * Cắt object JSON từ output `content call --method doc` (`Bundle[{json=...}]`).
 * Lấy hết object sau `json=`, bỏ vỏ Bundle. Không dừng ở `}]` của mảng `khung` khi `appChoPhep` đứng sau.
 */
fun catJsonKhoa(out: String): String? {
    val dong = out.lineSequence().firstOrNull { "json=" in it } ?: return null
    val sau = dong.substring(dong.indexOf("json=") + 5).trim()
    if (sau.isEmpty() || sau == "null") return null
    return catObjectJson(sau)
}

private fun catObjectJson(s: String): String? {
    val i = s.indexOf('{')
    if (i < 0) return null
    var depth = 0
    var chuoi = false
    var escape = false
    for (k in i until s.length) {
        val c = s[k]
        if (chuoi) {
            if (escape) escape = false
            else if (c == '\\') escape = true
            else if (c == '"') chuoi = false
            continue
        }
        when (c) {
            '"' -> chuoi = true
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return s.substring(i, k + 1)
            }
        }
    }
    return null
}
