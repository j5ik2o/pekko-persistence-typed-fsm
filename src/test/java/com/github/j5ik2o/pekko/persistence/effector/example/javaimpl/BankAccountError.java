package com.github.j5ik2o.pekko.persistence.effector.example.javaimpl;

/**
 * 銀行口座のエラーを表す列挙型
 */
public enum BankAccountError {
    /**
     * 限度額超過エラー
     */
    LIMIT_OVER_ERROR,
    
    /**
     * 残高不足エラー
     */
    INSUFFICIENT_FUNDS_ERROR
}
