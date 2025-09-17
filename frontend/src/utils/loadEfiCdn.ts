// src/utils/loadEfiCdn.ts
import type { CheckoutAPI } from "./efi-global";

type EfiWindow = Window & {
  __efiCheckoutReady?: boolean;
  __efiCheckout?: CheckoutAPI;
};

/**
 * Espera a SDK do Efí sinalizar que está pronta (window.__efiCheckoutReady)
 * e então retorna a instância CheckoutAPI (window.__efiCheckout).
 */
export async function ensureEfiSdkLoaded(): Promise<CheckoutAPI> {
  const w = window as EfiWindow;

  if (w.__efiCheckoutReady && w.__efiCheckout) {
    return w.__efiCheckout;
  }

  await new Promise<void>((resolve) => {
    const tick = setInterval(() => {
      if ((window as EfiWindow).__efiCheckoutReady && (window as EfiWindow).__efiCheckout) {
        clearInterval(tick);
        resolve();
      }
    }, 100);

    // “timeout” macio — evita travar caso o script seja bloqueado
    setTimeout(() => {
      clearInterval(tick);
      resolve();
    }, 8000);
  });

  const checkout = (window as EfiWindow).__efiCheckout;
  if (!checkout) {
    throw new Error(
      "SDK do Efí indisponível. Verifique o script com seu payee_code em index.html."
    );
  }
  return checkout;
}
