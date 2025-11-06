import io.ktor.serialization.kotlinx.json.*
import io.ktor.client.plugins.contentnegotiation.*

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import java.io.File
import java.net.http.HttpResponse

private val client = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            isLenient = true
        })
    }
}

private val tokenFile = File("auth_token.txt")

actual fun saveToken(token:String){
    tokenFile.writeText(token)
}

actual fun loadToken(): String? =
    if (tokenFile.exists()) tokenFile.readText() else null

actual suspend fun login(username:String,password:String) : LoginResponse {
    return client.post("http://localhost:8080/login"){
        setBody(LoginRequest(username,password))
        contentType(ContentType.Application.Json)
    }.body()
}

suspend fun register(username: String, password: String): String {
    val response = client.post("http://localhost:8080/register") {
        contentType(ContentType.Application.Json)
        setBody(UserCredentials(username, password))
    }

    if (response.status == HttpStatusCode.OK) {
        return response.bodyAsText()
    } else {
        throw Exception("Registration failed: ${response.status}")
    }
}