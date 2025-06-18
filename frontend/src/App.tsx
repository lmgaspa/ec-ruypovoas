import { BrowserRouter as Router, Routes, Route, useLocation } from 'react-router-dom';
import { useEffect } from 'react';
import Navbar from './components/common/Navbar';
import Footer from './components/common/Footer';
import HomePage from './pages/HomePage';
import BookPage from './pages/BookPage';
import CartPage from './pages/CartPage';
import BooksListPage from './pages/BooksListPage';
import CheckoutPage from './pages/CheckoutPage';
import PixPaymentPage from './pages/PixPaymentPage';

function ScrollToTop() {
  const location = useLocation();

  useEffect(() => {
    window.scrollTo(0, 0);
  }, [location]);

  return null;
}

function App() {
  return (
    <Router>
      <ScrollToTop />
      <Navbar />
      <main className="min-h-screen bg-background py-8">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/books" element={<BooksListPage />} />
          <Route path="/books/:id" element={<BookPage />} />
          <Route path="/cart" element={<CartPage />} />
          <Route path="/checkout" element={<CheckoutPage />} />
          <Route path="/pix" element={<PixPaymentPage />} />
        </Routes>
      </main>
      <Footer />
    </Router>
  );
}

export default App;
