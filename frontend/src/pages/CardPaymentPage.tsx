import { useNavigate } from "react-router-dom";
import { useState } from "react";
import type { CartItem } from "../context/CartTypes";
import { formatPrice } from "../utils/formatPrice";

const API_BASE =
  import.meta.env.VITE_API_BASE ??
  "https://editoranossolar-3fd4fdafdb9e.herokuapp.com";

// Tipos de cartão
interface CardData {
  number: string;
  holderName: string;
  expirationMonth: string;
  expirationYear: string;
  cvv: string;
}

// Tipos de resposta do checkout da Efí
interface PaymentTokenResponse {
  payment_token: string;
  card_mask: string;
}

// Tipo do objeto checkout exposto pelo script da Efí
interface CheckoutAPI {
  getPaymentToken(
    params: {
      brand: string;
      number: string;
      cvv: string;
      expiration_month: string;
      expiration_year: string;
      reuse: boolean;
    },
    callback: (
      error: { error_description?: string } | null,
      response: PaymentTokenResponse | null
    ) => void
  ): void;

  getInstallments(
    total: number,
    brand: string,
    callback: (error: unknown, response: unknown) => void
  ): void;
}

// Declaração global do $gn
declare global {
  interface Window {
    $gn: {
      ready: (fn: (checkout: CheckoutAPI) => void) => void;
    };
  }
}

export default function CardPaymentPage() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const [card, setCard] = useState<CardData>({
    number: "",
    holderName: "",
    expirationMonth: "",
    expirationYear: "",
    cvv: "",
  });

  const cart: CartItem[] = JSON.parse(localStorage.getItem("cart") || "[]");
  const total = cart.reduce((acc, i) => acc + i.price * i.quantity, 0);

  const handlePay = () => {
    setLoading(true);
    setErrorMsg(null);

    // Gera token de pagamento no front-end via Efí
    window.$gn.ready((checkout) => {
      checkout.getPaymentToken(
        {
          brand: "visa", // aqui depois podemos detectar automaticamente a bandeira
          number: card.number,
          cvv: card.cvv,
          expiration_month: card.expirationMonth,
          expiration_year: card.expirationYear,
          reuse: false,
        },
        async (error, response) => {
          if (error) {
            setErrorMsg(error.error_description ?? "Erro ao gerar token.");
            setLoading(false);
            return;
          }

          if (!response?.payment_token) {
            setErrorMsg("Token de pagamento não retornado.");
            setLoading(false);
            return;
          }

          // Envia para o backend
          try {
            const form = JSON.parse(localStorage.getItem("checkoutForm") || "{}");

            const res = await fetch(`${API_BASE}/api/checkout`, {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({
                ...form,
                payment: "card",
                paymentToken: response.payment_token,
                cartItems: cart,
                total,
                shipping: form.shipping ?? 0,
              }),
            });

            if (!res.ok) throw new Error(await res.text());
            const data: {
              message?: string;
              orderId?: string;
              paid?: boolean;
            } = await res.json();

            localStorage.removeItem("cart");

            navigate(
              `/pedido-confirmado?orderId=${data.orderId}&payment=card&paid=${
                data.paid ? "true" : "false"
              }`
            );
          } catch (e) {
            if (e instanceof Error) {
              setErrorMsg(e.message);
            } else {
              setErrorMsg("Erro inesperado no pagamento.");
            }
          } finally {
            setLoading(false);
          }
        }
      );
    });
  };

  return (
    <div className="max-w-md mx-auto p-6">
      <h2 className="text-xl font-semibold mb-4">Pagamento com Cartão</h2>
      {errorMsg && (
        <div className="bg-red-50 text-red-600 p-2 mb-4">{errorMsg}</div>
      )}

      <input
        value={card.number}
        onChange={(e) => setCard({ ...card, number: e.target.value })}
        placeholder="Número do cartão"
        className="border p-2 w-full mb-2"
      />
      <input
        value={card.holderName}
        onChange={(e) => setCard({ ...card, holderName: e.target.value })}
        placeholder="Nome impresso"
        className="border p-2 w-full mb-2"
      />
      <div className="flex gap-2">
        <input
          value={card.expirationMonth}
          onChange={(e) => setCard({ ...card, expirationMonth: e.target.value })}
          placeholder="MM"
          className="border p-2 w-1/2 mb-2"
        />
        <input
          value={card.expirationYear}
          onChange={(e) => setCard({ ...card, expirationYear: e.target.value })}
          placeholder="AA"
          className="border p-2 w-1/2 mb-2"
        />
      </div>
      <input
        value={card.cvv}
        onChange={(e) => setCard({ ...card, cvv: e.target.value })}
        placeholder="CVV"
        className="border p-2 w-full mb-4"
      />

      <p className="font-bold mb-4">Total: {formatPrice(total)}</p>

      <button
        disabled={loading}
        onClick={handlePay}
        className="bg-blue-600 text-white py-2 w-full rounded hover:bg-blue-500"
      >
        {loading ? "Processando..." : "Pagar com Cartão"}
      </button>
    </div>
  );
}
