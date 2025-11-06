import kotlinx.serialization.Serializable

expect fun saveToken(token:String)

expect fun loadToken(): String?

expect suspend fun login(username:String,password: String) :LoginResponse