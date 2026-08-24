package com.datn.foodshare.service.payment.strategy;

import com.datn.foodshare.util.constant.PaymentMethod;
import org.springframework.stereotype.Component;

@Component
public class PaymentStrategyFactory {

    private final CashPaymentStrategy cashPaymentStrategy;
    private final EWalletPaymentStrategy eWalletPaymentStrategy;

    public PaymentStrategyFactory(CashPaymentStrategy cashPaymentStrategy, EWalletPaymentStrategy eWalletPaymentStrategy) {
        this.cashPaymentStrategy = cashPaymentStrategy;
        this.eWalletPaymentStrategy = eWalletPaymentStrategy;
    }

    public PaymentStrategy getStrategy(PaymentMethod method) {
        return switch (method) {
            case CASH -> cashPaymentStrategy;
            case EWALLET -> eWalletPaymentStrategy;
        };
    }
}
