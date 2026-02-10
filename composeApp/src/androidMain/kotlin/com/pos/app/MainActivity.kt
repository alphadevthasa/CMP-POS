package com.pos.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.pos.app.data.AppContainer
import com.pos.app.data.db.DatabaseDriverFactory
import com.pos.app.print.AndroidReceiptPrinter
import com.pos.app.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        val container = AppContainer(
            driverFactory = DatabaseDriverFactory(applicationContext),
            printer = AndroidReceiptPrinter(applicationContext),
        )
        setContent { App(container) }
    }
}
