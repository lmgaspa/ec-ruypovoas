// src/utils/efi-global.d.ts
import type { CheckoutAPI } from "../types/efipay";

type GnBootstrap = {
  validForm: boolean;
  processed: boolean;
  done?: (checkout: CheckoutAPI) => void;
  ready(fn: (checkout: CheckoutAPI) => void): void;
};

declare global {
  interface Window {
    // tipo mínimo exigido (ready) + propriedades extras opcionais
    $gn?: { ready(fn: (checkout: CheckoutAPI) => void): void } & Partial<GnBootstrap>;
  }
}
export {};
