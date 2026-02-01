package com.pos.app.data

import com.pos.app.data.db.DatabaseDriverFactory
import com.pos.app.data.repo.OrderRepositoryImpl
import com.pos.app.data.repo.ProductRepositoryImpl
import com.pos.app.data.repo.SettingsRepositoryImpl
import com.pos.app.db.PosDatabase
import com.pos.app.domain.repository.OrderRepository
import com.pos.app.domain.repository.ProductRepository
import com.pos.app.domain.repository.SettingsRepository
import com.pos.app.print.ReceiptPrinter

class AppContainer(
    driverFactory: DatabaseDriverFactory,
    val printer: ReceiptPrinter,
) {
    val db: PosDatabase = PosDatabase(driverFactory.createDriver())
    val products: ProductRepository = ProductRepositoryImpl(db)
    val orders: OrderRepository = OrderRepositoryImpl(db)
    val settings: SettingsRepository = SettingsRepositoryImpl(db)

    init {
        products.seedIfEmpty()
    }
}
