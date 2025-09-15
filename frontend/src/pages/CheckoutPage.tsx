import { useState } from "react";
import { useNavigate } from "react-router-dom";
import CheckoutFormView from "../components/checkout/CheckoutFormView";
import type { CartItem } from "../context/CartTypes";

const API_BASE = import.meta.env.VITE_API_BASE ?? "https://seu-backend.herokuapp.com";

export default function CheckoutPage() {
  const navigate = useNavigate();

  // Carrega carrinho do localStorage
  const cart: CartItem[] = JSON.parse(localStorage.getItem("cart") || "[]");
  const shipping = 0; // você pode calcular via API depois
  const total = cart.reduce((acc, i) => acc + i.price * i.quantity, shipping);

  // Form state inicial
  const [form, setForm] = useState({
    firstName: "",
    lastName: "",
    cpf: "",
    country: "Brasil",
    cep: "",
    address: "",
    number: "",
    complement: "",
    district: "",
    city: "",
    state: "",
    phone: "",
    email: "",
    note: "",
    delivery: "normal",
    payment: "", // "pix" ou "card"
    shipping: shipping,
  });

  // Atualiza campos
  const handleChange = (
    e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>
  ) => {
    const { name, value } = e.target;
    setForm((prev) => ({ ...prev, [name]: value }));
  };

  // Atualiza quantidade
  const updateQuantity = (id: string, delta: number) => {
    const newCart = cart.map((item) =>
      item.id === id ? { ...item, quantity: Math.max(1, item.quantity + delta) } : item
    );
    localStorage.setItem("cart", JSON.stringify(newCart));
    window.location.reload();
  };

  // Remove item
  const removeItem = (id: string) => {
    const newCart = cart.filter((item) => item.id !== id);
    localStorage.setItem("cart", JSON.stringify(newCart));
    window.location.reload();
  };

  // PIX Checkout
  const handlePixCheckout = async () => {
    localStorage.setItem("checkoutForm", JSON.stringify(form));
    try {
      const res = await fetch(`${API_BASE}/api/checkout`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          ...form,
          payment: "pix",
          cartItems: cart,
          total,
          shipping,
        }),
      });
      if (!res.ok) throw new Error(await res.text());
      const data = await res.json();

      // Redireciona para página Pix
      navigate(`/pagamento-pix?orderId=${data.orderId}&txid=${data.txid}`);
    } catch (err) {
      console.error("Erro no Pix:", err);
      alert("Falha ao gerar Pix.");
    }
  };

  // Cartão Checkout
  const handleCardCheckout = () => {
    // Salva dados antes de redirecionar
    localStorage.setItem("checkoutForm", JSON.stringify(form));
    navigate("/pagamento-cartao");
  };

  const onNavigateBack = () => navigate("/cart");

  return (
    <CheckoutFormView
      cartItems={cart}
      total={total}
      shipping={shipping}
      form={form}
      handleChange={handleChange}
      updateQuantity={updateQuantity}
      removeItem={removeItem}
      handlePixCheckout={handlePixCheckout}
      handleCardCheckout={handleCardCheckout}
      onNavigateBack={onNavigateBack}
    />
  );
}
