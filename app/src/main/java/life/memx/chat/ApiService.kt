package life.memx.chat

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("/check_id/")
    fun checkUserId(@Body request: CheckIDRequest): Call<CheckIDResponse>
}