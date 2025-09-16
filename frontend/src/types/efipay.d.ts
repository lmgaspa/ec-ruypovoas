// src/types/efipay.d.ts
export {};

declare global {
  interface Window {
    $gn: {
      ready: (fn: (checkout: CheckoutAPI) => void) => void;
    };
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
        error: { error_description?: string } | null,
        response: { payment_token: string; card_mask?: string }
      ) => void
    ): void;
  }
}
