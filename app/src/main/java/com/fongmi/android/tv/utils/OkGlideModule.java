package com.ikanbot.tv.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;
import com.github.catvod.net.OkHttp;

import java.io.InputStream;

import okhttp3.OkHttpClient;
import okhttp3.Request;

@GlideModule
public class OkGlideModule extends AppGlideModule {

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static final OkHttpClient CLIENT = OkHttp.client().newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(chain -> {
                Request request = chain.request();
                if (request.url().host().contains("doubanio.com")) {
                    request = request.newBuilder()
                            .header("Referer", "https://movie.douban.com/")
                            .header("User-Agent", UA)
                            .build();
                }
                return chain.proceed(request);
            })
            .build();

    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        builder.setLogLevel(Log.ERROR);
    }

    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, Registry registry) {
        registry.replace(GlideUrl.class, InputStream.class, new OkHttpUrlLoader.Factory(CLIENT));
    }
}