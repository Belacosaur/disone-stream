package com.disone.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.disone.core.api.AddonApi
import com.disone.core.api.AuthApi
import com.disone.core.api.StreamApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @Named("apiBaseUrl")
    fun provideApiBaseUrl(): String = "https://disone-api.up.railway.app/"

    @Provides
    @Singleton
    @Named("authBaseUrl")
    fun provideAuthBaseUrl(): String = "https://disone-api.up.railway.app/"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        @Named("apiBaseUrl") baseUrl: String
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(
        okHttpClient: OkHttpClient,
        @Named("authBaseUrl") authBaseUrl: String
    ): AuthApi = Retrofit.Builder()
        .baseUrl(authBaseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideStreamApi(retrofit: Retrofit): StreamApi = retrofit.create(StreamApi::class.java)

    @Provides
    @Singleton
    fun provideAddonApi(
        okHttpClient: OkHttpClient,
        @Named("apiBaseUrl") baseUrl: String
    ): AddonApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AddonApi::class.java)

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()
}
