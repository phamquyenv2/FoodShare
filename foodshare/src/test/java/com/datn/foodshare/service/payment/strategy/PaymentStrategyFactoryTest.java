package com.datn.foodshare.service.payment.strategy;

import com.datn.foodshare.util.constant.PaymentMethod;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class PaymentStrategyFactoryTest {

    @Test
    void getStrategy_returnsStrategyForEachPaymentMethod() {
        CashPaymentStrategy cash = mock(CashPaymentStrategy.class);
        MomoPaymentStrategy momo = mock(MomoPaymentStrategy.class);
        ZaloPayPaymentStrategy zalo = mock(ZaloPayPaymentStrategy.class);
        PaymentStrategyFactory factory = new PaymentStrategyFactory(cash, momo, zalo);

        assertSame(cash, factory.getStrategy(PaymentMethod.CASH));
        assertSame(momo, factory.getStrategy(PaymentMethod.MOMO));
        assertSame(zalo, factory.getStrategy(PaymentMethod.ZALOPAY));
    }

    @Test
    void getStrategy_rejectsMissingMomoConfiguration() {
        PaymentStrategyFactory factory = new PaymentStrategyFactory(
                mock(CashPaymentStrategy.class), null, mock(ZaloPayPaymentStrategy.class));

        assertThrows(IllegalStateException.class, () -> factory.getStrategy(PaymentMethod.MOMO));
    }

    @Test
    void getStrategy_rejectsMissingZaloPayConfiguration() {
        PaymentStrategyFactory factory = new PaymentStrategyFactory(
                mock(CashPaymentStrategy.class), mock(MomoPaymentStrategy.class), null);

        assertThrows(IllegalStateException.class, () -> factory.getStrategy(PaymentMethod.ZALOPAY));
    }
}
