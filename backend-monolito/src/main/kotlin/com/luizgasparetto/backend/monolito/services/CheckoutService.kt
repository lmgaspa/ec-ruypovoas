package com.luizgasparetto.backend.monolito.services

import com.luizgasparetto.backend.monolito.dto.CheckoutRequest
import com.luizgasparetto.backend.monolito.dto.CheckoutResponse
import com.luizgasparetto.backend.monolito.models.Order
import com.luizgasparetto.backend.monolito.models.OrderItem
import com.luizgasparetto.backend.monolito.repositories.BookRepository
import com.luizgasparetto.backend.monolito.repositories.OrderRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class CheckoutService(
    private val bookRepository: BookRepository,
    private val orderRepository: OrderRepository,
    private val emailService: EmailService,
    private val pixService: PixService
) {

    @Transactional
    fun processCheckout(request: CheckoutRequest): CheckoutResponse {
        val booksMap = bookRepository.findAllById(request.cartItems.map { it.id })
            .associateBy { it.id }

        request.cartItems.forEach { item ->
            val book = booksMap[item.id] ?: throw IllegalArgumentException("Livro ${item.id} não encontrado.")
            if (book.stock < item.quantity) {
                throw IllegalArgumentException("Estoque insuficiente para o livro: ${book.title}")
            }
        }

        request.cartItems.forEach { item ->
            val book = booksMap[item.id]!!
            book.stock -= item.quantity
            bookRepository.save(book)
        }

        val pixResponse = pixService.createPixPayment(
            request.firstName,
            request.email,
            request.total + request.shipping
        )

        val paymentId = pixResponse["id"]?.asLong()
            ?: throw IllegalStateException("ID do pagamento não encontrado.")
        val qrCode = pixResponse["point_of_interaction"]?.get("transaction_data")?.get("qr_code")?.asText()
            ?: throw IllegalStateException("QR Code não encontrado.")
        val qrCodeBase64 = pixResponse["point_of_interaction"]?.get("transaction_data")?.get("qr_code_base64")?.asText()
            ?: throw IllegalStateException("QR Code Base64 não encontrado.")

        val savedOrder = orderRepository.save(
            Order(
                firstName = request.firstName,
                lastName = request.lastName,
                email = request.email,
                address = request.address,
                city = request.city,
                state = request.state,
                cep = request.cep,
                total = request.total,
                shipping = request.shipping,
                paymentId = paymentId,
                qrCode = qrCode,
                qrCodeBase64 = qrCodeBase64
            )
        )

        val orderItems = request.cartItems.map {
            OrderItem(
                bookId = it.id,
                title = it.title,
                quantity = it.quantity,
                price = it.price,
                order = savedOrder
            )
        }

        savedOrder.items.addAll(orderItems)
        val finalOrder = orderRepository.save(savedOrder)


        emailService.sendClientEmail(finalOrder)
        emailService.sendAuthorEmail(finalOrder)

        return CheckoutResponse(
            message = "Pedido realizado com sucesso!",
            orderId = finalOrder.id.toString(),
            qrCode = finalOrder.qrCode,
            qrCodeBase64 = finalOrder.qrCodeBase64
        )
    }
}
