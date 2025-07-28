import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { Book } from '../../data/books';
import BookDescription from './BookDescription';
import BookAuthor from './BookAuthor';
import AdditionalInfo from './AdditionalInfo';
import RelatedBooks from './RelatedBooks';
import AuthorInfo from './AuthorInfo';
import ButtonCountCart from '../cart/ButtonCountCart';
import { useCart } from '../../hooks/useCart';

type BookDetailsProps = Book;

const BookDetails = ({
  id,
  title,
  imageUrl,
  price,
  description,
  additionalInfo,
  author,
  relatedBooks,
}: BookDetailsProps) => {
  const [quantity, setQuantity] = useState(1);
  const navigate = useNavigate();
  const { addToCart } = useCart();

  const handleAddToCart = () => {
    addToCart(
      {
        id,
        title,
        imageUrl,
        price,
        description,
        author,
        additionalInfo,
        category: '',
        relatedBooks,
        stock: 100,
      },
      quantity
    );
    alert('Item adicionado ao carrinho!');
    navigate('/cart');
  };

  const handleIncrease = () => setQuantity((prev) => prev + 1);
  const handleDecrease = () => quantity > 1 && setQuantity((prev) => prev - 1);

  return (
    <div className="container mx-auto my-16 px-4">
      <div className="flex flex-col md:flex-row items-start gap-16 mb-16">
        <div className="w-full md:w-1/3 flex justify-center">
          <img src={imageUrl} alt={title} className="w-full max-w-xs rounded-md shadow-md" />
        </div>
        <div className="flex-1">
          <h1 className="text-4xl font-bold text-primary mb-4">{title}</h1>
          <p className="text-3xl text-secondary font-semibold mb-4">{price}</p>
          <BookDescription description={description} />
          <BookAuthor author={author} />

          <div className="flex items-center gap-4 mb-8">
            <ButtonCountCart
              quantity={quantity}
              onDecrease={handleDecrease}
              onIncrease={handleIncrease}
            />
            <button onClick={handleAddToCart} className="px-6 py-2 bg-green-600 text-white rounded-md shadow-md transition hover:bg-green-700">
              Adicionar ao Carrinho
            </button>
          </div>
        </div>
      </div>

      <AdditionalInfo additionalInfo={additionalInfo} />
      <RelatedBooks relatedBooks={relatedBooks} />
      <AuthorInfo author={author} />
    </div>
  );
};

export default BookDetails;
