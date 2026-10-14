package com.example.util

import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object ArtfluxImageLoader {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: Context): ImageLoader {
        return instance ?: synchronized(this) {
            instance ?: ImageLoader.Builder(context.applicationContext)
                .okHttpClient {
                    OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(20, TimeUnit.SECONDS)
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .addInterceptor { chain ->
                            val originalRequest = chain.request()
                            val requestBuilder = originalRequest.newBuilder()
                            val headers = ArtfluxNetwork.getHeadersForUrl(originalRequest.url.toString())
                            for ((k, v) in headers) {
                                requestBuilder.header(k, v)
                            }
                            val response = chain.proceed(requestBuilder.build())
                            if (!response.isSuccessful) {
                                throw java.io.IOException("HTTP ${response.code}: ${response.message.ifBlank { "Request failed" }}")
                            }
                            val contentType = response.header("Content-Type")?.lowercase().orEmpty()
                            if (contentType.isNotBlank() && (contentType.contains("text/html") || contentType.contains("application/json") || contentType.contains("text/plain"))) {
                                throw java.io.IOException("Server returned '$contentType' instead of valid media (HTTP ${response.code})")
                            }
                            response
                        }
                        .build()
                }
                .components {
                    if (Build.VERSION.SDK_INT >= 28) {
                        add(ImageDecoderDecoder.Factory())
                    } else {
                        add(GifDecoder.Factory())
                    }
                }
                .memoryCache {
                    MemoryCache.Builder(context.applicationContext)
                        .maxSizePercent(0.25)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(context.applicationContext.cacheDir.resolve("image_cache"))
                        .maxSizeBytes(150L * 1024 * 1024) // 150MB
                        .build()
                }
                .crossfade(true)
                .build().also { instance = it }
        }
    }
}
