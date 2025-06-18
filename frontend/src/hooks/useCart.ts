import type { Book } from '../data/books';
import type { CartItem } from '../context/CartTypes';
import { parsePrice } from '../utils/parsePrice';

export const useCart = () => {
  const getCart = (): CartItem[] => {
    return JSON.parse(localStorage.getItem('cart') || '[]');
  };

  const saveCart = (items: CartItem[]) => {
    localStorage.setItem('cart', JSON.stringify(items));
  };

  const addToCart = (book: Book, quantity: number = 1) => {
    const price = parsePrice(book.price);
    const cart = getCart();

    const existingItem = cart.find((item) => item.id === book.id);

    if (existingItem) {
      existingItem.quantity += quantity;
    } else {
      cart.push({
        id: book.id,
        title: book.title,
        imageUrl: book.imageUrl,
        price,
        quantity,
      });
    }

    saveCart(cart);
  };

  return {
    addToCart,
    getCart,
  };
};