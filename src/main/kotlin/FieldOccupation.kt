data class FieldOccupation(
    val category: Category,
    val championship: String,
    val date: Int,
    val startTime: Int,
    val duration: Int,
    val team: String,
    val otherTeam: String,
    val field: String,
) {
    val endTime: Int
        get() {
            val hours = startTime / 10000
            val minutes = (startTime / 100) % 100
            val seconds = startTime % 100

            return (hours + (minutes + duration) / 60) * 10000 + (minutes + duration) % 60 * 100 + seconds
        }
}

enum class Category(val eight:Int, val duration: Int, val fraction: String? = null) {
    u7(1, 60, "⅛"),
    u8(1, 60, "⅛"),
    u9(2, 60, "¼"),
    u10(4, 60, "½"),
    u11(4, 60, "½"),
    u12(4, 60, "½"),
    u14(8, 90),
    u16(8, 90),
    u19(8, 90),
    gentleman(4, 90, "½"),
    lady(4, 90, "½"),
    adult(8, 90),
}
