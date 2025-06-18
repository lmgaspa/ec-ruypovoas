package com.luizgasparetto.ecommerceag2.service;

import com.luizgasparetto.ecommerceag2.dto.PurchaseMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void sendPurchaseEmail(PurchaseMessage message) {
        SimpleMailMessage email = new SimpleMailMessage();
        email.setTo("luhmgasparetto@gmail.com");
        email.setSubject("📘 Nova compra recebida");

        StringBuilder livrosVendidos = new StringBuilder();
        if (message.getCartItems() != null && !message.getCartItems().isEmpty()) {
            livrosVendidos.append("📚 Livros Comprados:\n");
            message.getCartItems().forEach(item -> {
                livrosVendidos.append("- ")
                        .append(item.getId())
                        .append(" (Qtd: ")
                        .append(item.getQuantity())
                        .append(")\n");
            });
        } else {
            livrosVendidos.append("Nenhum livro listado.\n");
        }

        String body = String.format(
                "📘 Nova compra recebida!\n\n" +
                        "Cliente: %s %s\n" +
                        "Email: %s\n" +
                        "Telefone: %s\n" +
                        "Endereço: %s, %s - %s, %s - %s, CEP: %s\n\n" +
                        "Forma de entrega: %s\n" +
                        "Forma de pagamento: %s\n" +
                        "Valor total: R$ %.2f\n" +
                        "Observações: %s\n\n%s",
                message.getFirstName(),
                message.getLastName(),
                message.getEmail(),
                message.getPhone(),
                message.getAddress(),
                message.getNumber(),
                message.getDistrict(),
                message.getCity(),
                message.getState(),
                message.getCep(),
                message.getDelivery(),
                message.getPayment(),
                message.getValor(),
                message.getNote(),
                livrosVendidos.toString()
        );

        email.setText(body);
        mailSender.send(email);
    }
}
