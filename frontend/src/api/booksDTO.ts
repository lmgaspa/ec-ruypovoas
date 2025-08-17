export type BookDTO = {
  id: string;
  title: string;
  imageUrl: string;
  price: number;      // Double no backend
  description: string;
  author: string;
  category: string;
  stock: number;
  available: boolean;
};

export async function getBookById(id: string): Promise<BookDTO> {
  const res = await fetch(`/api/books/${id}`);
  if (!res.ok) throw new Error(`Falha ao buscar livro ${id}`);
  return res.json();
}