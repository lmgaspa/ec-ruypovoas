// src/pages/CardPaymentPage.tsx
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";

/* ---------- Tipos e helper da SDK Efí (inline) ---------- */
type Brand = "visa" | "mastercard" | "amex";

interface PaymentTokenResponse {
  payment_token: string;
  card_mask?: string;
}
interface PaymentTokenError {
  error_description?: string;
}
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
      error: PaymentTokenError | null,
      response: PaymentTokenResponse | null
    ) => void
  ): void;
}
declare global {
  interface Window {
    __efiCheckout?: CheckoutAPI;
    __efiCheckoutReady?: boolean;
    $gn?: { ready(fn: (checkout: CheckoutAPI) => void): void };
  }
}

/** Espera a SDK do Efí (exposta no index.html) estar pronta e retorna a API. */
async function ensureEfiSdkLoaded(): Promise<CheckoutAPI> {
  if (window.__efiCheckoutReady && window.__efiCheckout) return window.__efiCheckout;

  await new Promise<void>((resolve) => {
    const tick = setInterval(() => {
      if (window.__efiCheckoutReady && window.__efiCheckout) {
        clearInterval(tick);
        resolve();
      }
    }, 100);

    // timeout "suave" para não travar — 8s
    setTimeout(() => {
      clearInterval(tick);
      resolve();
    }, 8000);
  });

  const api = window.__efiCheckout;
  if (!api) throw new Error("SDK do Efí indisponível. Verifique o script no index.html.");
  return api;
}
/* ---------- Fim helper Efí ---------- */

/** Tipos locais */
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
  complement?: string;
  district: string;
  city: string;
  state: string;
  phone: string;
  email: string;
  note?: string;
  delivery?: string;
  payment?: string;
  shipping?: number;
}

const API_BASE =
  import.meta.env.VITE_API_BASE ??
  "https://editoranossolar-3fd4fdafdb9e.herokuapp.com";

/* ---------- Helpers ---------- */
function formatCardNumber(value: string, brand: Brand): string {
  const digits = value.replace(/\D/g, "");
  if (brand === "amex") {
    const d = digits.slice(0, 15);
    return d
      .replace(/^(\d{1,4})(\d{1,6})?(\d{1,5})?$/, (_, a, b, c) =>
        [a, b, c].filter(Boolean).join(" ")
      )
      .trim();
  }
  return digits.slice(0, 16).replace(/(\d{4})(?=\d)/g, "$1 ").trim();
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
function readJson<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return fallback;
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}
/* ---------- Fim helpers ---------- */

interface CardData {
  number: string;
  holderName: string;
  expirationMonth: string; // "MM"
  expirationYear: string; // "AA"
  cvv: string; // 3/4
  brand: Brand;
}

