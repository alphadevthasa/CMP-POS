package com.pos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pos.app.data.AppContainer
import com.pos.app.data.db.DatabaseDriverFactory
import com.pos.app.print.AndroidReceiptPrinter
import com.pos.app.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = AppContainer(
            driverFactory = DatabaseDriverFactory(applicationContext),
            printer = AndroidReceiptPrinter(applicationContext),
        )
        setContent { App(container) }
    }
}
