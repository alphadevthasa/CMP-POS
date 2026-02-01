package com.pos.app.data.repo

import com.pos.app.db.PosDatabase
import com.pos.app.domain.model.Category
import com.pos.app.domain.model.Product
import com.pos.app.domain.repository.ProductRepository

class ProductRepositoryImpl(private val db: PosDatabase) : ProductRepository {

    override fun categories(): List<Category> =
        db.posQueries.selectAllCategories().executeAsList().map {
            Category(id = it.id, name = it.name)
        }

    override fun products(): List<Product> =
        db.posQueries.selectProducts().executeAsList().map {
            Product(
                id = it.id,
                name = it.name,
                sku = it.sku,
                price = it.price,
                categoryId = it.category_id,
                active = it.active != 0L,
            )
        }

    /** ponytail: seed only when empty, single local device — no conflict handling needed. */
    override fun seedIfEmpty() {
        if (db.posQueries.countProducts().executeAsOne() > 0L) return

        val food = addCategory("Makanan")
        val drink = addCategory("Minuman")
        val snack = addCategory("Snack")
        val coffee = addCategory("Kopi")

        addProduct("Nasi Goreng", "FD-001", 25_000, food)
        addProduct("Mie Ayam", "FD-002", 22_000, food)
        addProduct("Ayam Geprek", "FD-003", 27_000, food)
        addProduct("Nasi Uduk", "FD-004", 20_000, food)
        addProduct("Es Teh Manis", "DR-001", 8_000, drink)
        addProduct("Es Jeruk", "DR-002", 10_000, drink)
        addProduct("Air Mineral", "DR-003", 5_000, drink)
        addProduct("Teh Botol", "DR-004", 6_000, drink)
        addProduct("Kopi Susu", "CF-001", 18_000, coffee)
        addProduct("Americano", "CF-002", 20_000, coffee)
        addProduct("Cappuccino", "CF-003", 22_000, coffee)
        addProduct("Keripik Singkong", "SN-001", 12_000, snack)
        addProduct("Pisang Goreng", "SN-002", 10_000, snack)
        addProduct("Donat Gula", "SN-003", 9_000, snack)
    }

    override fun addProduct(name: String, sku: String, price: Long, categoryId: Long): Long {
        db.posQueries.insertProduct(name, sku, price, categoryId, 1L)
        return db.posQueries.lastInsertRowId().executeAsOne()
    }

    override fun addCategory(name: String): Long {
        db.posQueries.insertCategory(name)
        return db.posQueries.lastInsertRowId().executeAsOne()
    }
}
