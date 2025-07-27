import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCart } from '../hooks/useCart';
import { formatPrice } from '../utils/formatPrice';
import { calcularFreteComBaseEmCarrinho } from '../utils/freteUtils';

const PixPaymentPage = () => {
  const { getCart } = useCart();
  const navigate = useNavigate();
  const cartItems = getCart();

  const [frete, setFrete] = useState(0);
  const [qrCodeBase64, setQrCodeBase64] = useState('');
  const [loading, setLoading] = useState(false);

  const totalProdutos = cartItems.reduce(
    (sum, item) => sum + item.price * item.quantity,
    0
  );
  const totalComFrete = totalProdutos + frete;

  useEffect(() => {
    const savedForm = localStorage.getItem('checkoutForm');
    if (!savedForm) return;

    const form = JSON.parse(savedForm);
    calcularFreteComBaseEmCarrinho(
      { cep: form.cep, cpf: form.cpf },
      cartItems
    )
      .then(setFrete)
      .catch(() => setFrete(0));
  }, [cartItems]);

  useEffect(() => {
    const savedForm = localStorage.getItem('checkoutForm');
    if (!savedForm || cartItems.length === 0) return;

    const form = JSON.parse(savedForm);
    setLoading(true);

    fetch('https://ecommerceag-6fa0e6a5edbf.herokuapp.com/api/checkout', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        firstName: form.firstName,
        lastName: form.lastName,
        email: form.email,
        address: form.address,
        city: form.city,
        state: form.state,
        cep: form.cep,
        shipping: frete,
        total: totalProdutos,
        cartItems
      })
    })
      .then((res) => res.json())
      .then((data) => {
        setQrCodeBase64(data.qrCodeBase64);
      })
      .catch((err) => {
        console.error('Erro ao gerar pagamento Pix:', err);
      })
      .finally(() => setLoading(false));
  }, [frete]);

  const handleReviewClick = () => {
    navigate('/checkout');
  };

  return (
    <div className="max-w-3xl mx-auto p-6">
      <h2 className="text-2xl font-semibold mb-4">Resumo da Compra (Pix)</h2>

      <div className="space-y-4">
        {cartItems.map((item) => (
          <div
            key={item.id}
            className="border p-4 rounded shadow-sm flex gap-4 items-center"
          >
            <img
              src={item.imageUrl}
              alt={item.title}
              className="w-24 h-auto object-contain"
            />
            <div>
              <p className="font-medium">{item.title}</p>
              <p>Quantidade: {item.quantity}</p>
              <p>Preço unitário: {formatPrice(item.price)}</p>
              <p>Subtotal: {formatPrice(item.price * item.quantity)}</p>
            </div>
          </div>
        ))}
      </div>

      <div className="mt-6 text-right space-y-2">
        <p className="text-lg">Subtotal: {formatPrice(totalProdutos)}</p>
        <p className="text-lg">Frete: {formatPrice(frete)}</p>
        <p className="text-xl font-bold">
          Total com Frete: {formatPrice(totalComFrete)}
        </p>
      </div>

      <div className="mt-8 flex justify-between">
        <button
          onClick={handleReviewClick}
          className="bg-gray-300 hover:bg-gray-400 text-black px-4 py-2 rounded"
        >
          Revisar compra
        </button>
      </div>

      {loading && (
        <p className="text-center mt-8 text-gray-600">Gerando QR Code Pix...</p>
      )}

      {qrCodeBase64 && (
        <div className="mt-10 text-center">
          <p className="text-lg mb-2 font-medium">Escaneie o QR Code com seu app de banco:</p>
          <img
            src={`data:image/png;base64,${qrCodeBase64}`}
            alt="QR Code Pix"
            className="mx-auto"
          />
        </div>
      )}
    </div>
  );
};

export default PixPaymentPage;
