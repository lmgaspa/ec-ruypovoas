import React from "react";
import { formatPrice } from "../../utils/formatPrice";
import { formatCpf, formatCep } from "../../utils/masks";
import type { CartItem } from "../../context/CartTypes";

/** Shape do formulário (separado para tipar REQUIRED_FIELDS fora do componente) */
interface CheckoutFormData {
  firstName: string;
  lastName: string;
  cpf: string;
  country: string;
  cep: string;
  address: string;
  number: string;
  complement: string; // opcional
  district: string;
  city: string;
  state: string;
  phone: string;
  email: string;
  note: string; // opcional
  delivery: string;
  payment: string;
}

interface CheckoutFormViewProps {
  cartItems: CartItem[];
  total: number;
  shipping: number;
  form: CheckoutFormData;
  handleChange: (
    e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>
  ) => void;
  updateQuantity: (id: string, delta: number) => void;
  removeItem: (id: string) => void;
  handlePixCheckout: () => void;
  handleCardCheckout: () => void;
  onNavigateBack: () => void;
}

/** Campos obrigatórios (fora do componente para satisfazer react-hooks/exhaustive-deps) */
const REQUIRED_FIELDS: (keyof CheckoutFormData)[] = [
  "firstName",
  "lastName",
  "cpf",
  "cep",
  "address",
  "number",
  "district",
  "city",
  "state",
  "phone",
  "email",
  "payment",
];

const CPF_REGEX = /^\d{3}\.\d{3}\.\d{3}-\d{2}$/; // 000.000.000-00
const CEP_REGEX = /^\d{5}-\d{3}$/;               // 00000-000
const PHONE_REGEX = /^\(\d{2}\)9\d{4}-\d{4}$/;   // (DD)90000-0000  (9 obrigatório)

/** Validação algorítmica de CPF (com dígitos verificadores) */
function isValidCpf(cpfMasked: string): boolean {
  const cpf = cpfMasked.replace(/\D/g, "");
  if (cpf.length !== 11) return false;
  if (/^(\d)\1{10}$/.test(cpf)) return false; // rejeita todos dígitos iguais

  const calcDV = (base: string, factorStart: number) => {
    let sum = 0;
    for (let i = 0; i < base.length; i++) {
      sum += Number(base[i]) * (factorStart - i);
    }
    const dv = (sum * 10) % 11;
    return dv === 10 ? 0 : dv;
  };

  const dv1 = calcDV(cpf.slice(0, 9), 10);
  if (dv1 !== Number(cpf[9])) return false;

  const dv2 = calcDV(cpf.slice(0, 10), 11);
  return dv2 === Number(cpf[10]);
}

/** Máscara estrita para telefone no formato (DD)90000-0000 */
function formatPhoneStrict(value: string): string {
  const digits = value.replace(/\D/g, "").slice(0, 11); // 2 DDD + 9 números
  const ddd = digits.slice(0, 2);
  const rest = digits.slice(2); // até 9 dígitos

  if (digits.length <= 2) return `(${ddd}`;
  if (rest.length <= 5) return `(${ddd})${rest}`;               // (71)9, (71)9000, (71)90000
  return `(${ddd})${rest.slice(0, 5)}-${rest.slice(5, 9)}`;     // (71)90000-0000
}

