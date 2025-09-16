// src/types/efipay.ts
export interface PaymentTokenResponse {
  payment_token: string;
  card_mask?: string; // <- opcional, padroniza com o resto do app
}

export type PaymentTokenError = { error_description?: string } | null;

export interface CheckoutAPI {
  getPaymentToken(
    params: {
      brand: string;
      number: string;
      cvv: string;
      expiration_month: string;
      expiration_year: string;
      reuse: boolean;
    },
    callback: (error: PaymentTokenError, response: PaymentTokenResponse | null) => void
  ): void;

  getInstallments(
    amountInCents: number,
    brand: string,
    callback: (error: PaymentTokenError, response: unknown) => void
  ): void;
}
