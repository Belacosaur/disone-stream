package com.disone.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.disone.BuildConfig
import com.disone.core.api.AddonApi
import com.disone.core.api.AuthApi
import com.disone.core.api.CommentsApi
import com.disone.core.api.ProfileApi
import com.disone.core.api.RatingsApi
import com.disone.core.api.ReviewsApi
import com.disone.core.api.LibraryApi
import com.disone.core.api.StreamApi
import com.disone.core.api.SubscriptionApi
import com.disone.core.api.AccessApi
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
    fun provideApiBaseUrl(): String = BuildConfig.API_BASE_URL

    @Provides
    @Singleton
    @Named("authBaseUrl")
    fun provideAuthBaseUrl(): String = BuildConfig.API_BASE_URL

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS else HttpLoggingInterceptor.Level.NONE
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
    fun provideProfileApi(retrofit: Retrofit): ProfileApi = retrofit.create(ProfileApi::class.java)

    @Provides
    @Singleton
    fun provideRatingsApi(retrofit: Retrofit): RatingsApi = retrofit.create(RatingsApi::class.java)

    @Provides
    @Singleton
    fun provideReviewsApi(retrofit: Retrofit): ReviewsApi = retrofit.create(ReviewsApi::class.java)

    @Provides
    @Singleton
    fun provideCommentsApi(retrofit: Retrofit): CommentsApi = retrofit.create(CommentsApi::class.java)

    @Provides
    @Singleton
    fun provideSubscriptionApi(retrofit: Retrofit): SubscriptionApi = retrofit.create(SubscriptionApi::class.java)

    @Provides
    @Singleton
    fun provideAccessApi(retrofit: Retrofit): AccessApi = retrofit.create(AccessApi::class.java)

    @Provides
    @Singleton
    fun provideLibraryApi(retrofit: Retrofit): LibraryApi = retrofit.create(LibraryApi::class.java)

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()
}
