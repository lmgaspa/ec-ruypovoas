// src/pages/CardPaymentPage.tsx
import { useNavigate } from "react-router-dom";
import { useEffect, useMemo, useState } from "react";
import type { PaymentTokenResponse } from "../types/efipay";
import { loadEfiCdn } from "../utils/loadEfiCdn";

const EFI_SANDBOX = import.meta.env.VITE_EFI_SANDBOX === "false"; // opcional
const EFI_PAYEE_CODE =
  import.meta.env.VITE_EFI_PAYEE_CODE || "cf1a4eb72fb74687e6a95a3da1bd027b"; // opcional
/** Tipos locais (somente para esta página) */
interface CartItem {
  id: string;
  title: string;
  price: number;
  quantity: number;
  imageUrl: string;
}
interface CheckoutFormData {
  firstName: string;
  lastName: string;
  cpf: string;
  country: string;
  cep: string;
  address: string;
  number: string;
  complement: string;
  district: string;
  city: string;
  state: string;
  phone: string;
  email: string;
  note: string;
  delivery: string;
  payment: string;
  shipping?: number;
}

type Brand = "visa" | "mastercard" | "amex";
interface CardData {
  number: string;
  holderName: string;
  expirationMonth: string; // "MM"
  expirationYear: string; // "AA"
  cvv: string; // 3 (Visa/Master) / 4 (Amex)
  brand: Brand;
}

const API_BASE =
  import.meta.env.VITE_API_BASE ??
  "https://editoranossolar-3fd4fdafdb9e.herokuapp.com";

/* ---------- Helpers de máscara/validação ---------- */
function formatCardNumber(value: string, brand: Brand): string {
  const digits = value.replace(/\D/g, "");
  if (brand === "amex") {
    // 15: 4-6-5
    const d = digits.slice(0, 15);
    return d
      .replace(/^(\d{1,4})(\d{1,6})?(\d{1,5})?$/, (_, a, b, c) =>
        [a, b, c].filter(Boolean).join(" ")
      )
      .trim();
  }
  // visa/master 16: 4-4-4-4
  return digits
    .slice(0, 16)
    .replace(/(\d{4})(?=\d)/g, "$1 ")
    .trim();
}
function formatMonthStrict(value: string): string {
  let d = value.replace(/\D/g, "").slice(0, 2);
  if (d.length === 1) {
    if (Number(d) > 1) d = `0${d}`;
  } else if (d.length === 2) {
    const n = Number(d);
    if (n === 0) d = "01";
    else if (n > 12) d = "12";
  }
  return d;
}
function formatYearStrict(value: string): string {
  return value.replace(/\D/g, "").slice(0, 2);
}
function formatCvv(value: string, brand: Brand): string {
  const max = brand === "amex" ? 4 : 3;
  return value.replace(/\D/g, "").slice(0, max);
}
function detectBrandFromDigits(digits: string): Brand | null {
  if (/^3[47]/.test(digits)) return "amex";
  if (/^4/.test(digits)) return "visa";
  if (/^(5[1-5]|2(2[2-9]|[3-6]|7[01]|720))/.test(digits)) return "mastercard";
  return null;
}
function isValidLuhn(numDigits: string): boolean {
  let sum = 0,
    dbl = false;
  for (let i = numDigits.length - 1; i >= 0; i--) {
    let n = Number(numDigits[i]);
    if (dbl) {
      n *= 2;
      if (n > 9) n -= 9;
    }
    sum += n;
    dbl = !dbl;
  }
  return sum % 10 === 0;
}
function perInstallmentFor(total: number, n: number): string {
  const v = Math.round((total / n) * 100) / 100;
  return v.toFixed(2);
}
/* ---------- Fim helpers ---------- */

