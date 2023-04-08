import java.io.InputStream

class Credentials {
    val credentials: InputStream = Credentials::class.java.getResourceAsStream("/client_oauth_secret.json") ?:
        throw IllegalStateException("Credentials not found, please copy the OAUTH client secret into src/main/resources/client_oauth_secret.json")
}