import { useMemo } from "react";
import { useSearchParams, Link } from "react-router-dom";

export default function PedidoConfirmado() {
  const [params] = useSearchParams();
  const orderId = useMemo(() => params.get("orderId"), [params]);

  const { firstName, lastName } = useMemo(() => {
    const savedForm = localStorage.getItem("checkoutForm");
    if (!savedForm) return { firstName: "", lastName: "" };
    try {
      const form = JSON.parse(savedForm);
      return {
        firstName: form.firstName || "",
        lastName: form.lastName || "",
      };
    } catch {
      return { firstName: "", lastName: "" };
    }
  }, []);

  return (
    <div className="max-w-2xl mx-auto p-6 text-center">
      <h1 className="text-2xl font-semibold mb-2">Pagamento confirmado 🎉</h1>

      {firstName && (
        <p className="text-lg text-gray-800">
          Obrigado, {firstName} {lastName}!
        </p>
      )}

      {orderId && (
        <p className="text-gray-700 mt-1">Pedido #{orderId}</p>
      )}

      <p className="text-gray-600 mt-2">
        Você receberá um e-mail com os detalhes do pedido. Obrigado pela compra!
      </p>

      <Link
        to="/"
        className="inline-block mt-6 bg-black text-white px-4 py-2 rounded"
      >
        Voltar para a loja
      </Link>
    </div>
  );
}
