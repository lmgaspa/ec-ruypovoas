import { useState, useEffect, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { useCart } from "../hooks/useCart";
import { formatCep, formatCpf, formatCelular } from "../utils/masks";
import CheckoutForm from "./CheckoutForm";
import type { CartItem } from "../context/CartTypes";
import type { CheckoutFormData } from "../types/checkoutTypes";
import { calcularFreteComBaseEmCarrinho } from "../utils/freteUtils";
import { getStockByIds } from "../api/stock";

const CheckoutPage = () => {
  const navigate = useNavigate();
  const { getCart } = useCart();

  const [cartItems, setCartItems] = useState<CartItem[]>([]);
  const [totalItems, setTotalItems] = useState(0); // só itens
  const [shipping, setShipping] = useState(0);
  const [stockById, setStockById] = useState<Record<string, number>>({});

  const [form, setForm] = useState<CheckoutFormData>(() => {
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
          shipping: 0,
        };
  });

  const onNavigateBack = () => navigate("/books");

  // Carrega carrinho e ajusta por estoque
  useEffect(() => {
    const cart = getCart();
    (async () => {
      const ids = cart.map((c) => c.id);
      const stockMap = await getStockByIds(ids);
      const stockDict = Object.fromEntries(
        ids.map((id) => [id, Math.max(0, stockMap[id]?.stock ?? 0)])
      );
      setStockById(stockDict);

      const fixed = cart
        .map((i) => {
          const s = stockDict[i.id] ?? 0;
          const qty = Math.min(i.quantity, Math.max(0, s));
          return { ...i, quantity: qty };
        })
        .filter((i) => i.quantity > 0);

      const sum = fixed.reduce((acc, item) => acc + item.price * item.quantity, 0);

      setCartItems(fixed);
      setTotalItems(sum);
      localStorage.setItem("cart", JSON.stringify(fixed));

      if (fixed.length !== cart.length || JSON.stringify(fixed) !== JSON.stringify(cart)) {
        alert("Atualizamos seu carrinho de acordo com o estoque atual.");
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Normaliza para cálculo de frete
  const cpfCepInfo = useMemo(() => {
    const cpf = form.cpf.replace(/\D/g, "");
    const cep = form.cep.replace(/\D/g, "");
    const phone = form.phone.replace(/\D/g, "");
    return { cpf, cep, phone };
  }, [form.cpf, form.cep, form.phone]);

  // Calcula frete
  useEffect(() => {
    if (
      cpfCepInfo.cpf === "00000000000" ||
      cpfCepInfo.cep.length !== 8 ||
      cartItems.length === 0
    ) {
      setShipping(0);
      return;
    }

    calcularFreteComBaseEmCarrinho(
      { cpf: cpfCepInfo.cpf, cep: cpfCepInfo.cep },
      cartItems
    )
      .then(setShipping)
      .catch(() => setShipping(0));
  }, [cpfCepInfo, cartItems]);

  // Persiste form + shipping (para CardPaymentPage ler)
  useEffect(() => {
    localStorage.setItem("checkoutForm", JSON.stringify({ ...form, shipping }));
  }, [form, shipping]);

  const updateQuantity = (id: string, delta: number) => {
    const updated = cartItems
      .map((item) => {
        if (item.id !== id) return item;
        const max = stockById[id] ?? Infinity;
        const next = item.quantity + delta;
        if (delta > 0 && next > max) {
          alert("Quantidade solicitada excede o estoque disponível.");
          return item;
        }
        if (next <= 0) return null;
        return { ...item, quantity: next };
      })
      .filter((item): item is CartItem => item !== null);

    setCartItems(updated);
    localStorage.setItem("cart", JSON.stringify(updated));
    const sum = updated.reduce((acc, it) => acc + it.price * it.quantity, 0);
    setTotalItems(sum);
    if (!updated.length) setShipping(0);
  };

  const removeItem = (id: string) => {
    const updated = cartItems.filter((item) => item.id !== id);
    setCartItems(updated);
    localStorage.setItem("cart", JSON.stringify(updated));
    const sum = updated.reduce((acc, it) => acc + it.price * it.quantity, 0);
    setTotalItems(sum);
    if (!updated.length) setShipping(0);
  };

  const handleChange = (
    e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>
  ) => {
    const { name, value: inputValue } = e.target;
    let value = inputValue;

    if (name === "cep") value = formatCep(value);
    if (name === "cpf") value = formatCpf(value);
    if (name === "phone") value = formatCelular(value);

    setForm((prev) => ({ ...prev, [name]: value }));

    if (name === "cep" && value.replace(/\D/g, "").length === 8) {
      fetch(`https://viacep.com.br/ws/${value.replace(/\D/g, "")}/json/`)
        .then((res) => res.json())
        .then((data) => {
          setForm((prev) => ({
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
      total={totalItems + shipping}
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
