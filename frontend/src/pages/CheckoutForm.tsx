import React from "react";
import { useNavigate } from "react-router-dom";
import type { CartItem } from "../context/CartTypes";
import type { CheckoutFormData } from "../types/CheckoutTypes";
import CheckoutFormView from "../components/checkout/CheckoutFormView";

interface CheckoutFormProps {
  cartItems: CartItem[];
  total: number;
  shipping: number;
  form: CheckoutFormData;
  updateQuantity: (id: string, delta: number) => void;
  removeItem: (id: string) => void;
  handleChange: (
    e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>
  ) => void;
  onNavigateBack: () => void;
}

const CheckoutForm = (props: CheckoutFormProps) => {
  const navigate = useNavigate();

  const handlePixCheckout = () => {
    if (!props.cartItems.length) {
      alert("Seu carrinho está vazio.");
      return;
    }

    const requiredFields: (keyof CheckoutFormData)[] = [
      "firstName",
      "lastName",
      "cpf",
      "cep",
      "address",
      "number",
      "district",
      "city",
      "state",
      "email",
      "phone"
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
    if (cpfDigits.length !== 11) {
      alert("CPF inválido.");
      return;
    }
    if (cepDigits.length !== 8) {
      alert("CEP inválido.");
      return;
    }

    navigate("/pix", {
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