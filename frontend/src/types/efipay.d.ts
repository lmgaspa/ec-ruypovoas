// Tipos globais e da SDK Efí (Checkout)
export {};

declare global {
  interface Window {
    __efiCheckout?: CheckoutAPI;
    __efiCheckoutReady?: boolean;
    $gn?: {
      ready(fn: (checkout: CheckoutAPI) => void): void;
    };
  }
}

export interface PaymentTokenResponse {
  payment_token: string;
  card_mask: string;
}

export type EfiInstallment = {
  quantity: number;
  value: number;
  total: number;
  has_interest?: boolean;
};

export type EfiInstallmentsResponse =
  | { installments: EfiInstallment[] }
  | EfiInstallment[];

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
      error: { error_description?: string } | null,
      response: PaymentTokenResponse | null
    ) => void
  ): void;

  getInstallments(
    total: number,
    callback: (
      error: { error_description?: string } | null,
      response: EfiInstallmentsResponse | null
    ) => void
  ): void;
}
