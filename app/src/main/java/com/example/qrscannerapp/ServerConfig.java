package com.example.qrscannerapp;

public final class ServerConfig {
    // Адрес задаётся в app/build.gradle: debug — тестовый сервер artest, release — боевой сервер
    public static final String BASE_URL = BuildConfig.SERVER_URL;

    private ServerConfig() {
    }
}
