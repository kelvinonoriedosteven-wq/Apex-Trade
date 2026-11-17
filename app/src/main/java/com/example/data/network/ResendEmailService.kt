package com.example.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class ResendEmailPayload(
    val from: String,
    val to: List<String>,
    val subject: String,
    val html: String,
    val text: String
)

@JsonClass(generateAdapter = true)
data class ResendEmailResponse(
    val id: String? = null,
    val message: String? = null,
    @Json(name = "statusCode") val statusCode: Int? = null
)

interface ResendEmailApi {
    @POST("emails")
    suspend fun sendEmail(
        @Header("Authorization") bearerToken: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body payload: ResendEmailPayload
    ): Response<ResendEmailResponse>
}

object ResendNetworkClient {
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val moshi: Moshi by lazy {
        Moshi.Builder().build()
    }

    val api: ResendEmailApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.resend.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ResendEmailApi::class.java)
    }
}
