package com.example

import android.app.Application

// Note: App Check is strictly initialized inside MainActivity.onCreate(savedInstanceState)
// with the launch Intent so debug tokens from Intent extras are preserved.
class NovaApplication : Application() {
    val container: NovaAppContainer by lazy {
        NovaAppContainer(this)
    }
}
