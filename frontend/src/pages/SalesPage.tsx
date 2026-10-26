import React, { useEffect, useState } from "react";
import axios from "axios";

interface Sale {
  id: number;
  customerName: string;
  productName: string;
  unitPrice: number;
  quantity: number;
  total: number;
  date: string;
}

const SalesPage: React.FC = () => {
  const [sales, setSales] = useState<Sale[]>([]);

  useEffect(() => {
    axios.get("/api/sales").then((res) => setSales(res.data));
  }, []);

  return (
    <div
      style={{
        maxWidth: 900,
        margin: "40px auto",
        background: "#fff",
        padding: 32,
        borderRadius: 8,
        boxShadow: "0 2px 8px #0001",
      }}
    >
      <h2>売上一覧</h2>
      <table style={{ width: "100%" }}>
        <thead>
          <tr>
            <th>ID</th>
            <th>顧客</th>
            <th>商品</th>
            <th>単価</th>
            <th>数量</th>
            <th>合計</th>
            <th>日付</th>
          </tr>
        </thead>
        <tbody>
          {sales.map((sale) => (
            <tr key={sale.id}>
              <td>{sale.id}</td>
              <td>{sale.customerName}</td>
              <td>{sale.productName}</td>
              <td>{sale.unitPrice.toLocaleString()}円</td>
              <td>{sale.quantity}</td>
              <td>{sale.total.toLocaleString()}円</td>
              <td>{new Date(sale.date).toLocaleString("ja-JP")}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

export default SalesPage;
