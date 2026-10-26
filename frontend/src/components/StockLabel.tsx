import React from "react";

interface StockLabelProps {
  availableQuantity: number;
}

const StockLabel: React.FC<StockLabelProps> = ({ availableQuantity }) => {
  if (availableQuantity > 0) {
    return <span>購入できる数: {availableQuantity}</span>;
  }
  return <span style={{ color: "#d32f2f" }}>在庫切れ</span>;
};

export default StockLabel;
