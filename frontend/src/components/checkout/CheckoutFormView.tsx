import React from "react";
import { formatPrice } from "../../utils/formatPrice";
import { formatCpf, formatCep } from "../../utils/masks";
import type { CartItem } from "../../context/CartTypes";

/** Shape do formulário */
export interface CheckoutFormData {
  firstName: string;
  lastName: string;
  cpf: string;
  country: string;
  cep: string;
  address: string;
  number: string;
  complement: string;
  district: string;
  city: string;
  state: string;
  phone: string;
  email: string;
  note: string;
  delivery: string;
  payment: string;
}

export interface CheckoutFormViewProps {
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

/** Campos obrigatórios */
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

const CPF_REGEX = /^\d{3}\.\d{3}\.\d{3}-\d{2}$/;
const CEP_REGEX = /^\d{5}-\d{3}$/;
const PHONE_REGEX = /^\(\d{2}\)9\d{4}-\d{4}$/;

function isValidCpf(cpfMasked: string): boolean {
  const cpf = cpfMasked.replace(/\D/g, "");
  if (cpf.length !== 11) return false;
  if (/^(\d)\1{10}$/.test(cpf)) return false;

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

function formatPhoneStrict(value: string): string {
  const digits = value.replace(/\D/g, "").slice(0, 11);
  const ddd = digits.slice(0, 2);
  const rest = digits.slice(2);

  if (digits.length <= 2) return `(${ddd}`;
  if (rest.length <= 5) return `(${ddd})${rest}`;
  return `(${ddd})${rest.slice(0, 5)}-${rest.slice(5, 9)}`;
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
  const handleMaskedChange = (
    e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>
  ) => {
    const target = e.target;
    const { name, value } = target;
    let v = value;

    if (name === "cpf") v = formatCpf(value);
    else if (name === "cep") v = formatCep(value);
    else if (name === "phone") v = formatPhoneStrict(value);

    if (v !== value) {
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

  const isFormValid =
    missingFields.length === 0 &&
    cartItems.length > 0 &&
    cpfDvOk &&
    cepOk &&
    phoneOk;

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
            {/* Campos principais */}
            <input name="firstName" value={form.firstName} onChange={handleChange} placeholder="Nome *" className="border p-2" />
            <input name="lastName" value={form.lastName} onChange={handleChange} placeholder="Sobrenome *" className="border p-2" />

            <input
              name="cpf"
              value={form.cpf}
              onChange={handleMaskedChange}
              placeholder="CPF (000.000.000-00) *"
              className="border p-2 col-span-2"
            />

            <input value="Brasil" disabled className="border p-2 col-span-2" />

            <input
              name="cep"
              value={form.cep}
              onChange={handleMaskedChange}
              placeholder="CEP (00000-000) *"
              className="border p-2 col-span-2"
            />

            <input name="address" value={form.address} onChange={handleChange} placeholder="Endereço *" className="border p-2 col-span-2" />
            <input name="number" value={form.number} onChange={handleChange} placeholder="Número *" className="border p-2" />
            <input name="complement" value={form.complement} onChange={handleChange} placeholder="Complemento" className="border p-2" />
            <input name="district" value={form.district} onChange={handleChange} placeholder="Bairro *" className="border p-2" />
            <input name="city" value={form.city} onChange={handleChange} placeholder="Cidade *" className="border p-2" />
            <input name="state" value={form.state} onChange={handleChange} placeholder="Estado *" className="border p-2" />

            <input
              name="phone"
              value={form.phone}
              onChange={handleMaskedChange}
              placeholder="Telefone (ex.: (71)90000-0000) *"
              className="border p-2 col-span-2"
            />

            <input name="email" value={form.email} onChange={handleChange} placeholder="E-mail *" className="border p-2 col-span-2" type="email" />
          </div>
        </div>

        {/* RESUMO DO PEDIDO */}
        <div>
          <h2 className="text-lg font-bold">SEU PEDIDO</h2>

          {cartItems.map((item) => (
            <div key={item.id} className="flex items-center justify-between gap-4 border-b pb-2">
              <img src={item.imageUrl} alt={item.title} className="w-12 h-16 object-cover rounded shadow" />
              <div className="flex-1">
                <p className="text-sm font-semibold">{item.title}</p>
                <div className="flex items-center gap-2 mt-1">
                  <button type="button" onClick={() => updateQuantity(item.id, -1)}>-</button>
                  <span>{item.quantity}</span>
                  <button type="button" onClick={() => updateQuantity(item.id, 1)}>+</button>
                  <button type="button" onClick={() => removeItem(item.id)} className="text-red-500 text-xs">Remover</button>
                </div>
              </div>
              <span>{formatPrice(item.price * item.quantity)}</span>
            </div>
          ))}

          <div className="flex justify-between"><span>Entrega</span><span>{shipping > 0 ? formatPrice(shipping) : "---"}</span></div>
          <div className="flex justify-between font-bold"><span>Total</span><span>{formatPrice(total)}</span></div>

          {/* PAGAMENTO */}
          <div className="mt-6">
            <h3 className="text-md font-semibold mb-2">Forma de pagamento</h3>
            <select name="payment" value={form.payment} onChange={handleChange} className="border w-full p-2 rounded">
              <option value="">Selecione...</option>
              <option value="pix">Pix</option>
              <option value="card">Cartão de crédito</option>
            </select>
          </div>

          {form.payment === "pix" && (
            <button onClick={handlePixCheckout} type="button" disabled={!isFormValid} className="bg-green-600 text-white py-2 w-full mt-4 rounded">
              Finalizar Pagamento por Pix
            </button>
          )}
          {form.payment === "card" && (
            <button onClick={handleCardCheckout} type="button" disabled={!isFormValid} className="bg-blue-600 text-white py-2 w-full mt-4 rounded">
              Finalizar Pagamento com Cartão
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

export default CheckoutFormView;
