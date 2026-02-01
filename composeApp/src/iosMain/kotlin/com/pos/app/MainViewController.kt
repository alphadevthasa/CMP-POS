package com.pos.app

import androidx.compose.ui.window.ComposeUIViewController
import com.pos.app.data.AppContainer
import com.pos.app.data.db.DatabaseDriverFactory
import com.pos.app.print.IosReceiptPrinter
import com.pos.app.ui.App
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    val container = AppContainer(DatabaseDriverFactory(), IosReceiptPrinter())
    return ComposeUIViewController { App(container) }
}
