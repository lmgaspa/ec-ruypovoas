import React from "react";
import { useNavigate } from "react-router-dom";
import type { CartItem } from "../context/CartTypes";
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

    navigate("/pix", {
      state: {
        form: props.form,
        cartItems: props.cartItems,
        total: props.total,
        shipping: props.shipping,
      },
    });
  };

  const handleCardCheckout = () => {
    if (!props.cartItems.length) {
      alert("Seu carrinho está vazio.");
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
