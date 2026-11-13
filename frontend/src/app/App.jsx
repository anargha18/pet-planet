import { BrowserRouter, Route, Routes } from 'react-router-dom';
import Layout from '../components/Layout';
import { AuthProvider, LoginPage, RegisterPage } from '../features/auth';
import { CartPage, CartProvider } from '../features/cart';
import { HomePage, PetDetailPage, PetsPage } from '../features/catalog';
import { CheckoutPage } from '../features/checkout';
import { OrdersPage } from '../features/orders';

function App() {
  return (
    <AuthProvider>
      <CartProvider>
        <BrowserRouter>
          <Routes>
            <Route element={<Layout />}>
              <Route path="/" element={<HomePage />} />
              <Route path="/pets" element={<PetsPage />} />
              <Route path="/pets/:id" element={<PetDetailPage />} />
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />
              <Route path="/cart" element={<CartPage />} />
              <Route path="/checkout" element={<CheckoutPage />} />
              <Route path="/orders" element={<OrdersPage />} />
              <Route path="*" element={<HomePage />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </CartProvider>
    </AuthProvider>
  );
}

export default App;
