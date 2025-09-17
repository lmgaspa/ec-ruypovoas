// src/pages/CardPaymentPage.tsx
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";

/* ===== Tipos mínimos da SDK Efí ===== */
type PaymentTokenResponse = { payment_token: string; card_mask?: string };
type PaymentTokenError = { error_description?: string };
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
    cb: (err: PaymentTokenError | null, res: PaymentTokenResponse | null) => void
  ): void;
}
type EfiWindow = Window & {
  __efiCheckout?: CheckoutAPI;
  __efiCheckoutReady?: boolean;
  $gn?: { ready(fn: (checkout: CheckoutAPI) => void): void };
};

/* ===== Loader da SDK (script com payee_code no index.html) ===== */
async function ensureEfiSdkLoaded(): Promise<CheckoutAPI> {
  const w = window as EfiWindow;
  if (w.__efiCheckoutReady && w.__efiCheckout) return w.__efiCheckout;

  await new Promise<void>((resolve) => {
    const tick = setInterval(() => {
      const ww = window as EfiWindow;
      if (ww.__efiCheckoutReady && ww.__efiCheckout) {
        clearInterval(tick);
        resolve();
      }
    }, 100);
    setTimeout(() => {
      clearInterval(tick);
      resolve();
    }, 8000);
  });

  const checkout = (window as EfiWindow).__efiCheckout;
  if (!checkout) throw new Error("SDK do Efí indisponível no navegador.");
  return checkout;
}

/* ===== Tipos locais ===== */
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

/** Agora com todas as bandeiras aceitas pela Efí */
type Brand = "visa" | "mastercard" | "amex" | "elo" | "hipercard" | "diners";

/* ===== Config ===== */
const API_BASE =
  import.meta.env.VITE_API_BASE ??
  "https://editoranossolar-3fd4fdafdb9e.herokuapp.com";

/* ===== Helpers ===== */
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
function formatMonthStrict(v: string): string {
  let d = v.replace(/\D/g, "").slice(0, 2);
  if (d.length === 1) {
    if (Number(d) > 1) d = `0${d}`;
  } else if (d.length === 2) {
    const n = Number(d);
    if (n === 0) d = "01";
    else if (n > 12) d = "12";
  }
  return d;
}
function formatYearStrict(v: string): string {
  return v.replace(/\D/g, "").slice(0, 2);
}
function toFourDigitYear(yy: string): string {
  const d = yy.replace(/\D/g, "");
  if (d.length === 4) return d;
  if (d.length === 2) return `20${d}`;
  return d;
}
function formatCvv(v: string, brand: Brand): string {
  const max = brand === "amex" ? 4 : 3;
  return v.replace(/\D/g, "").slice(0, max);
}
/** Auto-detecção básica (Visa/Master/Amex). Outras ficam por seleção manual. */
function detectBrandFromDigits(d: string): Brand | null {
  if (/^3[47]/.test(d)) return "amex";
  if (/^4/.test(d)) return "visa";
  if (/^(5[1-5]|2(2[2-9]|[3-6]|7[01]|720))/.test(d)) return "mastercard";
  return null; // elo/hipercard/diners ficam no select
}
function isValidLuhn(num: string): boolean {
  let sum = 0,
    dbl = false;
  for (let i = num.length - 1; i >= 0; i--) {
    let n = Number(num[i]);
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

/* ===== Página ===== */
interface CardData {
  number: string;
  holderName: string;
  expirationMonth: string;
  expirationYear: string; // "AA" no input; convertida p/ "YYYY"
  cvv: string;
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
      const checkout: CheckoutAPI = await ensureEfiSdkLoaded();

      const brandToSend = effectiveBrand; // já está em minúsculas e inclui elo/hipercard/diners
      const expYear4 = toFourDigitYear(card.expirationYear);

      const paramsBase = {
        number: numberDigits,
        cvv: card.cvv,
        expiration_month: card.expirationMonth,
        expiration_year: expYear4,
        reuse: false,
      };

      await new Promise<void>((resolve, reject) => {
        const tryGet = (brandStr: string, triedUpper = false) => {
          checkout.getPaymentToken(
            { brand: brandStr, ...paramsBase },
            async (error, response) => {
              try {
                if (error) {
                  // depuração: mostra erro bruto da SDK
                  // eslint-disable-next-line no-console
                  console.error("EFI getPaymentToken error:", error);
                  reject(
                    new Error(
                      error?.error_description ?? "Erro ao gerar token de pagamento."
                    )
                  );
                  return;
                }
                if (!response?.payment_token) {
                  // algumas integrações retornam {code:200,data:{}} em vez do token -> tenta uma vez em UPPERCASE
                  // eslint-disable-next-line no-console
                  console.error("EFI getPaymentToken empty response:", response);
                  if (!triedUpper) return tryGet(brandStr.toUpperCase(), true);
                  reject(new Error("Erro ao gerar token de pagamento."));
                  return;
                }

                const token = response.payment_token;

                const res = await fetch(`${API_BASE}/api/checkout/card`, {
                  method: "POST",
                  headers: { "Content-Type": "application/json" },
                  body: JSON.stringify({
                    ...form,
                    payment: "card",
                    paymentToken: token,
                    installments,
                    cartItems: cart,
                    shipping,
                  }),
                });

                if (!res.ok) {
                  const txt = await res.text();
                  reject(new Error(txt || `Erro HTTP ${res.status}`));
                  return;
                }

                const data: { message?: string; orderId?: string; paid?: boolean } =
                  await res.json();

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
        };

        tryGet(brandToSend);
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
      <h2 className="text-xl font-semibold mb-4 text-center">Pagamento com Cartão</h2>
      <label className="block text-sm font-medium mb-1">Bandeira</label>
      <select value={brand} onChange={onChangeBrand} className="border p-2 w-full mb-4 rounded">
        <option value="visa">Visa</option>
        <option value="mastercard">Mastercard</option>
        <option value="amex">American Express</option>
        <option value="elo">Elo</option>
        <option value="hipercard">Hipercard</option>
        <option value="diners">Diners</option>
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

      {errorMsg && <div className="bg-red-50 text-red-600 p-2 mb-4 rounded">{errorMsg}</div>}

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
