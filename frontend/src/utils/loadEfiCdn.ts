// src/utils/loadEfiCdn.ts
import type { CheckoutAPI } from "../types/efipay";

const LOADED_ID = "efi-cdn-script";

type LoadOpts = { sandbox?: boolean; payeeCode?: string };

type Bootstrap = {
  validForm: boolean;
  processed: boolean;
  done?: (checkout: CheckoutAPI) => void;
  ready(fn: (checkout: CheckoutAPI) => void): void;
};

export function loadEfiCdn(options?: LoadOpts) {
  if (typeof window === "undefined") return;

  const sandbox = options?.sandbox ?? false;
  const payeeCode = options?.payeeCode ?? "cf1a4eb72fb74687e6a95a3da1bd027b";

  if (!window.$gn) {
    const bootstrap: Bootstrap = {
      validForm: true,
      processed: false,
      done: undefined,
      ready(fn) {
        this.done = fn;
      },
    };
    // castzinho para evitar a checagem de propriedades extras no literal
    window.$gn = bootstrap as unknown as { ready(fn: (checkout: CheckoutAPI) => void): void };
  }

  if (document.getElementById(LOADED_ID)) return;

  const host = sandbox
    ? "https://cobrancas-h.api.efipay.com.br"
    : "https://cobrancas.api.efipay.com.br";

  const s = document.createElement("script");
  s.type = "text/javascript";
  s.async = false;
  s.id = LOADED_ID;
  const v = Math.floor(Math.random() * 1_000_000);
  s.src = `${host}/v1/cdn/${payeeCode}/${v}`;
  document.head.appendChild(s);
}
  