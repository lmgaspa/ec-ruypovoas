/// <reference types="vite/client" />
// src/utils/efiCardToken.ts
import RawEfiPay from "payment-token-efi";

// ---- Tipos mínimos usados da lib ----
type GetPaymentTokenPayload = {
  payee_code: string;
  brand: string;
  card_number: string;
  card_cvv: string;
  holder_name: string;
  expiration_month: string;
  expiration_year: string;
  reuse?: boolean;
  sandbox?: boolean;
};

type GetPaymentTokenResult = {
  payment_token?: string;
  card_mask?: string;
  error?: string;
  error_description?: string;
};

interface EfiPayApi {
  getPaymentToken(payload: GetPaymentTokenPayload): Promise<GetPaymentTokenResult>;
  isBlocked?: () => Promise<boolean>;
  getBrand?: (opts: { card_number: string }) => Promise<{ brand?: string }>;
}

// Cast sem `any`
const EfiPay = RawEfiPay as unknown as EfiPayApi;

const onlyDigits = (s: string) => s.replace(/\D+/g, "");
const toYYYY = (y: string) => (y.length === 2 ? `20${y}` : y);

export async function getPaymentTokenEfi(opts: {
  brand: string;
  number: string;
  holder: string;
  month: string;
  year: string;
  cvv: string;
  reuse?: boolean;
}) {
  const payeeCode = import.meta.env.VITE_EFI_PAYEE_CODE as string | undefined;
  if (!payeeCode) throw new Error("VITE_EFI_PAYEE_CODE não configurado.");

  if (!EfiPay || typeof EfiPay.getPaymentToken !== "function") {
    throw new Error("Biblioteca payment-token-efi não carregada.");
  }

  // Check opcional
  try {
    if (typeof EfiPay.isBlocked === "function" && (await EfiPay.isBlocked())) {
      throw new Error("Tokenização bloqueada por extensão/AD-block.");
    }
  } catch {
    /* ignora se indisponível */
  }

  const isSandbox = String(import.meta.env.VITE_EFI_SANDBOX ?? "false") === "true";

  const payload: GetPaymentTokenPayload = {
    payee_code: payeeCode,
    brand: String(opts.brand || "").toLowerCase(),
    card_number: onlyDigits(opts.number),
    card_cvv: onlyDigits(opts.cvv),
    holder_name: opts.holder.trim(),
    expiration_month: onlyDigits(opts.month).padStart(2, "0"),
    expiration_year: toYYYY(onlyDigits(opts.year)),
    reuse: !!opts.reuse,
    sandbox: isSandbox,
  };

  const res = await EfiPay.getPaymentToken(payload);
  if (!res?.payment_token) {
    throw new Error(res?.error_description || res?.error || "Falha ao gerar payment_token.");
  }

  return {
    paymentToken: res.payment_token,
    cardMask: res.card_mask ?? "",
  };
}

export async function detectCardBrand(cardNumber: string): Promise<string | null> {
  if (!EfiPay || typeof EfiPay.getBrand !== "function") return null;
  const res = await EfiPay.getBrand({ card_number: onlyDigits(cardNumber) });
  return res?.brand ? String(res.brand).toLowerCase() : null;
}
