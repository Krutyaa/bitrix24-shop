package bitrix24_shop.demo.model;

public record Product(
        Long id,
        String name,
        Double price
) {}