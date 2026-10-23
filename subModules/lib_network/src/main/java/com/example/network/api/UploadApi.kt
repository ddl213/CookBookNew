package com.example.network.api

import com.example.network.bean.UploadResponse
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * 图片上传接口。要求已登录；multipart 字段名固定为 file。
 */
interface UploadApi {

    @Multipart
    @POST("uploads")
    suspend fun upload(@Part file: MultipartBody.Part): UploadResponse
}
