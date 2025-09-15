package com.luizgasparetto.backend.monolito.repositories

import com.luizgasparetto.backend.monolito.models.Order
import com.luizgasparetto.backend.monolito.models.OrderStatus
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.OffsetDateTime

interface OrderRepository : JpaRepository<Order, Long> {

    fun findByTxid(txid: String): Order?

    @EntityGraph(attributePaths = ["items"])
    fun findWithItemsByTxid(txid: String): Order?

    // 🔹 Novo método para buscar pedidos por chargeId (cartão) // ALTERADO
    @EntityGraph(attributePaths = ["items"])
    fun findWithItemsByChargeId(chargeId: String): Order? // ALTERADO

    @EntityGraph(attributePaths = ["items"])
    @Query(
        """
        select distinct o
          from Order o
          left join o.items it
         where o.paid = false
           and o.status = :status
           and o.reserveExpiresAt is not null
           and o.reserveExpiresAt < :now
    """
    )
    fun findExpiredReservations(
        @Param("now") now: OffsetDateTime,
        @Param("status") status: OrderStatus
    ): List<Order>
}
