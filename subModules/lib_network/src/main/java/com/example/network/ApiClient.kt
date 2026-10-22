package com.example.network

import com.example.network.api.AuthApi
import com.example.network.session.SessionStore
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 全局网络客户端。
 *
 * 只创建一次 Retrofit / OkHttp 实例；NetworkConfig.baseUrl 需在首次访问前设置。
 * 未接入 HttpLoggingInterceptor，避免请求体、密码和 token 进入日志。
 */
object ApiClient {

    private const val TIMEOUT_SECONDS = 15L

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val token = SessionStore.token()
                val request = if (token.isNullOrBlank()) {
                    chain.request()
                } else {
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                }
                chain.proceed(request)
            }
            .build()
    }

    val authApi: AuthApi by lazy {
        Retrofit.Builder()
            .baseUrl(NetworkConfig.baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)
    }
}
