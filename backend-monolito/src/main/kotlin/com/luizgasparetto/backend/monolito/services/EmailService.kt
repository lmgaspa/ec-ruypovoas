package com.luizgasparetto.backend.monolito.services

import com.luizgasparetto.backend.monolito.models.Order
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service

@Service
class EmailService(
    private val mailSender: JavaMailSender,
    private val bookService: BookService,
    @Value("\${email.author}") private val authorEmail: String
) {
    private val log = org.slf4j.LoggerFactory.getLogger(EmailService::class.java)

    // ==================== PIX OU CARTÃO PAGO ====================

    fun sendClientEmail(order: Order) {
        sendEmail(
            to = order.email,
            subject = "✅ Pagamento confirmado (#${order.id}) – Editora Nosso Lar",
            html = buildHtmlMessage(order, isAuthor = false, declined = false)
        )
    }

    fun sendAuthorEmail(order: Order) {
        sendEmail(
            to = authorEmail,
            subject = "📦 Novo pedido pago (#${order.id}) – Editora Nosso Lar",
            html = buildHtmlMessage(order, isAuthor = true, declined = false)
        )
    }

    // ==================== CARTÃO RECUSADO ====================

    fun sendClientCardDeclined(order: Order) {
        sendEmail(
            to = order.email,
            subject = "❌ Pagamento não aprovado (#${order.id}) – Editora Nosso Lar",
            html = buildHtmlMessage(order, isAuthor = false, declined = true)
        )
    }

    fun sendAuthorCardDeclined(order: Order) {
        sendEmail(
            to = authorEmail,
            subject = "⚠️ Pedido recusado (#${order.id}) – Editora Nosso Lar",
            html = buildHtmlMessage(order, isAuthor = true, declined = true)
        )
    }

    // ==================== CORE ====================

    private fun sendEmail(to: String, subject: String, html: String) {
        val msg = mailSender.createMimeMessage()
        val h = MimeMessageHelper(msg, true, "UTF-8")
        val from = System.getenv("MAIL_USERNAME") ?: authorEmail
        h.setFrom(from)
        h.setTo(to)
        h.setSubject(subject)
        h.setText(html, true)

        try {
            mailSender.send(msg)
            log.info("MAIL enviado OK -> $to")
        } catch (e: Exception) {
            log.error("MAIL ERRO para $to: {}", e.message, e)
        }
    }

    private fun buildHtmlMessage(order: Order, isAuthor: Boolean, declined: Boolean): String {
        val total = "R$ %.2f".format(order.total.toDouble())
        val shipping = if (order.shipping > java.math.BigDecimal.ZERO)
            "R$ %.2f".format(order.shipping.toDouble()) else "Grátis"

        val phoneDigits = onlyDigits(order.phone)
        val nationalPhone = normalizeBrPhone(phoneDigits)
        val maskedPhone = maskCelularBr(nationalPhone.ifEmpty { order.phone ?: "" })
        val waHref = if (nationalPhone.length == 11) "https://wa.me/55$nationalPhone"
        else "https://wa.me/55$phoneDigits"

        val itemsHtml = order.items.joinToString("") {
            val img = bookService.getImageUrl(it.bookId)
            """
            <tr>
              <td style="padding:12px 0;border-bottom:1px solid #eee;">
                <table cellpadding="0" cellspacing="0" style="border-collapse:collapse">
                  <tr>
                    <td><img src="$img" alt="${it.title}" width="70" style="border-radius:6px;vertical-align:middle;margin-right:12px"></td>
                    <td style="padding-left:12px">
                      <div style="font-weight:600">${it.title}</div>
                      <div style="color:#555;font-size:12px">${it.quantity}x – R$ ${"%.2f".format(it.price.toDouble())}</div>
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
            """.trimIndent()
        }

        val addressLine = buildString {
            append(order.address)
            order.number?.takeIf { it.isNotBlank() }?.let { append(", nº ").append(it) }
            order.complement?.takeIf { it.isNotBlank() }?.let { append(" – ").append(it) }
            order.district?.takeIf { it.isNotBlank() }?.let { append(" – ").append(it) }
            append(", ${order.city} - ${order.state}, CEP ${order.cep}")
        }

        val noteBlock = order.note?.takeIf { it.isNotBlank() }?.let {
            """
            <p style="margin:8px 0 0"><strong>📝 Observação do cliente:</strong><br>${escapeHtml(it)}</p>
            """.trimIndent()
        } ?: ""

        val paymentMethod = when (order.paymentMethod?.lowercase()) {
            "pix" -> "Pix"
            "card" -> "Cartão de Crédito"
            else -> "Pagamento"
        }

        // 🔹 parcelas (só cartão e só se > 1)
        val installmentsInfo =
            if (!declined && order.paymentMethod.equals("card", ignoreCase = true)) {
                if ((order.installments ?: 1) > 1) {
                    val perInstallment = order.total.divide(
                        java.math.BigDecimal(order.installments ?: 1),
                        2, java.math.RoundingMode.HALF_UP
                    )
                    "<p><strong>💳 Parcelado em:</strong> ${order.installments}x de R$ %.2f sem juros</p>"
                        .format(perInstallment.toDouble())
                } else {
                    "<p><strong>💳 Pagamento à vista no cartão.</strong></p>"
                }
            } else ""

        // ==================== MENSAGENS ====================

        val headerClient = if (declined) {
            """
            <p>Olá, <strong>${order.firstName} ${order.lastName}</strong>.</p>
            <p>❌ Seu pagamento via <strong>$paymentMethod</strong> não foi aprovado.</p>
            <p>Tente novamente com outro cartão ou escolha Pix.</p>
            <p>📍 Endereço de recebimento: $addressLine</p>
            $noteBlock
            """.trimIndent()
        } else {
            """
            <p>Olá, <strong>${order.firstName} ${order.lastName}</strong>!</p>
            <p>🎉 Recebemos o seu pagamento via <strong>$paymentMethod</strong>. Seu pedido foi confirmado.</p>
            <p>📍 Endereço de recebimento: $addressLine</p>
            $noteBlock
            """.trimIndent()
        }

        val headerAuthor = if (declined) {
            """
            <p><strong>⚠️ Pedido recusado</strong> no site.</p>
            <p>👤 Cliente: ${order.firstName} ${order.lastName}</p>
            <p>✉️ Email: ${order.email}</p>
            <p>📱 WhatsApp: <a href="$waHref">$maskedPhone</a></p>
            <p>📍 Endereço: $addressLine</p>
            <p><strong>Pagamento:</strong> $paymentMethod (recusado)</p>
            $noteBlock
            """.trimIndent()
        } else {
            """
            <p><strong>📦 Novo pedido pago</strong> no site.</p>
            <p>👤 Cliente: ${order.firstName} ${order.lastName}</p>
            <p>✉️ Email: ${order.email}</p>
            <p>📱 WhatsApp: <a href="$waHref">$maskedPhone</a></p>
            <p>📍 Endereço: $addressLine</p>
            <p><strong>Pagamento:</strong> $paymentMethod</p>
            $noteBlock
            """.trimIndent()
        }

        val who = if (isAuthor) headerAuthor else headerClient

        val txidLine =
            if (!declined && order.paymentMethod.equals("pix", ignoreCase = true))
                order.txid?.let { "<p><strong>🔑 TXID Pix:</strong> $it</p>" }
            else null

        val contactBlock = if (!isAuthor) """
            <p style="margin:16px 0 0;color:#555">
              Em caso de dúvida, entre em contato com <strong>Editora Nosso Lar</strong><br>
              ✉️ Email: <a href="mailto:luhmgasparetto@gmail.com">luhmgasparetto@gmail.com</a> · 
              📱 WhatsApp: <a href="https://wa.me/5571994105740">(71) 99410-5740</a>
            </p>
        """.trimIndent() else ""

        return """
        <html>
        <body style="font-family:Arial,Helvetica,sans-serif;background:#f6f7f9;padding:24px">
          <div style="max-width:640px;margin:0 auto;background:#fff;border:1px solid #eee;border-radius:10px;overflow:hidden">
            <div style="background:#111;color:#fff;padding:16px 20px">
              <strong style="font-size:16px">📚 Editora Nosso Lar – Ecommerce</strong>
            </div>
            <div style="padding:20px">
              $who

              <p><strong>🧾 Nº do pedido:</strong> #${order.id}</p>
              ${txidLine ?: ""}

              ${if (!declined) """
              <h3 style="font-size:15px;margin:16px 0 8px">🛒 Itens</h3>
              <table width="100%">$itemsHtml</table>

              <p><strong>🚚 Frete:</strong> $shipping<br>
                 <strong>💰 Total:</strong> $total<br>
                 <strong>💳 Pagamento:</strong> $paymentMethod</p>
              $installmentsInfo
              """ else ""}

              ${if (!isAuthor) "<p>${if (declined) "💳 Tente novamente ou escolha Pix" else "💛 Obrigado por comprar com a gente!"}</p>" else ""}

              $contactBlock
            </div>
            <div style="background:#fafafa;color:#888;padding:12px 20px;text-align:center;font-size:12px">
              © ${java.time.Year.now()} Editora Nosso Lar. Todos os direitos reservados.
            </div>
          </div>
        </body>
        </html>
        """.trimIndent()
    }

    // Helpers
    private fun onlyDigits(s: String?): String = s?.filter { it.isDigit() } ?: ""
    private fun normalizeBrPhone(digits: String): String =
        when {
            digits.length >= 13 && digits.startsWith("55") -> digits.takeLast(11)
            digits.length >= 11 -> digits.takeLast(11)
            else -> digits
        }
    private fun maskCelularBr(src: String): String {
        val d = onlyDigits(src).let { normalizeBrPhone(it) }
        return when {
            d.length <= 2 -> "(${d}"
            d.length <= 7 -> "(${d.substring(0, 2)})${d.substring(2)}"
            d.length <= 11 -> "(${d.substring(0, 2)})${d.substring(2, 7)}-${d.substring(7)}"
            else -> "(${d.substring(0, 2)})${d.substring(2, 7)}-${d.substring(7, 11)}"
        }
    }
    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