export default function CardPaymentPage() {
  const navigate = useNavigate();

  const cart: CartItem[] = useMemo(() => readJson<CartItem[]>("cart", []), []);
  const form: CheckoutFormData = useMemo(
    () => readJson<CheckoutFormData>("checkoutForm", {} as CheckoutFormData),
    []
  );

  const shipping = Number(form?.shipping ?? 0);
  const subtotal = cart.reduce((acc, i) => acc + i.price * i.quantity, 0);
  const total = subtotal + shipping;

  useEffect(() => {
    if (!Array.isArray(cart) || cart.length === 0 || total <= 0) {
      navigate("/checkout");
    }
  }, [cart, total, navigate]);

  const [brand, setBrand] = useState<Brand>("visa");
  const [card, setCard] = useState<CardData>({
    number: "",
    holderName: "",
    expirationMonth: "",
    expirationYear: "",
    cvv: "",
    brand: "visa",
  });

  const [installments, setInstallments] = useState<number>(1);
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

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
    setCard((prev) => ({ ...prev, number: formatCardNumber(e.target.value, b) }));
  };
  const onChangeHolder = (e: React.ChangeEvent<HTMLInputElement>) => {
    setCard((prev) => ({ ...prev, holderName: e.target.value }));
  };
  const onChangeMonth = (e: React.ChangeEvent<HTMLInputElement>) => {
    setCard((prev) => ({ ...prev, expirationMonth: formatMonthStrict(e.target.value) }));
  };
  const onChangeYear = (e: React.ChangeEvent<HTMLInputElement>) => {
    setCard((prev) => ({ ...prev, expirationYear: formatYearStrict(e.target.value) }));
  };
  const onChangeCvv = (e: React.ChangeEvent<HTMLInputElement>) => {
    const b = effectiveBrand;
    setCard((prev) => ({ ...prev, cvv: formatCvv(e.target.value, b) }));
  };

  const lenOk =
    (effectiveBrand === "amex" && numberDigits.length === 15) ||
    (effectiveBrand !== "amex" && numberDigits.length === 16);
  const luhnOk = lenOk && isValidLuhn(numberDigits);
  const holderOk = card.holderName.trim().length > 0;
  const monthOk =
    /^\d{2}$/.test(card.expirationMonth) &&
    Number(card.expirationMonth) >= 1 &&
    Number(card.expirationMonth) <= 12;
  const yearOk = /^\d{2}$/.test(card.expirationYear);
  const cvvOk = new RegExp(`^\\d{${cvvLen}}$`).test(card.cvv);

  const canPay = !loading && luhnOk && holderOk && monthOk && yearOk && cvvOk && total > 0;

  const perInstallment = useMemo(() => {
    if (installments <= 1) return total;
    return Math.round((total / installments) * 100) / 100;
  }, [total, installments]);

  const handlePay = async () => {
    if (!canPay) return;
    setLoading(true);
    setErrorMsg(null);

    try {
      const checkout = await ensureEfiSdkLoaded();

      const brandToSend: Brand =
        effectiveBrand === "visa"
          ? "visa"
          : effectiveBrand === "mastercard"
          ? "mastercard"
          : "amex";

      await new Promise<void>((resolve, reject) => {
        checkout.getPaymentToken(
          {
            brand: brandToSend,
            number: numberDigits,
            cvv: card.cvv,
            expiration_month: card.expirationMonth,
            expiration_year: card.expirationYear,
            reuse: false,
          },
          async (error, response) => {
            try {
              if (error || !response?.payment_token) {
                 console.error("EFI getPaymentToken error:", error);
                reject(
                  new Error(
                    error?.error_description ?? "Erro ao gerar token de pagamento."
                  )
                );
                return;
              }

              // Envia para o endpoint de CARTÃO
              const res = await fetch(`${API_BASE}/api/checkout/card`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                  ...form,
                  payment: "card",
                  paymentToken: (response as PaymentTokenResponse).payment_token,
                  installments,
                  cartItems: cart,
                  total,
                  shipping,
                }),
              });

              if (!res.ok) {
                const txt = await res.text();
                reject(new Error(txt || `Erro HTTP ${res.status}`));
                return;
              }

              const data: {
                message?: string;
                orderId?: string;
                status?: string;
                success?: boolean;
                paid?: boolean;
              } = await res.json();

              localStorage.removeItem("cart");
              navigate(
                `/pedido-confirmado?orderId=${data.orderId}&payment=card&paid=${
                  data.paid ? "true" : "false"
                }`
              );
              resolve();
            } catch (e) {
              reject(e instanceof Error ? e : new Error("Erro inesperado."));
            }
          }
        );
      });
    } catch (e) {
      setErrorMsg(e instanceof Error ? e.message : "Falha no pagamento.");
      // eslint-disable-next-line no-console
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-md mx-auto p-6">
      <h2 className="text-xl font-semibold mb-4">Pagamento com Cartão</h2>

      <label className="block text-sm font-medium mb-1">Bandeira</label>
      <select value={brand} onChange={onChangeBrand} className="border p-2 w-full mb-4 rounded">
        <option value="visa">Visa</option>
        <option value="mastercard">Mastercard</option>
        <option value="amex">American Express</option>
      </select>

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

      <input
        value={card.holderName}
        onChange={onChangeHolder}
        placeholder="Nome impresso"
        className="border p-2 w-full mb-2"
        autoComplete="cc-name"
      />

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

      <input
        value={card.cvv}
        onChange={onChangeCvv}
        placeholder={`CVV (${cvvLen} dígitos)`}
        className="border p-2 w-full mb-4"
        inputMode="numeric"
        autoComplete="cc-csc"
      />

      <label className="block text-sm font-medium mb-1">Parcelas (sem juros)</label>
      <select
        className="border p-2 w-full rounded mb-2"
        value={installments}
        onChange={(e) => setInstallments(Number(e.target.value))}
      >
        {[1, 2, 3, 4, 5, 6].map((n) => (
          <option value={n} key={n}>
            {n}x {n === 1 ? "(à vista)" : `de R$ ${perInstallmentFor(total, n)}`} sem juros
          </option>
        ))}
      </select>
      <p className="text-sm text-gray-600 mb-4">
        {installments}x de R$ {perInstallment.toFixed(2)} (total R$ {total.toFixed(2)}) — sem juros
      </p>

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
