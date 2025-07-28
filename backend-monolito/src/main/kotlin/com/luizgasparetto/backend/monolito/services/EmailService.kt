package com.luizgasparetto.backend.monolito.services

import com.luizgasparetto.backend.monolito.models.Order
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service

@Service
class EmailService(private val mailSender: JavaMailSender) {

    private val authorEmail = "ag1957@gmail.com"

    fun sendClientEmail(order: Order) {
        val message = SimpleMailMessage()
        message.setTo(order.email)
        message.subject = "Confirmação do seu pedido – Agenor Gasparetto"
        message.text = buildClientMessage(order)
        mailSender.send(message)
    }

    fun sendAuthorEmail(order: Order) {
        val message = SimpleMailMessage()
        message.setTo(authorEmail)
        message.subject = "Novo pedido recebido"
        message.text = buildAuthorMessage(order)
        mailSender.send(message)
    }

    private fun buildClientMessage(order: Order): String {
        val total = "R$ %.2f".format(order.total)
        val shippingLine = if (order.shipping > 0.0) "Frete: R$ %.2f\n".format(order.shipping) else ""

        return """
            Olá ${order.firstName},

            Recebemos seu pedido! Aqui estão os detalhes:

            ${order.items.joinToString("\n") {
            "- ${it.title} (${it.quantity}x) – R$%.2f".format(it.price)
        }}

            $shippingLine
            Total: $total
            Pagamento: Pix

            Obrigado por comprar conosco!
        """.trimIndent()
    }

    private fun buildAuthorMessage(order: Order): String {
        val total = "R$ %.2f".format(order.total)
        val shippingLine = if (order.shipping > 0.0) "Frete: R$ %.2f\n".format(order.shipping) else ""

        return """
            Novo pedido:

            Cliente: ${order.firstName} ${order.lastName}
            Email: ${order.email}
            Endereço: ${order.address}, ${order.city} - ${order.state}, CEP ${order.cep}

            Itens:
            ${order.items.joinToString("\n") {
            "- ${it.title} (${it.quantity}x) – R$%.2f".format(it.price)
        }}

            $shippingLine
            Total: $total
        """.trimIndent()
    }
}
