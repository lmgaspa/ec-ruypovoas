import React from "react";
import { useNavigate } from "react-router-dom";
import type { CartItem } from "../context/CartTypes";
import CheckoutFormView from "../components/checkout/CheckoutFormView";

interface CheckoutFormProps {
  cartItems: CartItem[];
  total: number;    // itens + frete
  shipping: number;
  form: {
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
  };
  updateQuantity: (id: string, delta: number) => void;
  removeItem: (id: string) => void;
  handleChange: (
    e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>
  ) => void;
  onNavigateBack: () => void;
}

const CheckoutForm: React.FC<CheckoutFormProps> = (props) => {
  const navigate = useNavigate();

  const handlePixCheckout = () => {
    if (!props.cartItems.length) {
      alert("Seu carrinho está vazio.");
      return;
    }

    const required: (keyof CheckoutFormProps["form"])[] = [
      "firstName","lastName","cpf","cep","address","number",
      "district","city","state","email","phone",
    ];
    const missing = required.find((k) => String(props.form[k] ?? "").trim() === "");
    if (missing) {
      alert("Por favor, preencha todos os campos obrigatórios.");
      return;
    }

    const cpfDigits = props.form.cpf.replace(/\D/g, "");
    const cepDigits = props.form.cep.replace(/\D/g, "");
    const phoneDigits = props.form.phone.replace(/\D/g, "");

    if (cpfDigits.length !== 11) { alert("CPF inválido."); return; }
    if (cepDigits.length !== 8) { alert("CEP inválido."); return; }
    if (phoneDigits.length !== 11 || phoneDigits[2] !== "9") {
      alert("Celular inválido. Use o formato (xx)9xxxx-xxxx."); return;
    }

    navigate("/pagamento-pix", {
      state: {
        form: props.form,
        cartItems: props.cartItems,
        total: props.total,
        shipping: props.shipping,
      },
    });
  };

  return (
    <CheckoutFormView
      {...props}
      handlePixCheckout={handlePixCheckout}
    />
  );
};

export default CheckoutForm;
