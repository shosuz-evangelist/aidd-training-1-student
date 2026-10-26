import React from "react";
import { Link, useNavigate } from "react-router";
import { useAuth } from "../auth/AuthContext";

const Header: React.FC = () => {
  const { isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();
  // ユーザー名はlocalStorageから取得（本来はContextやAPIから取得すべき）
  let username = "ゲスト";
  try {
    const user = JSON.parse(localStorage.getItem("token") || "{}");
    if (user && user.name) username = user.name;
  } catch {}

  const handleLogout = () => {
    logout();
    navigate("/login");
  };

  if (!isAuthenticated) return null;

  return (
    <header
      style={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        padding: "12px 24px",
        background: "#1976d2",
        color: "#fff",
        position: "sticky",
        top: 0,
        zIndex: 1000,
      }}
    >
      <nav style={{ display: "flex", gap: "20px" }}>
        <Link
          to="/products"
          style={{ color: "#fff", textDecoration: "none", fontWeight: "bold" }}
        >
          商品
        </Link>
        <Link
          to="/cart"
          style={{ color: "#fff", textDecoration: "none", fontWeight: "bold" }}
        >
          カート
        </Link>
        <Link
          to="/customers"
          style={{ color: "#fff", textDecoration: "none", fontWeight: "bold" }}
        >
          顧客
        </Link>
        <Link
          to="/sales"
          style={{ color: "#fff", textDecoration: "none", fontWeight: "bold" }}
        >
          売上
        </Link>
        <Link
          to="/approvals"
          style={{ color: "#fff", textDecoration: "none", fontWeight: "bold" }}
        >
          承認
        </Link>
        <Link
          to="/subscription"
          style={{ color: "#fff", textDecoration: "none", fontWeight: "bold" }}
        >
          定期便
        </Link>
        <Link
          to="/courses"
          style={{ color: "#fff", textDecoration: "none", fontWeight: "bold" }}
        >
          講座
        </Link>
        <Link
          to="/reservations"
          style={{ color: "#fff", textDecoration: "none", fontWeight: "bold" }}
        >
          予約一覧
        </Link>
      </nav>
      <div style={{ display: "flex", alignItems: "center", gap: "16px" }}>
        <span>ログイン中: {username}</span>
        <button
          onClick={handleLogout}
          style={{
            background: "#fff",
            color: "#1976d2",
            border: "none",
            borderRadius: "4px",
            padding: "6px 12px",
            cursor: "pointer",
            fontWeight: "bold",
          }}
        >
          ログアウト
        </button>
      </div>
    </header>
  );
};

export default Header;
