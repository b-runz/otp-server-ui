package one.brj.bikebus.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import one.brj.bikebus.BuildConfig
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object NetworkModule {
    private val json = Json { ignoreUnknownKeys = true }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val placesApi: PlacesApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://places.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PlacesApi::class.java)
    }

    val otpServerApi: OtpServerApi by lazy {
        OtpServerApi(
            client = okHttpClient,
            baseUrl = BuildConfig.OTP_SERVER_BASE_URL,
            authToken = BuildConfig.OTP_AUTH_TOKEN,
        )
    }
}
