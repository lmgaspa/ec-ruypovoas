import React from 'react';
import { useNavigate } from 'react-router-dom';

interface RelatedBook {
  id: string;
  title: string;
  imageUrl: string;
  price: string;
  category: string;
}

interface RelatedBooksProps {
  relatedBooks: RelatedBook[];
}

const RelatedBooks: React.FC<RelatedBooksProps> = ({ relatedBooks }) => {
  const navigate = useNavigate();

  if (relatedBooks.length === 0) return null;

  return (
    <div className="bg-background rounded-lg shadow-lg p-8 mb-16">
      <h2 className="text-2xl font-bold text-primary mb-4">Livros do Mesmo Autor</h2>
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-12">
        {relatedBooks.map((book) => (
          <div
            key={book.id}
            className="bg-background rounded-lg shadow-md overflow-hidden cursor-pointer transition transform hover:scale-105"
            onClick={() => navigate(`/books/${book.id}`)}
          >
            <img src={book.imageUrl} alt={book.title} className="w-full h-72 object-cover rounded-t-lg" />
            <div className="p-4 text-center">
              <h3 className="text-md font-semibold text-primary uppercase">{book.category}</h3>
              <h4 className="text-lg font-bold text-primary mt-1">{book.title}</h4>
              <p className="text-secondary font-semibold">{book.price}</p>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default RelatedBooks;
