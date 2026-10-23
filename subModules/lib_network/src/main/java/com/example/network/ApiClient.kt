package com.example.network

import com.example.network.api.AuthApi
import com.example.network.api.FriendApi
import com.example.network.api.RecipeApi
import com.example.network.api.SocialApi
import com.example.network.api.UploadApi
import com.example.network.api.UserApi
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

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(NetworkConfig.baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val authApi: AuthApi by lazy { retrofit.create(AuthApi::class.java) }

    val userApi: UserApi by lazy { retrofit.create(UserApi::class.java) }

    val friendApi: FriendApi by lazy { retrofit.create(FriendApi::class.java) }

    val recipeApi: RecipeApi by lazy { retrofit.create(RecipeApi::class.java) }

    val uploadApi: UploadApi by lazy { retrofit.create(UploadApi::class.java) }

    val socialApi: SocialApi by lazy { retrofit.create(SocialApi::class.java) }
}
