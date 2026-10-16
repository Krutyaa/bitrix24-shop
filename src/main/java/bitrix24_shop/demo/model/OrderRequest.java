package bitrix24_shop.demo.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record OrderRequest(
        @NotBlank(message = "Имя обязательно для заполнения")
        String name,

        @NotBlank(message = "Телефон или почта обязательны")
        String contact,

        @NotEmpty(message = "Корзина не должна быть пустой")
        List<@Valid CartItem> items
) {}