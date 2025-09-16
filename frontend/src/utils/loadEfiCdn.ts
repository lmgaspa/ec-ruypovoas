// src/utils/loadEfiCdn.ts
import type { CheckoutAPI } from "./efi-global";

type EfiWindow = Window & {
  __efiCheckoutReady?: boolean;
  __efiCheckout?: CheckoutAPI;
};

/**
 * Espera a SDK do Efí sinalizar que está pronta (flag __efiCheckoutReady)
 * e devolve a API (checkout) tipada. Rejeita se estourar o timeout.
 */
export function ensureEfiSdkLoaded(
  opts: { timeoutMs?: number } = {}
): Promise<CheckoutAPI> {
  const timeoutMs = opts.timeoutMs ?? 8000;
  const w = window as EfiWindow;

  // já está pronta
  if (w.__efiCheckoutReady && w.__efiCheckout) {
    return Promise.resolve(w.__efiCheckout);
  }

  return new Promise<CheckoutAPI>((resolve, reject) => {
    const start = Date.now();

    const id = window.setInterval(() => {
      if (w.__efiCheckoutReady && w.__efiCheckout) {
        clearInterval(id);
        resolve(w.__efiCheckout);
        return;
      }
      if (Date.now() - start > timeoutMs) {
        clearInterval(id);
        reject(new Error("Efí SDK não ficou pronta a tempo."));
      }
    }, 100);
  });
}
