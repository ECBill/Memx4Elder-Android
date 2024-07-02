package life.memx.chat

data class CheckIDResponse(
    val user_id: String,
    val authority: String,
    val status: String
)