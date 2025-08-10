// src/pages/PixPaymentPage.tsx
import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { formatPrice } from "../utils/formatPrice";
import { calcularFreteComBaseEmCarrinho } from "../utils/freteUtils";
import type { CartItem } from "../context/CartTypes";

const API_BASE = import.meta.env.VITE_API_BASE ?? "https://ecommerceag-6fa0e6a5edbf.herokuapp.com";

export default function PixPaymentPage() {
  const navigate = useNavigate();

  // estado básico
  const [cartItems, setCartItems] = useState<CartItem[]>(
    JSON.parse(localStorage.getItem("cart") || "[]")
  );
  const [frete, setFrete] = useState(0);
  const [qrCodeImg, setQrCodeImg] = useState("");
  const [pixCopiaECola, setPixCopiaECola] = useState("");
  const [loading, setLoading] = useState(false);

  // controle do pedido / SSE
  const [orderId, setOrderId] = useState<string | null>(null);
  const [txid, setTxid] = useState<string | null>(null);
  const esRef = useRef<EventSource | null>(null);
  const retryRef = useRef<number>(0);

  const totalProdutos = cartItems.reduce((s, i) => s + i.price * i.quantity, 0);
  const totalComFrete = totalProdutos + frete;

  // garante carrinho em memória
  useEffect(() => {
    if (cartItems.length === 0) {
      const stored = localStorage.getItem("cart");
      if (stored) setCartItems(JSON.parse(stored));
    }
  }, [cartItems.length]);

  // calcula frete assim que tiver form + carrinho
  useEffect(() => {
    const savedForm = localStorage.getItem("checkoutForm");
    if (!savedForm || cartItems.length === 0) return;
    const form = JSON.parse(savedForm);
    calcularFreteComBaseEmCarrinho({ cep: form.cep, cpf: form.cpf }, cartItems)
      .then(setFrete)
      .catch(() => setFrete(0));
  }, [cartItems]);

  // cria a cobrança (uma vez) e abre SSE
  useEffect(() => {
    if (!frete || cartItems.length === 0 || orderId) return;
    const savedForm = localStorage.getItem("checkoutForm");
    if (!savedForm) {
      navigate("/checkout");
      return;
    }
    const form = JSON.parse(savedForm);

    let cancelled = false;
    (async () => {
      try {
        setLoading(true);
        const res = await fetch(`${API_BASE}/api/checkout`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            firstName: form.firstName,
            lastName: form.lastName,
            cpf: form.cpf,
            country: form.country,
            cep: form.cep,
            address: form.address,
            number: form.number,
            complement: form.complement,
            district: form.district,
            city: form.city,
            state: form.state,
            phone: form.phone,
            email: form.email,
            note: form.note,
            payment: form.payment,
            shipping: frete,
            cartItems,
            total: totalProdutos,
          }),
        });
        if (!res.ok) throw new Error(await res.text());
        const data = await res.json();

        if (cancelled) return;

        const img = (data.qrCodeBase64 || "").startsWith("data:image")
          ? data.qrCodeBase64
          : `data:image/png;base64,${data.qrCodeBase64 || ""}`;

        setQrCodeImg(img);
        setPixCopiaECola(data.qrCode || "");
        setOrderId(data.orderId);
        setTxid(data.txid);

        // abre SSE
        openSse(data.orderId);
      } catch (err) {
        console.error("Falha ao gerar cobrança:", err);
      } finally {
        setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [frete, cartItems, totalProdutos, navigate]);

  // abre SSE + reconexão simples com backoff
  function openSse(id: string) {
    // fecha anterior se houver
    if (esRef.current) {
      esRef.current.close();
      esRef.current = null;
    }
    const url = `${API_BASE}/api/orders/${id}/events`;
    const es = new EventSource(url, { withCredentials: false });
    esRef.current = es;

    es.onmessage = (evt) => {
      retryRef.current = 0; // zera backoff quando chega mensagem
      try {
        const data = JSON.parse(evt.data || "{}");
        // você pode usar qualquer payload que implementou no backend:
        // { type: "paid", orderId, txid, mailedAt } etc.
        if (data.type === "paid" || data.paid === true) {
          // limpa carrinho e navega
          localStorage.removeItem("cart");
          localStorage.removeItem("checkoutForm");
          navigate(`/pedido-confirmado?orderId=${id}`);
        }
      } catch (e) {
        console.warn("SSE parse error:", e);
      }
    };

    es.onerror = () => {
      es.close();
      // tenta reconectar com backoff (2s, 4s, 8s… máx 30s)
      const n = Math.min(30000, Math.pow(2, retryRef.current++) * 2000);
      setTimeout(() => openSse(id), n);
    };
  }

  // limpa SSE ao sair da página
  useEffect(() => {
    return () => {
      if (esRef.current) esRef.current.close();
    };
  }, []);

  return (
    <div className="max-w-3xl mx-auto p-6">
      <h2 className="text-2xl font-semibold mb-4">Resumo da Compra (Pix)</h2>

      <div className="space-y-4">
        {cartItems.map((item) => (
          <div key={item.id} className="border p-4 rounded shadow-sm flex gap-4 items-center">
            <img src={item.imageUrl} alt={item.title} className="w-24 h-auto object-contain" />
            <div>
              <p className="font-medium">{item.title}</p>
              <p>Quantidade: {item.quantity}</p>
              <p>Preço unitário: {formatPrice(item.price)}</p>
              <p>Subtotal: {formatPrice(item.price * item.quantity)}</p>
            </div>
          </div>
        ))}
      </div>

      <div className="mt-6 text-right space-y-2">
        <p className="text-lg">Subtotal: {formatPrice(totalProdutos)}</p>
        <p className="text-lg">Frete: {formatPrice(frete)}</p>
        <p className="text-xl font-bold">Total com Frete: {formatPrice(totalComFrete)}</p>
      </div>

      {loading && <p className="text-center mt-8 text-gray-600">Gerando QR Code Pix...</p>}

      {qrCodeImg && (
        <div className="mt-10 text-center space-y-3">
          <p className="text-lg font-medium">Escaneie o QR Code com seu app do banco:</p>
          <img src={qrCodeImg} alt="QR Code Pix" className="mx-auto" />
          {pixCopiaECola && (
            <div className="max-w-xl mx-auto">
              <p className="mt-4 text-sm text-gray-700">Ou copie e cole no seu app:</p>
              <div className="flex gap-2 items-center mt-1">
                <input readOnly value={pixCopiaECola} className="flex-1 border rounded px-2 py-1 text-xs" />
                <button
                  onClick={() => navigator.clipboard.writeText(pixCopiaECola)}
                  className="bg-black text-white px-3 py-1 rounded text-sm"
                >
                  Copiar
                </button>
              </div>
              {orderId && txid && (
                <p className="text-xs text-gray-500 mt-3">
                  Pedido #{orderId} — TXID {txid}
                </p>
              )}
            </div>
          )}
          <p className="text-sm text-gray-600 mt-4">
            Assim que o pagamento for confirmado, você será redirecionado automaticamente.
          </p>
        </div>
      )}
    </div>
  );
}
