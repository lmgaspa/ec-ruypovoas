package com.luizgasparetto.backend.monolito.repositories

import com.luizgasparetto.backend.monolito.models.Order
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface OrderRepository : JpaRepository<Order, Long> {

    fun findByTxid(txid: String): Order?

    @EntityGraph(attributePaths = ["items"])
    fun findWithItemsByTxid(txid: String): Order?
}
