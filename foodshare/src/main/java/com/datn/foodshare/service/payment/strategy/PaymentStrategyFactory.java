package com.datn.foodshare.service.payment.strategy;

import com.datn.foodshare.util.constant.PaymentMethod;
import org.springframework.stereotype.Component;

@Component
public class PaymentStrategyFactory {

    private final CashPaymentStrategy cashPaymentStrategy;
    private final MomoPaymentStrategy momoPaymentStrategy;
    private final ZaloPayPaymentStrategy zaloPayPaymentStrategy;

    public PaymentStrategyFactory(CashPaymentStrategy cashPaymentStrategy,
                                  MomoPaymentStrategy momoPaymentStrategy, ZaloPayPaymentStrategy zaloPayPaymentStrategy) {
        this.cashPaymentStrategy = cashPaymentStrategy;
        this.momoPaymentStrategy = momoPaymentStrategy;
        this.zaloPayPaymentStrategy = zaloPayPaymentStrategy;
    }


    public PaymentStrategy getStrategy(PaymentMethod method) {
        return switch (method) {
            case CASH -> cashPaymentStrategy;
            case MOMO -> requireConfigured(momoPaymentStrategy, "MOMO");
            case ZALOPAY -> requireConfigured(zaloPayPaymentStrategy, "ZALOPAY");
        };
    }

    private PaymentStrategy requireConfigured(PaymentStrategy strategy, String provider) {
        if (strategy == null) throw new IllegalStateException(provider + " payment strategy is not configured");
        return strategy;
    }
}
