package com.flowpay

import android.app.Application

class FlowPayApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize crypto engine, sync engine, etc.
        // In production: DI with Hilt/Koin
    }
}
