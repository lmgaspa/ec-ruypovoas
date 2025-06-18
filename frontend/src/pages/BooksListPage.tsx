import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { Book } from '../data/books';
import BookDescription from '../components/book/BookDescription';
import BookAuthor from '../components/book/BookAuthor';
import { books } from '../data/books';
import { useCart } from '../hooks/useCart';

const BooksListPage = () => {
  const navigate = useNavigate();
  const [quantity, setQuantity] = useState<{ [id: string]: number }>({});
  const { addToCart } = useCart();

  const handleAddToCart = (book: Book) => {
    addToCart(book, quantity[book.id] || 1);
    alert('Item adicionado ao carrinho!');
    navigate('/cart');
  };

  const handleIncrease = (id: string) => {
    setQuantity((prev) => ({ ...prev, [id]: (prev[id] || 1) + 1 }));
  };

  const handleDecrease = (id: string) => {
    setQuantity((prev) => {
      const updated = { ...prev };
      if (updated[id] > 1) updated[id] -= 1;
      return updated;
    });
  };

  return (
    <div className="container mx-auto mt-2 mb-8 px-4">
      <h1 className="text-4xl font-bold text-primary mb-8">Livros</h1>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
        {books.map((book) => (
          <div key={book.id} className="bg-background rounded-md shadow-md p-4">
            <img src={book.imageUrl} alt={book.title} className="w-full max-w-xs rounded-md shadow-md mb-4" />
            <h2 className="text-2xl font-semibold text-primary mb-2">{book.title}</h2>
            <p className="text-lg text-secondary mb-4">{book.price}</p>
            <div className="flex items-center gap-4 mb-4">
              <button className="px-4 py-2 bg-gray-200" onClick={() => handleDecrease(book.id)}>-</button>
              <span className="text-lg">{quantity[book.id] || 1}</span>
              <button className="px-4 py-2 bg-gray-200" onClick={() => handleIncrease(book.id)}>+</button>
              <button onClick={() => handleAddToCart(book)} className="px-6 py-2 bg-green-600 text-white rounded-md shadow-md transition hover:bg-green-700">
                Adicionar ao Carrinho
              </button>
            </div>
            <BookDescription description={book.description} />
            <BookAuthor author={book.author} />
          </div>
        ))}
      </div>
    </div>
  );
};

export default BooksListPage;
