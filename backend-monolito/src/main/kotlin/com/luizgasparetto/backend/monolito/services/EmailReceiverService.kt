package com.luizgasparetto.backend.monolito.services

import com.luizgasparetto.backend.monolito.models.Order
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.slf4j.LoggerFactory
import jakarta.mail.internet.MimeMessage

/**
 * Envia e-mail para o AUTOR (quem recebe o aviso de novo pedido pago).
 */
@Service
class EmailReceiverService(
    private val mailSender: JavaMailSender,
    private val bookService: BookService,
    @Value("\${email.author}") private val authorEmail: String
) {
    private val log = LoggerFactory.getLogger(EmailReceiverService::class.java)

    fun sendAuthorEmail(order: Order) {
        val msg: MimeMessage = mailSender.createMimeMessage()
        val h = MimeMessageHelper(msg, true, "UTF-8")
        val from = System.getenv("MAIL_USERNAME") ?: authorEmail
        h.setFrom(from)
        h.setTo(authorEmail)
        h.setSubject("Novo pedido pago (#${order.id}) – Agenor Gasparetto")
        h.setText(buildHtmlMessage(order, isAuthor = true), true)
        try {
            mailSender.send(msg)
            log.info("MAIL autor OK -> {}", authorEmail)
        } catch (e: Exception) {
            log.error("MAIL autor ERRO: {}", e.message, e)
        }
    }

    private fun buildHtmlMessage(order: Order, isAuthor: Boolean): String {
        val total = "R$ %.2f".format(order.total.toDouble())
        val shipping = if (order.shipping > java.math.BigDecimal.ZERO)
            "R$ %.2f".format(order.shipping.toDouble()) else "Grátis"

        val phoneDigits = onlyDigits(order.phone)
        val nationalPhone = normalizeBrPhone(phoneDigits)
        val maskedPhone = maskCelularBr(nationalPhone.ifEmpty { order.phone })
        val waHref = nationalPhone.takeIf { it.length == 11 }
            ?.let { "https://wa.me/55$it" }
            ?: "https://wa.me/55$phoneDigits"

        val itemsHtml = order.items.joinToString("") {
            val img = bookService.getImageUrl(it.bookId)
            """
            <tr>
              <td style="padding:12px 0;border-bottom:1px solid #eee;">
                <table cellpadding="0" cellspacing="0" style="border-collapse:collapse">
                  <tr>
                    <td>
                      <img src="$img" alt="${it.title}" width="70" style="border-radius:6px;vertical-align:middle;margin-right:12px">
                    </td>
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
            order.number.takeIf { it.isNotBlank() }?.let { append(", nº ").append(it) }
            order.complement?.takeIf { it.isNotBlank() }?.let { append(" – ").append(it) }
            order.district.takeIf { it.isNotBlank() }?.let { append(" – ").append(it) }
            append(", ${order.city} - ${order.state}, CEP ${order.cep}")
        }

        val noteBlock = order.note?.takeIf { it.isNotBlank() }?.let {
            """
            <p style="margin:8px 0 0"><strong>Observação do cliente:</strong><br>${escapeHtml(it)}</p>
            """.trimIndent()
        } ?: ""

        // 🔴 CPF REMOVIDO: não exibimos mais no e-mail do autor

        val headerClient = """
            <p style="margin:0 0 12px">Olá, <strong>${order.firstName} ${order.lastName}</strong>!</p>
            <p style="margin:0 0 16px">Recebemos o seu pagamento via Pix. Seu pedido foi confirmado 🎉</p>
            <p style="margin:0 0 4px">Endereço de recebimento: $addressLine</p>
        """.trimIndent()

        val headerAuthor = """
            <p style="margin:0 0 12px"><strong>Novo pedido pago</strong> no site.</p>
            <p style="margin:0 0 4px">Cliente: ${order.firstName} ${order.lastName}</p>
            <p style="margin:0 0 4px">Email: ${order.email}</p>
            <p style="margin:0 0 4px">WhatsApp: <a href="$waHref">$maskedPhone</a></p>
            
            <p style="margin:0 0 4px">Endereço: $addressLine</p>
            $noteBlock
        """.trimIndent()

        val who = if (isAuthor) headerAuthor else headerClient
        val txidLine = order.txid?.let { "<p style=\"margin:0 0 8px\"><strong>TXID Pix:</strong> $it</p>" } ?: ""

        val contactBlock = """
            <p style="margin:16px 0 0;color:#555">
              Em caso de dúvida ou cancelamento, entre em contato com <strong>Agenor Gasparetto</strong><br>
              Email: <a href="mailto:ag1957@gmail.com">ag1957@gmail.com</a> · WhatsApp: <a href="https://wa.me/5571994105740">(71) 99410-5740</a>
            </p>
        """.trimIndent()

        return """
        <html>
        <body style="font-family:Arial,Helvetica,sans-serif;background:#f6f7f9;padding:24px">
          <div style="max-width:640px;margin:0 auto;background:#fff;border:1px solid #eee;border-radius:10px;overflow:hidden">
            <div style="background:#111;color:#fff;padding:16px 20px">
              <strong style="font-size:16px">Agenor Gasparetto – Ecommerce</strong>
            </div>
            <div style="padding:20px">
              $who

              <p style="margin:12px 0 8px"><strong>Nº do pedido:</strong> #${order.id}</p>
              $txidLine

              <h3 style="font-size:15px;margin:16px 0 8px">Itens</h3>
              <table width="100%" cellspacing="0" cellpadding="0" style="border-collapse:collapse">
                $itemsHtml
              </table>

              <div style="margin-top:14px">
                <p style="margin:4px 0"><strong>Frete:</strong> $shipping</p>
                <p style="margin:4px 0;font-size:16px"><strong>Total:</strong> $total</p>
                <p style="margin:4px 0"><strong>Pagamento:</strong> Pix</p>
              </div>

              ${if (!isAuthor) "<p style=\"margin:16px 0 0\">Obrigado por comprar com a gente! 💛</p>" else ""}

              $contactBlock
            </div>
            <div style="background:#fafafa;color:#888;padding:12px 20px;text-align:center;font-size:12px">
              © ${java.time.Year.now()} Agenor Gasparetto. Todos os direitos reservados.
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
