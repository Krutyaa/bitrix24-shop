package bitrix24_shop.demo.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartItem(
        @NotNull(message = "ID товара обязателен")
        Long productId,

        @Min(value = 1, message = "Количество должно быть не менее 1")
        Integer quantity
) {}