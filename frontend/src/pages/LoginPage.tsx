import React, { useState } from "react";
import { useNavigate } from "react-router";
import { useAuth } from "../auth/AuthContext";

const LoginPage: React.FC = () => {
  const { login } = useAuth();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const success = await login(username, password);
    if (success) {
      navigate("/");
    } else {
      setError("ログインに失敗しました");
    }
  };

  return (
    <div
      style={{
        maxWidth: 400,
        margin: "80px auto",
        background: "#fff",
        padding: 32,
        borderRadius: 8,
        boxShadow: "0 2px 8px #0001",
      }}
    >
      <h2>ログイン</h2>
      <form onSubmit={handleSubmit}>
        <div>
          <input
            type="text"
            placeholder="ユーザー名"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            style={{ width: "100%", marginBottom: 12, padding: 8 }}
          />
        </div>
        <div>
          <input
            type="password"
            placeholder="パスワード"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            style={{ width: "100%", marginBottom: 12, padding: 8 }}
          />
        </div>
        {error && <div style={{ color: "red", marginBottom: 12 }}>{error}</div>}
        <button
          type="submit"
          style={{
            width: "100%",
            padding: 10,
            background: "#1976d2",
            color: "#fff",
            border: "none",
            borderRadius: 4,
          }}
        >
          ログイン
        </button>
      </form>
    </div>
  );
};

export default LoginPage;
