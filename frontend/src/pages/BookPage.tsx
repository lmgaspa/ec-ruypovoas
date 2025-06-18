import { useParams } from 'react-router-dom';
import { books } from '../data/books';
import BookDetails from '../components/book/BookDetails';

function BookPage() {
  const { id } = useParams<{ id: string }>();
  const book = books.find((book) => book.id === id);

  if (!book) return <p>Livro não encontrado</p>;

  return <BookDetails {...book} />;
}

export default BookPage;
