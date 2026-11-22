import { BrowserRouter, Route, Routes } from 'react-router-dom';
import Layout from '../components/Layout';
import {
  AdminDeliveriesPage,
  AdminHomePage,
  AdminInventoryPage,
  AdminOrdersPage,
  AdminPetsPage,
  AdminUsersPage,
} from '../features/admin';
import { AuthProvider, LoginPage, RegisterPage } from '../features/auth';
import { CartPage, CartProvider } from '../features/cart';
import { HomePage, PetDetailPage, PetsPage } from '../features/catalog';
import { CheckoutPage } from '../features/checkout';
import { OrderDetailPage, OrdersPage } from '../features/orders';
import { ProfilePage } from '../features/profile';
import AdminRoute from './AdminRoute';
import NotFoundPage from './NotFoundPage';
import ProtectedRoute from './ProtectedRoute';

function App() {
  return (
    // The router is outermost so the auth layer can redirect on an expired session.
    <BrowserRouter>
      <AuthProvider>
        <CartProvider>
          <Routes>
            <Route element={<Layout />}>
              <Route path="/" element={<HomePage />} />
              <Route path="/pets" element={<PetsPage />} />
              <Route path="/pets/:id" element={<PetDetailPage />} />
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />
              <Route path="/cart" element={<CartPage />} />

              <Route element={<ProtectedRoute />}>
                <Route path="/checkout" element={<CheckoutPage />} />
                <Route path="/orders" element={<OrdersPage />} />
                <Route path="/orders/:orderId" element={<OrderDetailPage />} />
                <Route path="/profile" element={<ProfilePage />} />
              </Route>

              <Route path="/admin" element={<AdminRoute />}>
                <Route index element={<AdminHomePage />} />
                <Route path="pets" element={<AdminPetsPage />} />
                <Route path="inventory" element={<AdminInventoryPage />} />
                <Route path="orders" element={<AdminOrdersPage />} />
                <Route path="users" element={<AdminUsersPage />} />
                <Route path="deliveries" element={<AdminDeliveriesPage />} />
              </Route>

              <Route path="*" element={<NotFoundPage />} />
            </Route>
          </Routes>
        </CartProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
