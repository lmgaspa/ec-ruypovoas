// src/utils/efi-global.d.ts

// Tipos mínimos necessários para usar a SDK da Efí no front

export type PaymentTokenResponse = {
  payment_token: string;
  card_mask?: string;
};

export type PaymentTokenError = {
  error_description?: string;
};

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
    callback: (
      error: PaymentTokenError | null,
      response: PaymentTokenResponse | null
    ) => void
  ): void;

  /** opcional — só declare se for usar */
  getInstallments?(
    amountInCents: number,
    brand: string,
    callback: (
      error: PaymentTokenError | null,
      response: unknown
    ) => void
  ): void;
}

export interface GnBootstrap {
  ready(fn: (checkout: CheckoutAPI) => void): void;
  validForm?: boolean;
  processed?: boolean;
  done?: unknown;
}

declare global {
  interface Window {
    /** Bootstrap injetado pelo script do Efí (index.html) */
    $gn?: GnBootstrap;

    /** API exposta por você no onReady do Efí (index.html) */
    __efiCheckout?: CheckoutAPI;
    __efiCheckoutReady?: boolean;
  }
}

export {};
