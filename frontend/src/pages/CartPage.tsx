import React, { useEffect, useState } from "react";
import axios, { isAxiosError } from "axios";
import { useNavigate } from "react-router";

interface CartItem {
  productId: number;
  productName: string;
  imageUrl: string;
  unitPrice: number;
  quantity: number;
  availableQuantity: number;
}

const customerId = (): number | undefined =>
  JSON.parse(localStorage.getItem("token") || "{}").id;

const messageOf = (e: unknown, fallback: string): string =>
  isAxiosError(e) ? (e.response?.data?.message ?? fallback) : fallback;

const CartPage: React.FC = () => {
  const [items, setItems] = useState<CartItem[]>([]);
  const [message, setMessage] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const navigate = useNavigate();

  const load = () => {
    const id = customerId();
    if (!id) return;
    axios.get(`/api/cart/items?customerId=${id}`).then((res) => setItems(res.data));
  };

  useEffect(load, []);

  const handleCheckout = async () => {
    setSubmitting(true);
    setMessage("");
    try {
      await axios.post("/api/cart/checkout", {
        customerId: customerId(),
        availabilities: items.map((i) => ({
          productId: i.productId,
          availableQuantity: i.availableQuantity,
        })),
      });
      navigate("/sales");
    } catch (e) {
      setMessage(messageOf(e, "購入を確定できませんでした"));
      load();
    } finally {
      setSubmitting(false);
    }
  };

  const handleRemove = async (productId: number) => {
    try {
      await axios.delete(`/api/cart/items/${productId}?customerId=${customerId()}`);
      setItems(items.filter((i) => i.productId !== productId));
    } catch (e) {
      setMessage(messageOf(e, "カートから外せませんでした"));
    }
  };

  const total = items.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0);

  return (
    <div
      style={{
        maxWidth: 700,
        margin: "40px auto",
        background: "#fff",
        padding: 32,
        borderRadius: 8,
        boxShadow: "0 2px 8px #0001",
      }}
    >
      <h2>カート</h2>
      {items.length === 0 ? (
        <div>カートは空です</div>
      ) : (
        <>
          <table style={{ width: "100%", marginBottom: 24 }}>
            <thead>
              <tr>
                <th>商品</th>
                <th>数量</th>
                <th>小計</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.productId}>
                  <td style={{ display: "flex", alignItems: "center", gap: 8 }}>
                    <img
                      src={item.imageUrl}
                      alt={item.productName}
                      style={{ width: 60, height: 60, objectFit: "cover", borderRadius: 4 }}
                    />
                    {item.productName}
                  </td>
                  <td>{item.quantity}</td>
                  <td>{(item.unitPrice * item.quantity).toLocaleString()}円</td>
                  <td>
                    <button
                      onClick={() => handleRemove(item.productId)}
                      style={{ color: "#d32f2f", border: "none", background: "none" }}
                    >
                      削除
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div style={{ textAlign: "right", fontWeight: 700, fontSize: 18 }}>
            合計: {total.toLocaleString()}円
          </div>
          <button
            onClick={handleCheckout}
            disabled={submitting}
            style={{
              marginTop: 16,
              padding: "10px 32px",
              background: "#388e3c",
              color: "#fff",
              border: "none",
              borderRadius: 4,
            }}
          >
            購入確定
          </button>
        </>
      )}
      {message && <div style={{ marginTop: 16, color: "#d32f2f" }}>{message}</div>}
    </div>
  );
};

export default CartPage;
