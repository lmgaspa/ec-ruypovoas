import { useEffect, useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import QRCode from 'react-qr-code';
import { formatPrice } from '../utils/formatPrice';
import CartTable from '../components/cart/CartTable';
import type { CartItem } from '../context/CartTypes';
import { generatePixPayload } from '../utils/pixPayload';

const PixPaymentPage = () => {
  const navigate = useNavigate();
  const { state } = useLocation();
  const { cartItems, total, shipping } = state || {};

  const [payload, setPayload] = useState('');

  useEffect(() => {
    if (!cartItems || cartItems.length === 0) return;

    const totalComFrete = Math.round((total + shipping) * 100) / 100;

    const pixPayload = generatePixPayload({
      key: '29322022000',
      name: 'Agenor Gasparetto',
      city: 'ITABUNA',
      amount: totalComFrete,
    });

    setPayload(pixPayload);
  }, [cartItems, total, shipping]);

  const handleQuantityChange = (itemId: string, amount: number) => {
    const updated = cartItems
      .map((item: CartItem) =>
        item.id === itemId ? { ...item, quantity: Math.max(0, item.quantity + amount) } : item
      )
      .filter((item: CartItem) => item.quantity > 0);

    localStorage.setItem('cart', JSON.stringify(updated));
    window.location.reload(); // recarrega para atualizar o Pix
  };

  const handleRemoveItem = (itemId: string) => {
    const updated = cartItems.filter((item: CartItem) => item.id !== itemId);
    localStorage.setItem('cart', JSON.stringify(updated));
    window.location.reload(); // recarrega para atualizar o Pix
  };

  if (!cartItems || cartItems.length === 0 || !payload) {
    return (
      <div className="max-w-3xl mx-auto p-8 text-center">
        <h1 className="text-xl font-bold text-red-600">Carrinho vazio</h1>
        <p className="mt-4">Adicione produtos ao carrinho antes de pagar com Pix.</p>
        <button
          className="mt-6 bg-gray-300 text-gray-800 px-4 py-2 rounded hover:bg-gray-400"
          onClick={() => navigate('/books')}
        >
          Voltar para a loja
        </button>
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto p-6 text-center">
      <h1 className="text-2xl font-bold mb-4">Pagamento via Pix</h1>

      <p className="text-sm text-red-600 mb-4">Aceitamos apenas Pix como forma de pagamento.</p>

      <div className="mb-8">
        <CartTable
          items={cartItems}
          onQuantityChange={handleQuantityChange}
          onRemoveItem={handleRemoveItem}
        />
        <div className="text-right pr-4">
          <p className="text-sm">Frete: {formatPrice(shipping)}</p>
          <p className="text-xl font-bold">Total: {formatPrice(total)}</p>
        </div>
      </div>

      <div className="inline-block bg-white p-4 rounded shadow-md mb-6">
        <QRCode value={payload} size={200} />
      </div>

      <p className="text-base font-medium mb-6">
        Escaneie o QR Code ou copie o código para pagar via Pix.
      </p>

      <div className="flex justify-center">
        <button
          onClick={() => navigate('/books')}
          className="px-6 py-2 bg-green-600 text-white rounded-md shadow-md transition hover:bg-green-700"
        >
          Continuar comprando
        </button>
      </div>
    </div>
  );
};

export default PixPaymentPage;