const CheckoutFormView: React.FC<CheckoutFormViewProps> = ({
  cartItems,
  total,
  shipping,
  form,
  handleChange,
  updateQuantity,
  removeItem,
  handlePixCheckout,
  handleCardCheckout,
  onNavigateBack,
}) => {
  // Aplica máscaras antes de delegar ao handleChange do pai (sem usar "any")
  const handleMaskedChange = (
    e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>
  ) => {
    const target = e.target as HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement;
    const { name, value } = target;
    let v = value;

    if (name === "cpf") v = formatCpf(value);            // 000.000.000-00
    else if (name === "cep") v = formatCep(value);       // 00000-000
    else if (name === "phone") v = formatPhoneStrict(value); // (DD)90000-0000

    if (v !== value) {
      // atualiza o valor do elemento antes de propagar para o handler do pai
      (target as HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement).value = v;
    }
    handleChange(e);
  };

  const missingFields = React.useMemo(
    () => REQUIRED_FIELDS.filter((f) => String(form[f] ?? "").trim().length === 0),
    [form]
  );

  const cpfFormatOk = CPF_REGEX.test(form.cpf);
  const cpfDvOk = cpfFormatOk && isValidCpf(form.cpf);
  const cepOk = CEP_REGEX.test(form.cep);
  const phoneOk = PHONE_REGEX.test(form.phone);

  // Só libera se: sem faltantes, carrinho > 0, CPF (formato + DV) ok, CEP ok e Phone ok.
  const isFormValid =
    missingFields.length === 0 &&
    cartItems.length > 0 &&
    cpfDvOk &&
    cepOk &&
    phoneOk;

  const cpfInvalid = form.cpf !== "" && !cpfDvOk;
  const cepInvalid = form.cep !== "" && !cepOk;
  const phoneInvalid = form.phone !== "" && !phoneOk;

  return (
    <div className="max-w-5xl mx-auto py-12 px-4">
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 mt-6">
        {/* FORMULÁRIO */}
        <div className="lg:col-span-2 space-y-6">
          <div className="mb-4">
            <button
              onClick={onNavigateBack}
              type="button"
              className="px-6 py-2 bg-green-600 text-white rounded-md shadow-md transition hover:bg-green-700"
            >
              ← Continuar comprando
            </button>
          </div>

          <h2 className="text-lg font-bold">DADOS DE COBRANÇA E ENTREGA</h2>

          <div className="grid grid-cols-2 gap-4">
            <input
              name="firstName"
              value={form.firstName}
              onChange={handleChange}
              placeholder="Nome *"
              className="border p-2"
              autoComplete="given-name"
            />
            <input
              name="lastName"
              value={form.lastName}
              onChange={handleChange}
              placeholder="Sobrenome *"
              className="border p-2"
              autoComplete="family-name"
            />

            <input
              name="cpf"
              value={form.cpf}
              onChange={handleMaskedChange}
              placeholder="CPF (000.000.000-00) *"
              className={`border p-2 col-span-2 ${cpfInvalid ? "border-red-500" : ""}`}
              inputMode="numeric"
              aria-invalid={cpfInvalid}
            />
            {cpfInvalid && (
              <p className="text-xs text-red-600 col-span-2">
                CPF inválido. Use o formato 000.000.000-00 (ex.: 044.094.825-80).
              </p>
            )}

            <input value="Brasil" disabled className="border p-2 col-span-2" />

            <input
              name="cep"
              value={form.cep}
              onChange={handleMaskedChange}
              placeholder="CEP (00000-000) *"
              className={`border p-2 col-span-2 ${cepInvalid ? "border-red-500" : ""}`}
              inputMode="numeric"
              aria-invalid={cepInvalid}
              autoComplete="postal-code"
            />
            {cepInvalid && (
              <p className="text-xs text-red-600 col-span-2">
                CEP inválido. Use o formato 00000-000 (ex.: 45600-730).
              </p>
            )}

            <input
              name="address"
              value={form.address}
              onChange={handleChange}
              placeholder="Endereço *"
              className="border p-2 col-span-2"
              autoComplete="address-line1"
            />
            <input
              name="number"
              value={form.number}
              onChange={handleChange}
              placeholder="Número *"
              className="border p-2"
              inputMode="numeric"
              autoComplete="address-line2"
            />
            <input
              name="complement"
              value={form.complement}
              onChange={handleChange}
              placeholder="Complemento (opcional)"
              className="border p-2"
              autoComplete="address-line3"
            />
            <input
              name="district"
              value={form.district}
              onChange={handleChange}
              placeholder="Bairro *"
              className="border p-2"
            />
            <input
              name="city"
              value={form.city}
              onChange={handleChange}
              placeholder="Cidade *"
              className="border p-2"
              autoComplete="address-level2"
            />
            <input
              name="state"
              value={form.state}
              onChange={handleChange}
              placeholder="Estado *"
              className="border p-2"
              autoComplete="address-level1"
            />

            <input
              name="phone"
              value={form.phone}
              onChange={handleMaskedChange}
              placeholder="Telefone (ex.: (71)90000-0000) *"
              className={`border p-2 col-span-2 ${phoneInvalid ? "border-red-500" : ""}`}
              inputMode="tel"
              aria-invalid={phoneInvalid}
              autoComplete="tel-national"
            />
            {phoneInvalid && (
              <p className="text-xs text-red-600 col-span-2">
                Telefone inválido. Use o formato (DD)90000-0000 (ex.: (71)90000-0000).
              </p>
            )}

            <input
              name="email"
              value={form.email}
              onChange={handleChange}
              placeholder="E-mail *"
              className="border p-2 col-span-2"
              type="email"
              autoComplete="email"
            />
          </div>

          <div>
            <h2 className="text-lg font-bold mt-6">INFORMAÇÕES ADICIONAIS</h2>
            <textarea
              name="note"
              value={form.note}
              onChange={handleChange}
              placeholder="Observações sobre seu pedido... (opcional)"
              className="border w-full p-2 h-24"
            />
          </div>
        </div>

        {/* RESUMO DO PEDIDO */}
        <div>
          <h2 className="text-lg font-bold">SEU PEDIDO</h2>

          {cartItems.map((item) => (
            <div key={item.id} className="flex items-center justify-between gap-4 border-b pb-2">
              <img src={item.imageUrl} alt={item.title} className="w-12 h-16 object-cover rounded shadow" />
              <div className="flex-1">
                <p className="text-sm font-semibold text-text-primary">{item.title}</p>
                <div className="flex items-center gap-2 mt-1">
                  <button type="button" className="bg-gray-200 px-2 py-1 rounded hover:bg-gray-300" onClick={() => updateQuantity(item.id, -1)}>-</button>
                  <span className="text-sm">{item.quantity}</span>
                  <button type="button" className="bg-gray-200 px-2 py-1 rounded hover:bg-gray-300" onClick={() => updateQuantity(item.id, 1)}>+</button>
                  <button
                    type="button"
                    className="ml-2 text-red-500 text-xs hover:underline"
                    onClick={() => removeItem(item.id)}
                  >
                    Remover
                  </button>
                </div>
              </div>
              <span className="text-sm font-medium">{formatPrice(item.price * item.quantity)}</span>
            </div>
          ))}

          <div className="flex justify-between">
            <span>Entrega</span>
            <span>{shipping > 0 ? formatPrice(shipping) : "---"}</span>
          </div>

          <div className="flex justify-between font-bold">
            <span>Total</span>
            <span>{formatPrice(total)}</span>
          </div>

          {/* ESCOLHA DO PAGAMENTO */}
          <div className="mt-6">
            <h3 className="text-md font-semibold mb-2">Forma de pagamento</h3>
            <select
              name="payment"
              value={form.payment}
              onChange={handleChange}
              className="border w-full p-2 rounded"
            >
              <option value="">Selecione...</option>
              <option value="pix">Pix</option>
              <option value="card">Cartão de crédito</option>
            </select>

            {!isFormValid && (
              <p className="text-sm text-red-600 mt-2">
                Preencha todos os campos obrigatórios. Formatos exigidos: CPF 000.000.000-00 (válido), CEP 00000-000, Telefone (DD)90000-0000.
              </p>
            )}
          </div>

          {/* BOTÕES DE PAGAMENTO (bloqueados até validar) */}
          {form.payment === "pix" && (
            <button
              onClick={handlePixCheckout}
              type="button"
              disabled={!isFormValid}
              aria-disabled={!isFormValid}
              className={`bg-green-600 text-white py-2 w-full mt-4 rounded transition ${
                !isFormValid ? "opacity-50 cursor-not-allowed" : "hover:bg-green-500"
              }`}
            >
              Finalizar Pagamento por Pix
            </button>
          )}

          {form.payment === "card" && (
            <button
              onClick={handleCardCheckout}
              type="button"
              disabled={!isFormValid}
              aria-disabled={!isFormValid}
              className={`bg-blue-600 text-white py-2 w-full mt-4 rounded transition ${
                !isFormValid ? "opacity-50 cursor-not-allowed" : "hover:bg-blue-500"
              }`}
            >
              Finalizar Pagamento com Cartão
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

export default CheckoutFormView;
