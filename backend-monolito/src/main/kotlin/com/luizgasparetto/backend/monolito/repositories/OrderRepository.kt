package com.luizgasparetto.backend.monolito.repositories

import com.luizgasparetto.backend.monolito.models.Order
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface OrderRepository : JpaRepository<Order, String>