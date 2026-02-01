package com.pos.app.print

import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIMarkupTextPrintFormatter
import platform.UIKit.UIPrintInteractionController
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

/**
 * iOS printing via the OS:
 *  - [printEscPos] sends the rendered text to AirPrint (works with AirPrint-capable
 *    receipt printers and any shared printer).
 *  - [sharePdf] opens the system share sheet (Save to Files, AirPrint, mail, ...).
 * ponytail: raw ESC/POS over CoreBluetooth BLE is not implemented; AirPrint/share covers
 * iPad receipt printing. Add a CBCentralManager writer if a BLE-only printer is required.
 */
@OptIn(ExperimentalForeignApi::class)
class IosReceiptPrinter : ReceiptPrinter {

    override suspend fun printEscPos(address: String, bytes: ByteArray, text: String): Result<Unit> =
        runCatching {
            val controller = UIPrintInteractionController.sharedPrintController()
            controller.printFormatter = UIMarkupTextPrintFormatter(markupText = text)
            val root = rootViewController() ?: error("Tidak ada view controller aktif")
            controller.presentFromRect(
                rect = root.view.bounds,
                inView = root.view,
                animated = true,
                completionHandler = null,
            )
            Unit
        }

    override suspend fun sharePdf(doc: PdfDoc): Result<Unit> = runCatching {
        val root = rootViewController() ?: error("Tidak ada view controller aktif")
        val activity = UIActivityViewController(
            activityItems = listOf(doc.text),
            applicationActivities = null,
        )
        activity.popoverPresentationController?.let { popover ->
            popover.sourceView = root.view
            popover.sourceRect = root.view.bounds
        }
        root.presentViewController(activity, animated = true, completion = null)
        Unit
    }

    private fun rootViewController(): UIViewController? =
        UIApplication.sharedApplication.keyWindow?.rootViewController
}
