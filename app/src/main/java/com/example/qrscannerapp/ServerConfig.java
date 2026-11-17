package com.example.qrscannerapp;

public final class ServerConfig {
    // Адрес задаётся в app/build.gradle (SERVER_URL); сейчас в обеих сборках — тестовый сайт artest
    public static final String BASE_URL = BuildConfig.SERVER_URL;

    private ServerConfig() {
    }
}
