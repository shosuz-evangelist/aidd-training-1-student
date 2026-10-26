import React, { useEffect, useState } from "react";
import { Link } from "react-router";
import axios from "axios";
import StockLabel from "../components/StockLabel";

interface Product {
  id: number;
  name: string;
  price: number;
  imageUrl: string;
  availableQuantity: number;
}

const ProductListPage: React.FC = () => {
  const [products, setProducts] = useState<Product[]>([]);

  useEffect(() => {
    axios.get("/api/products").then((res) => setProducts(res.data));
  }, []);

  return (
    <div style={{ maxWidth: 900, margin: "40px auto" }}>
      <h2>商品一覧</h2>
      <div style={{ display: "flex", flexWrap: "wrap", gap: 24 }}>
        {products.map((product) => (
          <div
            key={product.id}
            style={{
              width: 220,
              background: "#fff",
              borderRadius: 8,
              boxShadow: "0 2px 8px #0001",
              padding: 16,
            }}
          >
            <Link
              to={`/products/${product.id}`}
              style={{ textDecoration: "none", color: "inherit" }}
            >
              <img
                src={product.imageUrl}
                alt={product.name}
                style={{
                  width: "100%",
                  height: 140,
                  objectFit: "cover",
                  borderRadius: 4,
                }}
              />
              <div style={{ marginTop: 8, fontWeight: 600 }}>
                {product.name}
              </div>
              <div style={{ color: "#1976d2", fontWeight: 700 }}>
                {product.price.toLocaleString()}円
              </div>
            </Link>
            <StockLabel availableQuantity={product.availableQuantity} />
          </div>
        ))}
      </div>
    </div>
  );
};

export default ProductListPage;
