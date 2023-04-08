data class SportlinkData(
    val data: List<List<String>>,
    val columns: List<Column>,
    val cache: Boolean?,
)

data class Column(
    val title: String?,
    val type: String?,
    val english: String?,
)
