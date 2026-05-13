interface AiModel {
    suspend fun chat(message: String): String
}
