package bitrix24_shop.demo.model;

import java.math.BigDecimal;

public record OrderResult(
        Long orderId,
        BigDecimal totalAmount
) {}
