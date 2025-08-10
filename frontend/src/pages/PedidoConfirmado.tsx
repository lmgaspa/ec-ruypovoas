import { useMemo } from "react";
import { useSearchParams, Link } from "react-router-dom";

export default function PedidoConfirmado() {
  const [params] = useSearchParams();
  const orderId = useMemo(() => params.get("orderId"), [params]);
  const name = useMemo(() => params.get("name"), [params]);

  return (
    <div className="max-w-2xl mx-auto p-6 text-center">
      <h1 className="text-2xl font-semibold mb-2">Pagamento confirmado 🎉</h1>
      {name && <p className="text-gray-700 mb-1">Obrigado, <strong>{name}</strong>!</p>}
      {orderId && <p className="text-gray-700">Pedido #{orderId}</p>}
      <p className="text-gray-600 mt-2">
        Você receberá um e-mail com os detalhes do pedido. Obrigado pela compra!
      </p>
      <Link to="/" className="inline-block mt-6 bg-black text-white px-4 py-2 rounded">
        Voltar para a loja
      </Link>
    </div>
  );
}
