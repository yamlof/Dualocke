package org.example.project

import LoginRequest
import LoginResponse
import UserCredentials
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import com.auth0.jwt.*
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import kotlinx.serialization.json.Json
import java.util.Date

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json{
            ignoreUnknownKeys = true
            prettyPrint = true
            isLenient = true
        })
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
            val request = call.receive<UserCredentials>()
            if (validateUser(request.username, request.password)) {
                val token = generateToken(request.username)
                call.respond(LoginResponse(token))
            } else {
                call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
            }
        }

        authenticate("auth-jwt"){
            get("/profile") {
                val principal = call.principal<JWTPrincipal>()
                val username = principal!!.payload.getClaim("username").asString()
                call.respondText("Hello, $username! this is a protected route")
            }
        }

        post("/register") {
            val request = call.receive<UserCredentials>()
            if (userExists(request.username)) {
                call.respond(HttpStatusCode.Conflict,"User already exists")
            }else {
                createUser(request.username,request.password)
                call.respond(HttpStatusCode.OK,"User created successfully")
            }
        }
    }


    routing {
        get("/") {
            call.respondText("Ktor: ${Greeting().greet()}")
        }
    }

}