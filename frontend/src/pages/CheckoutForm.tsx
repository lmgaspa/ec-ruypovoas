import React from "react";
import { useNavigate } from "react-router-dom";
import type { CartItem } from "../components/checkout/CheckoutFormView";
import type { CheckoutFormData } from "../components/checkout/CheckoutFormView";
import CheckoutFormView from "../components/checkout/CheckoutFormView";

interface CheckoutFormProps {
  cartItems: CartItem[];
  total: number;
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

  const handlePixCheckout = () => {
    if (!props.cartItems.length) {
      alert("Seu carrinho está vazio.");
      return;
    }

    // salva para página Pix (se você tiver uma)
    localStorage.setItem("checkoutForm", JSON.stringify(props.form));
    localStorage.setItem("cart", JSON.stringify(props.cartItems));
    navigate("/pagamento-pix");
  };

  const handleCardCheckout = () => {
    if (!props.cartItems.length) {
      alert("Seu carrinho está vazio.");
      return;
    }

    // Valida mínimas antes de ir para a página de cartão
    if (!props.form.firstName || !props.form.lastName || !props.form.email) {
      alert("Preencha os dados principais antes de continuar com cartão.");
      return;
    }

    localStorage.setItem("checkoutForm", JSON.stringify(props.form));
    localStorage.setItem("cart", JSON.stringify(props.cartItems));

    navigate("/pagamento-cartao");
  };

  return (
    <CheckoutFormView
      {...props}
      handlePixCheckout={handlePixCheckout}
      handleCardCheckout={handleCardCheckout}
    />
  );
};

export default CheckoutForm;
