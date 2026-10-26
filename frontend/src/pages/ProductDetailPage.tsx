import React, { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router";
import axios, { isAxiosError } from "axios";
import StockLabel from "../components/StockLabel";

interface Product {
  id: number;
  name: string;
  price: number;
  imageUrl: string;
  availableQuantity: number;
}

const ProductDetailPage: React.FC = () => {
  const { id } = useParams();
  const [product, setProduct] = useState<Product | null>(null);
  const [quantity, setQuantity] = useState(1);
  const [error, setError] = useState("");
  const navigate = useNavigate();

  useEffect(() => {
    axios.get(`/api/products/${id}`).then((res) => setProduct(res.data));
  }, [id]);

  const handleAddToCart = async () => {
    const user = JSON.parse(localStorage.getItem("token") || "{}");
    const customerId = user.id;
    try {
      await axios.post("/api/cart", {
        customerId,
        productId: product?.id,
        quantity,
      });
      navigate("/cart");
    } catch (e) {
      setError(isAxiosError(e) ? (e.response?.data?.message ?? "カートに入れられませんでした") : "カートに入れられませんでした");
    }
  };

  if (!product) return <div>Loading...</div>;

  const soldOut = product.availableQuantity <= 0;
  // 入力補助のみ。業務ルールの検証は API が行う。
  const handleQuantityChange = (value: string) => {
    const n = Math.floor(Number(value));
    if (!Number.isFinite(n)) return;
    setQuantity(Math.min(Math.max(n, 1), Math.max(product.availableQuantity, 1)));
  };

  return (
    <div
      style={{
        maxWidth: 600,
        margin: "40px auto",
        background: "#fff",
        padding: 32,
        borderRadius: 8,
        boxShadow: "0 2px 8px #0001",
      }}
    >
      <img
        src={product.imageUrl}
        alt={product.name}
        style={{
          width: "100%",
          height: 260,
          objectFit: "cover",
          borderRadius: 4,
        }}
      />
      <h2>{product.name}</h2>
      <div style={{ color: "#1976d2", fontWeight: 700, fontSize: 20 }}>
        {product.price.toLocaleString()}円
      </div>
      <div style={{ margin: "8px 0" }}>
        <StockLabel availableQuantity={product.availableQuantity} />
      </div>
      <div style={{ margin: "16px 0" }}>
        <input
          type="number"
          min={1}
          max={soldOut ? 1 : product.availableQuantity}
          value={quantity}
          disabled={soldOut}
          onChange={(e) => handleQuantityChange(e.target.value)}
          style={{ width: 60, marginRight: 8 }}
        />
        <button
          onClick={handleAddToCart}
          disabled={soldOut}
          style={{
            padding: "8px 24px",
            background: soldOut ? "#bdbdbd" : "#1976d2",
            color: soldOut ? "#757575" : "#fff",
            border: "none",
            borderRadius: 4,
            cursor: soldOut ? "not-allowed" : "pointer",
          }}
        >
          カートに追加
        </button>
      </div>
      {error && <div style={{ color: "#d32f2f" }}>{error}</div>}
    </div>
  );
};

export default ProductDetailPage;