export default function CardPaymentPage() {
  const navigate = useNavigate();

  const cart: CartItem[] = useMemo(
    () => JSON.parse(localStorage.getItem("cart") || "[]"),
    []
  );
  const form: CheckoutFormData = useMemo(
    () => JSON.parse(localStorage.getItem("checkoutForm") || "{}"),
    []
  );

  const shipping = Number(form?.shipping ?? 0);
  const total =
    cart.reduce((acc, i) => acc + i.price * i.quantity, 0) + shipping;

  const [brand, setBrand] = useState<Brand>("visa");
  const [card, setCard] = useState<CardData>({
    number: "",
    holderName: "",
    expirationMonth: "",
    expirationYear: "",
    cvv: "",
    brand: "visa",
  });

  const [installments, setInstallments] = useState<number>(1); // 1..6 sem juros
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  // carrega o SDK Efí com seu payee_code
  useEffect(() => {
    loadEfiCdn({ sandbox: EFI_SANDBOX, payeeCode: EFI_PAYEE_CODE });
  }, []);

  // Proteção: se não houver carrinho/total válido, volta ao checkout
  if (!Array.isArray(cart) || cart.length === 0 || total <= 0) {
    // Evita loop infinito se essa página for aberta direto sem contexto
    // Você pode trocar por um link/CTA se preferir
  }

  // masks
  const numberDigits = card.number.replace(/\D/g, "");
  const inferredBrand = detectBrandFromDigits(numberDigits);
  const effectiveBrand: Brand = inferredBrand ?? brand;
  const cvvLen = effectiveBrand === "amex" ? 4 : 3;

  const onChangeBrand = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const newBrand = e.target.value as Brand;
    setBrand(newBrand);
    setCard((prev) => ({
      ...prev,
      brand: newBrand,
      number: formatCardNumber(prev.number, newBrand),
      cvv: formatCvv(prev.cvv, newBrand),
    }));
  };
  const onChangeNumber = (e: React.ChangeEvent<HTMLInputElement>) => {
    const b = effectiveBrand;
    setCard((prev) => ({
      ...prev,
      number: formatCardNumber(e.target.value, b),
    }));
  };
  const onChangeHolder = (e: React.ChangeEvent<HTMLInputElement>) => {
    setCard((prev) => ({ ...prev, holderName: e.target.value }));
  };
  const onChangeMonth = (e: React.ChangeEvent<HTMLInputElement>) => {
    setCard((prev) => ({
      ...prev,
      expirationMonth: formatMonthStrict(e.target.value),
    }));
  };
  const onChangeYear = (e: React.ChangeEvent<HTMLInputElement>) => {
    setCard((prev) => ({
      ...prev,
      expirationYear: formatYearStrict(e.target.value),
    }));
  };
  const onChangeCvv = (e: React.ChangeEvent<HTMLInputElement>) => {
    const b = effectiveBrand;
    setCard((prev) => ({ ...prev, cvv: formatCvv(e.target.value, b) }));
  };

  // validações
  const digits = numberDigits;
  const lenOk =
    (effectiveBrand === "amex" && digits.length === 15) ||
    (effectiveBrand !== "amex" && digits.length === 16);
  const luhnOk = lenOk && isValidLuhn(digits);
  const holderOk = card.holderName.trim().length > 0;
  const monthOk =
    /^\d{2}$/.test(card.expirationMonth) &&
    Number(card.expirationMonth) >= 1 &&
    Number(card.expirationMonth) <= 12;
  const yearOk = /^\d{2}$/.test(card.expirationYear);
  const cvvOk = new RegExp(`^\\d{${cvvLen}}$`).test(card.cvv);

  const canPay =
    !loading && luhnOk && holderOk && monthOk && yearOk && cvvOk && total > 0;

  const perInstallment = useMemo(() => {
    if (installments <= 1) return total;
    return Math.round((total / installments) * 100) / 100;
  }, [total, installments]);

  const handlePay = () => {
    if (!canPay) return;
    setLoading(true);
    setErrorMsg(null);

    if (!window.$gn) {
      setErrorMsg(
        "SDK de pagamentos não carregou ainda. Tente novamente em alguns segundos."
      );
      setLoading(false);
      return;
    }

    const brandToSend =
      effectiveBrand === "visa"
        ? "visa"
        : effectiveBrand === "mastercard"
        ? "mastercard"
        : "amex";

    window.$gn.ready((checkout) => {
      checkout.getPaymentToken(
        {
          brand: brandToSend,
          number: digits,
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

          try {
            const res = await fetch(`${API_BASE}/api/checkout`, {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({
                ...form,
                payment: "card",
                cardToken: (response as PaymentTokenResponse).payment_token,
                installments, // até 6 sem juros
                cartItems: cart,
                total,
                shipping,
              }),
            });

            if (!res.ok) throw new Error(await res.text());
            const data: { message?: string; orderId?: string; paid?: boolean } =
              await res.json();

            localStorage.removeItem("cart");
            navigate(
              `/pedido-confirmado?orderId=${data.orderId}&payment=card&paid=${
                data.paid ? "true" : "false"
              }`
            );
          } catch (e) {
            setErrorMsg(
              e instanceof Error ? e.message : "Erro inesperado no pagamento."
            );
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

      {/* Bandeira */}
      <label className="block text-sm font-medium mb-1">Bandeira</label>
      <select
        value={brand}
        onChange={onChangeBrand}
        className="border p-2 w-full mb-4 rounded"
      >
        <option value="visa">Visa</option>
        <option value="mastercard">Mastercard</option>
        <option value="amex">American Express</option>
      </select>

      {/* Número */}
      <input
        value={card.number}
        onChange={onChangeNumber}
        placeholder={
          effectiveBrand === "amex"
            ? "Número do cartão (ex.: 3714 496353 98431)"
            : "Número do cartão (ex.: 4111 1111 1111 1111)"
        }
        className="border p-2 w-full mb-2"
        inputMode="numeric"
        autoComplete="cc-number"
      />

      {/* Nome */}
      <input
        value={card.holderName}
        onChange={onChangeHolder}
        placeholder="Nome impresso"
        className="border p-2 w-full mb-2"
        autoComplete="cc-name"
      />

      {/* MM / AA */}
      <div className="flex gap-2">
        <input
          value={card.expirationMonth}
          onChange={onChangeMonth}
          placeholder="MM"
          className="border p-2 w-1/2 mb-2"
          inputMode="numeric"
          autoComplete="cc-exp-month"
        />
        <input
          value={card.expirationYear}
          onChange={onChangeYear}
          placeholder="AA"
          className="border p-2 w-1/2 mb-2"
          inputMode="numeric"
          autoComplete="cc-exp-year"
        />
      </div>

      {/* CVV */}
      <input
        value={card.cvv}
        onChange={onChangeCvv}
        placeholder={`CVV (${cvvLen} dígitos)`}
        className="border p-2 w-full mb-4"
        inputMode="numeric"
        autoComplete="cc-csc"
      />

      {/* Parcelas sem juros */}
      <label className="block text-sm font-medium mb-1">
        Parcelas (sem juros)
      </label>
      <select
        className="border p-2 w-full rounded mb-2"
        value={installments}
        onChange={(e) => setInstallments(Number(e.target.value))}
      >
        {[1, 2, 3, 4, 5, 6].map((n) => (
          <option value={n} key={n}>
            {n}x{" "}
            {n === 1 ? "(à vista)" : `de R$ ${perInstallmentFor(total, n)}`} sem
            juros
          </option>
        ))}
      </select>
      <p className="text-sm text-gray-600 mb-4">
        {installments}x de R$ {perInstallment.toFixed(2)} (total R${" "}
        {total.toFixed(2)}) — sem juros
      </p>

      {/* Erro */}
      {errorMsg && (
        <div className="bg-red-50 text-red-600 p-2 mb-4 rounded">
          {errorMsg}
        </div>
      )}

      <button
        disabled={!canPay}
        onClick={handlePay}
        className={`bg-blue-600 text-white py-2 w-full rounded ${
          canPay ? "hover:bg-blue-500" : "opacity-50 cursor-not-allowed"
        }`}
      >
        {loading ? "Processando..." : "Pagar com Cartão"}
      </button>
    </div>
  );
}
