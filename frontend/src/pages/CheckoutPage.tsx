import React from "react";
import CheckoutFormView from "../components/checkout/CheckoutFormView";
import type { CartItem, CheckoutFormData } from "../components/checkout/CheckoutFormView";

// --------- Estado inicial do formulário ----------
const INITIAL_FORM: CheckoutFormData = {
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
  payment: "", // "pix" | "card" | ""
};

// --------- Hook de persistência simples ----------
function usePersistentFormState<T>(storageKey: string, initial: T) {
  const [state, setState] = React.useState<T>(() => {
    try {
      const raw = localStorage.getItem(storageKey);
      if (!raw) return initial;
      const parsed = JSON.parse(raw);
      // merge superficial para manter chaves novas do INITIAL_FORM
      return { ...initial, ...parsed };
    } catch {
      return initial;
    }
  });

  React.useEffect(() => {
    try {
      localStorage.setItem(storageKey, JSON.stringify(state));
    } catch {
      // evitar no-empty: storage pode falhar (modo privado Safari, quota, etc.)
      void 0;
    }
  }, [storageKey, state]);

  return [state, setState] as const;
}

const STORAGE_KEY = "checkout_form_v1";

export default function CheckoutPage() {
  // --------- Carrinho (exemplo; substitua pela sua fonte real) ----------
  const [cartItems, setCartItems] = React.useState<CartItem[]>(() => {
    // traga do seu provider/Redux se existir
    return [];
  });

  // --------- Formulário (estável e persistente) ----------
  const [form, setForm] = usePersistentFormState<CheckoutFormData>(
    STORAGE_KEY,
    INITIAL_FORM
  );

  // --------- Handlers do formulário ----------
  const handleChange = React.useCallback(
    (
      e: React.ChangeEvent<
        HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement
      >
    ) => {
      const { name, value } = e.target;
      setForm((prev) => ({ ...prev, [name]: value }));
    },
    [setForm]
  );

  // --------- Operações do carrinho (não tocam no form) ----------
  const updateQuantity = React.useCallback(
    (id: string, delta: number) => {
      setCartItems((prev) =>
        prev
          .map((it) =>
            it.id === id
              ? { ...it, quantity: Math.max(1, it.quantity + delta) }
              : it
          )
          .filter((it) => it.quantity > 0)
      );
      // ⚠️ não redefina o formulário aqui
    },
    [setCartItems]
  );

  const removeItem = React.useCallback(
    (id: string) => {
      setCartItems((prev) => prev.filter((it) => it.id !== id));
      // ⚠️ não redefina o formulário aqui
    },
    [setCartItems]
  );

  // --------- Totais ----------
  const subtotal = React.useMemo(
    () => cartItems.reduce((acc, it) => acc + it.price * it.quantity, 0),
    [cartItems]
  );
  const shipping = React.useMemo(() => {
    // substitua pela sua lógica real de frete
    return 0;
  }, []);
  const total = subtotal + shipping;

  // --------- Navegação ----------
  const onNavigateBack = React.useCallback(() => {
    // ex.: navigate("/"); ou window.history.back();
    window.history.back();
  }, []);

  // --------- Pagamentos ----------
  const handlePixCheckout = React.useCallback(() => {
    // valide e chame seu backend de PIX
    console.log("PIX com:", { form, cartItems, total });
  }, [form, cartItems, total]);

  const handleCardCheckout = React.useCallback(() => {
    // valide e siga para a etapa de cartão (tokenização Efí etc.)
    console.log("CARD com:", { form, cartItems, total });
  }, [form, cartItems, total]);

  return (
    <div className="container mx-auto p-4">
      {/* ⚠️ NÃO use key dinâmica baseada no carrinho aqui */}
      <CheckoutFormView
        cartItems={cartItems}
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
    </div>
  );
}
