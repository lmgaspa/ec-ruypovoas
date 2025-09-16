import React from "react";
import { useNavigate } from "react-router-dom";
import type { CartItem } from "../context/CartTypes";
import type { CheckoutFormData } from "../types/checkoutTypes";
import CheckoutFormView from "../components/checkout/CheckoutFormView";

interface CheckoutFormProps {
  cartItems: CartItem[];
  total: number;    // itens + frete
  shipping: number;
  form: CheckoutFormData;
  updateQuantity: (id: string, delta: number) => void;
  removeItem: (id: string) => void;
  handleChange: (
    e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>
  ) => void;
  onNavigateBack: () => void;
}

const CheckoutForm: React.FC<CheckoutFormProps> = (props) => {
  const navigate = useNavigate();

  const handleCheckout = () => {
    if (!props.cartItems.length) {
      alert("Seu carrinho está vazio.");
      return;
    }

    const requiredFields: (keyof CheckoutFormData)[] = [
      "firstName","lastName","cpf","cep","address","number",
      "district","city","state","email","phone",
    ];
    const missingField = requiredFields.find(
      (field) => String(props.form[field] ?? "").trim() === ""
    );
    if (missingField) {
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

    // Decisão por forma de pagamento
    if (props.form.payment === "pix") {
      navigate("/pix", {
        state: {
          form: props.form,
          cartItems: props.cartItems,
          total: props.total,
          shipping: props.shipping,
        },
      });
      return;
    }

    if (props.form.payment === "card") {
      // Nada de coletar cartão aqui. Só ir para a tela de cartão.
      // O CardPaymentPage já lê 'cart' e 'checkoutForm' do localStorage
      // e faz a tokenização Efí.
      navigate("/card");
      return;
    }

    alert("Forma de pagamento inválida.");
  };

  return (
    <CheckoutFormView
      {...props}
      handleCheckout={handleCheckout}
    />
  );
};

export default CheckoutForm;
