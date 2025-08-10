import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import type { CartItem } from "../context/CartTypes";
import { formatPrice } from "../utils/formatPrice";
import { calcularFreteComBaseEmCarrinho } from "../utils/freteUtils";

const API_BASE =
  import.meta.env.VITE_API_BASE ??
  "https://ecommerceag-6fa0e6a5edbf.herokuapp.com";

export default function PixPaymentPage() {
  const navigate = useNavigate();
  const initialCart: CartItem[] = (() => {
    const stored = localStorage.getItem("cart");
    return stored ? JSON.parse(stored) : [];
  })();

  const [cartItems, setCartItems] = useState<CartItem[]>(initialCart);
  const [frete, setFrete] = useState(0);
  const [qrCodeImg, setQrCodeImg] = useState("");
  const [pixCopiaECola, setPixCopiaECola] = useState("");
  const [loading, setLoading] = useState(false);
  const [orderId, setOrderId] = useState<string | null>(null);
  const sseRef = useRef<EventSource | null>(null);

  const totalProdutos = cartItems.reduce(
    (sum, item) => sum + item.price * item.quantity,
    0
  );
  const totalComFrete = totalProdutos + frete;

  useEffect(() => {
    if (cartItems.length === 0) {
      const stored = localStorage.getItem("cart");
      if (stored) setCartItems(JSON.parse(stored));
    }
  }, [cartItems.length]);

  useEffect(() => {
    const savedForm = localStorage.getItem("checkoutForm");
    if (!savedForm || cartItems.length === 0) return;
    const form = JSON.parse(savedForm);
    calcularFreteComBaseEmCarrinho({ cep: form.cep, cpf: form.cpf }, cartItems)
      .then(setFrete)
      .catch(() => setFrete(0));
  }, [cartItems]);

  useEffect(() => {
    const run = async () => {
      if (!frete || cartItems.length === 0 || qrCodeImg) return;
      const savedForm = localStorage.getItem("checkoutForm");
      if (!savedForm) {
        navigate("/checkout");
        return;
      }
      const form = JSON.parse(savedForm);

      setLoading(true);
      try {
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

        const img = (data.qrCodeBase64 || "").startsWith("data:image")
          ? data.qrCodeBase64
          : `data:image/png;base64,${data.qrCodeBase64 || ""}`;
        setQrCodeImg(img);
        setPixCopiaECola(data.qrCode || "");
        setOrderId(data.orderId || null);

        // conecta no SSE para saber quando pagar
        if (data.orderId) {
          // fecha um antigo (hot reload etc.)
          if (sseRef.current) {
            sseRef.current.close();
            sseRef.current = null;
          }
          const url = `${API_BASE}/api/orders/${data.orderId}/events`;
          // ...
          const es = new EventSource(url, { withCredentials: false });
          sseRef.current = es;

          es.addEventListener("paid", () => {
            // limpa carrinho e vai para a tela de sucesso
            localStorage.removeItem("cart");
            const nf = JSON.parse(localStorage.getItem("checkoutForm") || "{}");
            const fn = [nf.firstName, nf.lastName]
              .filter(Boolean)
              .join(" ")
              .trim();
            navigate(
              `/pedido-confirmado?orderId=${data.orderId}${
                fn ? `&name=${encodeURIComponent(fn)}` : ""
              }`
            );
          });

          // Evite bloco vazio — log simples já satisfaz o lint:
          es.onerror = (err) => {
            console.warn("SSE error (reconexão automática do navegador):", err);
          };
        }
      } catch (e) {
        console.error(e);
      } finally {
        setLoading(false);
      }
    };
    run();

    return () => {
      if (sseRef.current) {
        sseRef.current.close();
        sseRef.current = null;
      }
    };
  }, [frete, cartItems, qrCodeImg, totalProdutos, navigate]);

  const handleReviewClick = () => {
    navigate("/checkout");
  };

  return (
    <div className="max-w-3xl mx-auto p-6">
      <h2 className="text-2xl font-semibold mb-4">Resumo da Compra (Pix)</h2>

      <div className="space-y-4">
        {cartItems.map((item) => (
          <div
            key={item.id}
            className="border p-4 rounded shadow-sm flex gap-4 items-center"
          >
            <img
              src={item.imageUrl}
              alt={item.title}
              className="w-24 h-auto object-contain"
            />
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
        <p className="text-xl font-bold">
          Total com Frete: {formatPrice(totalComFrete)}
        </p>
      </div>

      <div className="mt-8 flex justify-between">
        <button
          onClick={handleReviewClick}
          className="bg-gray-300 hover:bg-gray-400 text-black px-4 py-2 rounded"
        >
          Revisar compra
        </button>

        {orderId && (
          <span className="text-sm text-gray-500 self-center">
            Pedido #{orderId}
          </span>
        )}
      </div>

      {loading && (
        <p className="text-center mt-8 text-gray-600">Gerando QR Code Pix...</p>
      )}

      {qrCodeImg && (
        <div className="mt-10 text-center space-y-3">
          <p className="text-lg font-medium">
            Escaneie o QR Code com seu app do banco:
          </p>
          <img src={qrCodeImg} alt="QR Code Pix" className="mx-auto" />
          {pixCopiaECola && (
            <div className="max-w-xl mx-auto">
              <p className="mt-4 text-sm text-gray-700">
                Ou copie e cole no seu app:
              </p>
              <div className="flex gap-2 items-center mt-1">
                <input
                  readOnly
                  value={pixCopiaECola}
                  className="flex-1 border rounded px-2 py-1 text-xs"
                />
                <button
                  onClick={() => navigator.clipboard.writeText(pixCopiaECola)}
                  className="bg-black text-white px-3 py-1 rounded text-sm"
                >
                  Copiar
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
