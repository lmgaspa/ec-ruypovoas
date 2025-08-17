import { useState, useEffect, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { useCart } from "../hooks/useCart";
import { formatCep, formatCpf, formatCelular } from "../utils/masks";
import CheckoutForm from "./CheckoutForm";
import type { CartItem } from "../context/CartTypes";
import { calcularFreteComBaseEmCarrinho } from "../utils/freteUtils";
import { getStockByIds } from "../api/stock";

const CheckoutPage = () => {
  const navigate = useNavigate();
  const { getCart } = useCart();

  const [cartItems, setCartItems] = useState<CartItem[]>([]);
  const [total, setTotal] = useState(0);
  const [shipping, setShipping] = useState(0);

  const [form, setForm] = useState(() => {
    const saved = localStorage.getItem("checkoutForm");
    return saved
      ? JSON.parse(saved)
      : {
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
          delivery: "",
          payment: "pix",
        };
  });

  const onNavigateBack = () => navigate("/");

  useEffect(() => {
    const cart = getCart();
    (async () => {
      const ids = cart.map((c) => c.id);
      const stockMap = await getStockByIds(ids);
      const fixed = cart
        .map((i) => {
          const s = stockMap[i.id]?.stock ?? 0;
          const qty = Math.min(i.quantity, Math.max(0, s));
          return { ...i, quantity: qty };
        })
        .filter((i) => i.quantity > 0);

      setCartItems(fixed);
      localStorage.setItem("cart", JSON.stringify(fixed));
      const sum = fixed.reduce((acc, item) => acc + item.price * item.quantity, 0);
      setTotal(sum);

      if (fixed.length !== cart.length || JSON.stringify(fixed) !== JSON.stringify(cart)) {
        alert("Atualizamos seu carrinho de acordo com o estoque atual.");
      }
    })();
  }, [getCart]);

  const cpfCepInfo = useMemo(() => {
    const cpf = form.cpf.replace(/\D/g, "");
    const cep = form.cep.replace(/\D/g, "");
    return { cpf, cep };
  }, [form.cpf, form.cep]);

  useEffect(() => {
    if (cpfCepInfo.cpf === "00000000000" || cpfCepInfo.cep.length !== 8 || cartItems.length === 0) {
      setShipping(0);
      return;
    }

    calcularFreteComBaseEmCarrinho({ cpf: cpfCepInfo.cpf, cep: cpfCepInfo.cep }, cartItems)
      .then(setShipping)
      .catch(() => setShipping(0));
  }, [cpfCepInfo, cartItems]);

  useEffect(() => {
    localStorage.setItem("checkoutForm", JSON.stringify(form));
  }, [form]);

  const updateQuantity = (id: string, delta: number) => {
    const updated = cartItems
      .map((item) => (item.id === id ? { ...item, quantity: item.quantity + delta } : item))
      .filter((item) => item.quantity > 0);

    setCartItems(updated);
    localStorage.setItem("cart", JSON.stringify(updated));
    const sum = updated.reduce((acc, item) => acc + item.price * item.quantity, 0);
    setTotal(sum);
  };

  const removeItem = (id: string) => {
    const updated = cartItems.filter((item) => item.id !== id);
    setCartItems(updated);
    localStorage.setItem("cart", JSON.stringify(updated));
    const sum = updated.reduce((acc, item) => acc + item.price * item.quantity, 0);
    setTotal(sum);
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const { name, value: inputValue } = e.target;
    let value = inputValue;

    if (name === "cep") value = formatCep(value);
    if (name === "cpf") value = formatCpf(value);
    if (name === "phone") value = formatCelular(value);

    setForm((prev: typeof form) => ({ ...prev, [name]: value }));

    if (name === "cep" && value.replace(/\D/g, "").length === 8) {
      fetch(`https://viacep.com.br/ws/${value.replace(/\D/g, "")}/json/`)
        .then((res) => res.json())
        .then((data) => {
          setForm((prev: typeof form) => ({
            ...prev,
            address: data.logradouro || "",
            district: data.bairro || "",
            city: data.localidade || "",
            state: data.uf || "",
          }));
        })
        .catch(() => {});
    }
  };

  return (
    <CheckoutForm
      cartItems={cartItems}
      total={total + shipping}
      shipping={shipping}
      form={form}
      updateQuantity={updateQuantity}
      removeItem={removeItem}
      handleChange={handleChange}
      onNavigateBack={onNavigateBack}
    />
  );
};

export default CheckoutPage;
