import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import type { CartItem } from "../context/CartTypes";
import { formatPrice } from "../utils/formatPrice";
import { calcularFreteComBaseEmCarrinho } from "../utils/freteUtils";

const API_BASE =
  import.meta.env.VITE_API_BASE ??
  "https://ecommerce-adilson-f543f4ef7a51.herokuapp.com";

export default function PixPaymentPage() {
  const navigate = useNavigate();

  const initialCart: CartItem[] = (() => {
    const stored = localStorage.getItem("cart");
    return stored ? JSON.parse(stored) : [];
  })();

  const [cartItems, setCartItems] = useState<CartItem[]>(initialCart);
  const [frete, setFrete] = useState<number | null>(null);
  const [qrCodeImg, setQrCodeImg] = useState("");
  const [pixCopiaECola, setPixCopiaECola] = useState("");
  const [loading, setLoading] = useState(false);
  const [orderId, setOrderId] = useState<string | null>(null);

  const sseRef = useRef<EventSource | null>(null);
  const retryTimerRef = useRef<number | null>(null);
  const backoffRef = useRef(1500);
  const isMountedRef = useRef(true);

  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
      try {
        sseRef.current?.close();
      } catch {
        /* no-op */
      }
      sseRef.current = null;
      if (retryTimerRef.current) {
        window.clearTimeout(retryTimerRef.current);
        retryTimerRef.current = null;
      }
    };
  }, []);

  const totalProdutos = useMemo(
    () => cartItems.reduce((sum, item) => sum + item.price * item.quantity, 0),
    [cartItems]
  );
  const totalComFrete = useMemo(
    () => totalProdutos + (frete ?? 0),
    [totalProdutos, frete]
  );

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
      .then((v) => {
        if (isMountedRef.current) {
          setFrete(v);
        }
      })
      .catch(() => {
        if (isMountedRef.current) {
          setFrete(0);
        }
      });
  }, [cartItems]);

  useEffect(() => {
    const run = async () => {
      if (frete === null || cartItems.length === 0 || orderId) return;

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
            shipping: frete, // pode ser 0
            cartItems,
            total: totalProdutos,
          }),
        });
        if (!res.ok) throw new Error(await res.text());
        const data = await res.json();

        const img = (data.qrCodeBase64 || "").startsWith("data:image")
          ? data.qrCodeBase64
          : `data:image/png;base64,${data.qrCodeBase64 || ""}`;

        if (!isMountedRef.current) return;

        setQrCodeImg(img);
        setPixCopiaECola(data.qrCode || "");
        setOrderId(String(data.orderId || ""));
      } catch (e) {
        console.error(e);
      } finally {
        if (isMountedRef.current) {
          setLoading(false);
        }
      }
    };
    run();
  }, [frete, cartItems, totalProdutos, navigate, orderId]);

  const connectSSE = (id: string) => {
    try {
      sseRef.current?.close();
    } catch {
      /* no-op */
    }
    sseRef.current = null;

    const url = `${API_BASE}/api/orders/${id}/events`;
    const es = new EventSource(url, { withCredentials: false });
    sseRef.current = es;

    const resetBackoff = () => {
      backoffRef.current = 1500;
    };

    es.addEventListener("open", () => {
      resetBackoff();
    });

    es.addEventListener("ping", () => {
      /* keep-alive */
    });

    es.addEventListener("paid", () => {
      try {
        es.close();
        sseRef.current = null;
      } catch {
        /* no-op */
      }
      localStorage.removeItem("cart");
      const nf = JSON.parse(localStorage.getItem("checkoutForm") || "{}");
      const fn = [nf.firstName, nf.lastName].filter(Boolean).join(" ").trim();
      const idForNav = id;

      navigate(
        `/pedido-confirmado?orderId=${idForNav}${
          fn ? `&name=${encodeURIComponent(fn)}` : ""
        }`
      );
    });

    es.onerror = () => {
      try {
        es.close();
      } catch {
        /* no-op */
      }
      sseRef.current = null;

      const wait = Math.min(backoffRef.current, 10000);

      if (retryTimerRef.current) {
        window.clearTimeout(retryTimerRef.current);
        retryTimerRef.current = null;
      }
      retryTimerRef.current = window.setTimeout(() => {
        backoffRef.current = Math.min(backoffRef.current * 2, 10000);
        if (isMountedRef.current && orderId === id) {
          connectSSE(id);
        }
      }, wait);
    };
  };

  useEffect(() => {
    if (!orderId) return;
    connectSSE(orderId);
    return () => {
      try {
        sseRef.current?.close();
      } catch {
        /* no-op */
      }
      sseRef.current = null;
      if (retryTimerRef.current) {
        window.clearTimeout(retryTimerRef.current);
        retryTimerRef.current = null;
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [orderId]);

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
        <p className="text-lg">Frete: {formatPrice(frete ?? 0)}</p>
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
        <span className="text-sm text-gray-500 self-center" />
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

//