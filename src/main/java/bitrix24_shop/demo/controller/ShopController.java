package bitrix24_shop.demo.controller;

import bitrix24_shop.demo.model.OrderRequest;
import bitrix24_shop.demo.model.OrderResult;
import bitrix24_shop.demo.model.Product;
import bitrix24_shop.demo.service.BitrixService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ShopController {

    private final BitrixService bitrixService;

    public ShopController(BitrixService bitrixService) {
        this.bitrixService = bitrixService;
    }

    @GetMapping("/products")
    public List<Product> getProducts() {
        return bitrixService.getProducts();
    }

    @PostMapping("/order")
    public Map<String, Object> createOrder(
            @Valid @RequestBody OrderRequest request
    ) {
        OrderResult result = bitrixService.createOrder(request);

        return Map.of(
                "success", true,
                "orderId", result.orderId(),
                "totalAmount", result.totalAmount(),
                "message", "Заказ успешно оформлен!"
        );
    }
}
