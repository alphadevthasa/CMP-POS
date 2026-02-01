package com.pos.app.ui

import com.pos.app.domain.model.StoreSettings
import com.pos.app.print.PdfDoc
import com.pos.app.print.RLine
import com.pos.app.print.ReceiptFormatter
import com.pos.app.print.ReceiptPrinter

suspend fun printLines(
    printer: ReceiptPrinter,
    settings: StoreSettings,
    lines: List<RLine>,
): Result<Unit> =
    printer.printEscPos(
        address = settings.printerAddress,
        bytes = ReceiptFormatter.renderEscPos(lines, settings.printerWidthChars),
        text = ReceiptFormatter.renderText(lines, settings.printerWidthChars),
    )

suspend fun shareLines(
    printer: ReceiptPrinter,
    settings: StoreSettings,
    lines: List<RLine>,
    fileName: String,
): Result<Unit> {
    val text = ReceiptFormatter.renderText(lines, settings.printerWidthChars)
    return printer.sharePdf(PdfDoc(fileName, text))
}
