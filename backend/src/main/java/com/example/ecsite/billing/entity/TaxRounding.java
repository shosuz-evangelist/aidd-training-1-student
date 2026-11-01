package com.example.ecsite.billing.entity;

/** 消費税の端数の処理方法 */
public enum TaxRounding {
    /** 切り捨て */
    FLOOR,
    /** 四捨五入 */
    HALF_UP
}
