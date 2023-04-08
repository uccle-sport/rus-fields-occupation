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

enum class Category(val eight:Int, val duration: Int) {
    u7(1, 60),
    u8(1, 60),
    u9(2, 60),
    u10(4, 60),
    u11(4, 60),
    u12(8, 60),
    u14(8, 90),
    u16(8, 90),
    u19(8, 90),
    gentleman(4, 90),
    lady(4, 90),
    adult(8, 90),
}
