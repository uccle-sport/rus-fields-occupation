import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.calendar.Calendar.Builder
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.jackson.*
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.time.Duration.Companion.days

val SCOPES = listOf(CalendarScopes.CALENDAR_READONLY, CalendarScopes.CALENDAR_EVENTS)
val httpTransport: NetHttpTransport = GoogleNetHttpTransport.newTrustedTransport()
const val TOKENS_DIRECTORY_PATH = "tokens"

val gsonFactory: GsonFactory = GsonFactory.getDefaultInstance()
val fields = listOf(
    "c_6c7b57e27023124330349ddcd033d0fd6c37ea408b0f0e38e2c07c135b442cdd@group.calendar.google.com",
    "c_0b034e5f2034db98fb7caae1c7c3c2e65b7eda1377e2106f41756e2fdc8fb304@group.calendar.google.com"
)
val zone: ZoneId = java.time.ZoneId.of("Europe/Brussels")


@Throws(IOException::class)
private fun getCredentials(httpTransport: NetHttpTransport): Credential? {
    // Load client secrets.
    val cred = Credentials()
    val clientSecrets = GoogleClientSecrets.load(gsonFactory, cred.credentials?.reader())

    // Build flow and trigger user authorization request.
    val flow =
        GoogleAuthorizationCodeFlow.Builder(
            httpTransport, gsonFactory, clientSecrets, SCOPES
        )
            .setDataStoreFactory(FileDataStoreFactory(File(TOKENS_DIRECTORY_PATH)))
            .setAccessType("offline")
            .build()
    val receiver = LocalServerReceiver.Builder().setPort(8888).build()
    //returns an authorized Credential object.
    return AuthorizationCodeInstalledApp(flow, receiver).authorize("antoine@ucclesport.be")
}


private const val FIELD_1_PATTERN = "UCCL 1"
private const val FIELDS_MAX_INDEX = 1

private const val ADDRESS = "Chaussée de Ruisbroek 18 - 1180 Uccle"

private val nf = DecimalFormat("00")
private val sdf = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val googleDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

private val client = HttpClient(CIO) {
    install(ContentNegotiation) {
        jackson()
    }
}

fun main() = runBlocking {
    val now = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS)

    try {
        (0..8).forEach { week ->
            val cursor = now + Duration.ofDays(week * 7L)
            val data: SportlinkData =
                client
                    .get("https://hockey.be/wp-json/sportlink-api/cached?lang=fr&endpoint=program&dump=&clubid=&facilityid=CD7LH3S&poolid=0&subpool=A&from=${cursor.format(sdf)}&to=${(cursor + Duration.ofDays(6)).format(sdf)}")
                    .body()
            val fieldOccupations = parseFieldOccupations(data)

            val googleCalendarService = Builder(httpTransport, gsonFactory, getCredentials(httpTransport))
                .setApplicationName("rus-fields")
                .build()

            val fieldIndexes = 0..FIELDS_MAX_INDEX
            val events = fieldIndexes.map { field ->
                googleCalendarService.events().list(fields[field])
                    .setMaxResults(100)
                    .setTimeMin(DateTime(cursor.atZone(zone).format(googleDateTimeFormatter)))
                    .setTimeMax(DateTime((cursor+Duration.ofDays(7)).atZone(zone).format(googleDateTimeFormatter)))
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute()
            }

            val foundEvents = fieldOccupations.mapNotNull { g ->
                val field = if (g.field.contains(FIELD_1_PATTERN.toRegex())) 0 else FIELDS_MAX_INDEX
                val startDateTime =
                    DateTime("${g.date.formatDate(nf)}T${g.startTime.formatTime(nf)}${g.date.timeZone()}")
                val endDateTime =
                    DateTime("${g.date.formatDate(nf)}T${g.endTime.formatTime(nf)}${g.date.timeZone()}")
                val found = events[field].items.find {
                    it.summary == g.summary() &&
                            it.description == "Field ${g.field}" &&
                            it.start.dateTime == startDateTime &&
                            it.end.dateTime == endDateTime
                }

                if (found === null) {
                    val event: Event = Event()
                        .setSummary(g.summary())
                        .setLocation(ADDRESS)
                        .setDescription("Field ${g.field}")
                        .setStart(startDateTime.toEventDateTime())
                        .setEnd(endDateTime.toEventDateTime())

                    googleCalendarService.events().insert(fields[field], event).execute()
                }
                found
            }.toSet()

            fieldIndexes.forEach { field ->
                (events[field].items.toSet() - foundEvents).forEach {
                    googleCalendarService.events().delete(fields[field], it.id).execute()
                }
            }
        }

    } catch (e: Exception) {
        println(e)
    }
}

private fun parseFieldOccupations(data: SportlinkData) = data.data.map { columns ->
    val (date, time, teamChampionship) = columns
    val (team, championship) = teamChampionship.split(" - ")
    val dateParts = date.split("[><]".toRegex())
    val category = try {
        Category.valueOf(team.split(" ")[0].lowercase(Locale.getDefault()))
    } catch (e: Exception) {
        Category.adult
    }
    val otherParts = columns[6].split("[><]".toRegex())

    FieldOccupation(
        category,
        championship,
        dateParts[2].replace("(.+)/(.+)/(.+)".toRegex(), "$3$2$1").toInt(),
        time.replace(":", "").toInt(),
        category.duration,
        team,
        otherParts[2] + " " + otherParts[8],
        dateParts.last()
    )
}

private fun FieldOccupation.summary() = "${category.fraction?.let { "$it: "} ?: ""} $team vs $otherTeam [$championship]"

private fun DateTime.toEventDateTime(): EventDateTime? =
    EventDateTime()
        .setDateTime(this)
        .setTimeZone("Europe/Brussels")

private fun Int.timeZone(): ZoneOffset? =
    LocalDate.of(this / 10000, this / 100 % 100, this % 100).atStartOfDay().atZone(zone).offset

private fun Int.formatTime(nf: DecimalFormat) =
    "${nf.format(this / 10000)}:${nf.format((this / 100) % 100)}:${nf.format(this % 100)}"

private fun Int.formatDate(nf: DecimalFormat) =
    "${this / 10000}-${nf.format((this / 100) % 100)}-${nf.format(this % 100)}"