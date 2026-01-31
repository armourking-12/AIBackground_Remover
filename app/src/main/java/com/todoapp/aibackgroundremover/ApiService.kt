package com.todoapp.aibackgroundremover

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

// 🔹 Gradio Image Object
data class GradioImage(
    @SerializedName("url") val url: String? = null,
    @SerializedName("path") val path: String? = null,
    @SerializedName("orig_name") val origName: String? = "image.png"
)

// 🔹 Request Payload
data class RequestPayload(
    @SerializedName("data") val data: List<GradioImage>
)

// 🔹 Response Payload
data class ResponsePayload(
    @SerializedName("event_id") val eventId: String? = null,
    @SerializedName("data") val data: List<GradioImage>? = null
)

// 🔹 Hugging Face Gradio API
interface HuggingFaceApi {

    @POST("/gradio_api/call/process_image")
    fun removeBackground(
        @Header("Authorization") token: String,
        @Body payload: RequestPayload
    ): Call<ResponsePayload>
}
