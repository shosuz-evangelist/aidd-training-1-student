import React from "react";
import { Routes, Route, Navigate, useLocation } from "react-router";
import LoginPage from "./pages/LoginPage";
import ProductListPage from "./pages/ProductListPage";
import ProductDetailPage from "./pages/ProductDetailPage";
import CartPage from "./pages/CartPage";
import CustomersPage from "./pages/CustomersPage";
import SalesPage from "./pages/SalesPage";
import ApprovalPage from "./pages/ApprovalPage";
import SubscriptionPage from "./pages/SubscriptionPage";
import CourseListPage from "./pages/CourseListPage";
import CourseRegisterPage from "./pages/CourseRegisterPage";
import ReservationListPage from "./pages/ReservationListPage";
import { AuthProvider, useAuth } from "./auth/AuthContext";
import Header from "./components/Header";

function PrivateRoute({ children }: { children: React.JSX.Element }) {
  const { isAuthenticated } = useAuth();
  return isAuthenticated ? children : <Navigate to="/login" />;
}

const AppRoutes: React.FC = () => (
  <Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route
      path="/"
      element={
        <PrivateRoute>
          <ProductListPage />
        </PrivateRoute>
      }
    />
    <Route
      path="/products/:id"
      element={
        <PrivateRoute>
          <ProductDetailPage />
        </PrivateRoute>
      }
    />
    <Route
      path="/cart"
      element={
        <PrivateRoute>
          <CartPage />
        </PrivateRoute>
      }
    />
    <Route
      path="/customers"
      element={
        <PrivateRoute>
          <CustomersPage />
        </PrivateRoute>
      }
    />
    <Route
      path="/sales"
      element={
        <PrivateRoute>
          <SalesPage />
        </PrivateRoute>
      }
    />
    <Route
      path="/approvals"
      element={
        <PrivateRoute>
          <ApprovalPage />
        </PrivateRoute>
      }
    />
    <Route
      path="/subscription"
      element={
        <PrivateRoute>
          <SubscriptionPage />
        </PrivateRoute>
      }
    />
    <Route
      path="/courses"
      element={
        <PrivateRoute>
          <CourseListPage />
        </PrivateRoute>
      }
    />
    <Route
      path="/courses/new"
      element={
        <PrivateRoute>
          <CourseRegisterPage />
        </PrivateRoute>
      }
    />
    <Route
      path="/reservations"
      element={
        <PrivateRoute>
          <ReservationListPage />
        </PrivateRoute>
      }
    />
    <Route path="*" element={<Navigate to="/" />} />
  </Routes>
);

const App: React.FC = () => {
  const location = useLocation();
  const isLoginPage = location.pathname === "/login";
  return (
    <AuthProvider>
      {!isLoginPage && <Header />}
      <AppRoutes />
    </AuthProvider>
  );
};

export default App;
