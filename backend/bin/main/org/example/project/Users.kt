package org.example.project

import UserCredentials
import io.ktor.http.invoke
import io.ktor.util.collections.ConcurrentMap
import io.ktor.utils.io.ConcurrentIOException
import kotlinx.serialization.json.Json
import org.mindrot.jbcrypt.BCrypt
import java.io.File

val userFile = File("users.json")

fun loadUsers(): MutableList<UserCredentials> =
    if (userFile.exists()) {
        Json.decodeFromString(userFile.readText())
    } else {
        mutableListOf()
    }

// Save users to file
fun saveUsers(users: List<UserCredentials>) {
    userFile.writeText(Json.encodeToString(users))
}

fun userExists(username: String): Boolean {
    return loadUsers().any { it.username == username }
}

fun createUser(username: String, password: String) {
    val list = if (userFile.exists()) {
        Json.decodeFromString<MutableList<UserCredentials>>(userFile.readText())
    } else {
        mutableListOf()
    }
    val hashedPassword = hashPassword(password)
    list.add(UserCredentials(username, hashedPassword))
    userFile.writeText(Json.encodeToString(list))
}

fun validateUser(username: String, password: String): Boolean {
    val users = loadUsers()
    val user = users.find { it.username == username } ?: return false
    return BCrypt.checkpw(password, user.password)
}

fun hashPassword(password: String):String = BCrypt.hashpw(password, BCrypt.gensalt())