package org.example.project

import LoginRequest
import LoginResponse
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import com.auth0.jwt.*
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import java.util.Date

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }

    val jwtSecret = "secret"
    val jwtIssuer = "ktor-sample"
    val jwtAudience = "ktor-audience"
    val jwtValidity = 60000L

    fun generateToken(username:String) : String{
        return JWT.create()
            .withSubject("Authentication")
            .withIssuer(jwtIssuer)
            .withAudience(jwtAudience)
            .withClaim("username",username)
            .withExpiresAt(Date(System.currentTimeMillis() + jwtValidity))
            .sign(Algorithm.HMAC256(jwtSecret))
    }

    install(Authentication) {
        jwt("auth-jwt") {
            realm = "ktor sample app"
            verifier(
                JWT
                    .require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtIssuer)
                    .build()
            )
            validate{ credential ->
                if (credential.payload.getClaim("username").asString() != null) JWTPrincipal(credential.payload)
                else null
            }
        }
    }

    routing {
        post("/login") {
            val login = call.receive<LoginRequest>()
            if (login.username == "user" && login.password == "password") {
                val token = generateToken(login.username)
                call.respond(LoginResponse(token))
            } else {
                call.respondText("Invalid credentials",status = Unauthorized)
            }
        }

        authenticate("auth-jwt"){
            get("/profile") {
                val principal = call.principal<JWTPrincipal>()
                val username = principal!!.payload.getClaim("username").asString()
                call.respondText("Hello, $username! this is a protected route")
            }
        }
    }


    routing {
        get("/") {
            call.respondText("Ktor: ${Greeting().greet()}")
        }
    }

}