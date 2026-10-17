package bitrix24_shop.demo.service;

import bitrix24_shop.demo.model.OrderResult;
import bitrix24_shop.demo.model.CartItem;
import bitrix24_shop.demo.model.OrderRequest;
import bitrix24_shop.demo.model.Product;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BitrixService {

    @Value("${bitrix24.webhook.url}")
    private String webhookUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @SuppressWarnings("unchecked")
    public List<Product> getProducts() {
        String url = webhookUrl + "crm.product.list.json";

        try {
            Map<String, Object> response =
                    restTemplate.getForObject(url, Map.class);

            checkBitrixResponse(response, "получение каталога товаров");

            List<Map<String, Object>> resultList =
                    (List<Map<String, Object>>) response.get("result");

            List<Product> products = new ArrayList<>();

            for (Map<String, Object> item : resultList) {
                Long id = Long.valueOf(item.get("ID").toString());
                String name = (String) item.get("NAME");

                Object priceObj = item.get("PRICE");
                BigDecimal price = priceObj == null
                        ? BigDecimal.ZERO
                        : new BigDecimal(priceObj.toString());

                products.add(new Product(id, name, price.doubleValue()));
            }

            return products;

        } catch (Exception e) {
            throw new RuntimeException(
                    "Не удалось получить товары из Bitrix24: " + e.getMessage(), e
            );
        }
    }

    @SuppressWarnings("unchecked")
    public OrderResult createOrder(OrderRequest request) {
        List<Product> catalog = getProducts();

        Map<Long, Product> productMap = new HashMap<>();

        for (Product product : catalog) {
            productMap.put(product.id(), product);
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<Map<String, Object>> productRows = new ArrayList<>();

        for (CartItem item : request.items()) {
            Product product = productMap.get(item.productId());

            if (product == null) {
                throw new IllegalArgumentException(
                        "Товар с ID " + item.productId()
                                + " не найден в каталоге"
                );
            }

            BigDecimal price = BigDecimal.valueOf(product.price());

            totalAmount = totalAmount.add(
                    price.multiply(BigDecimal.valueOf(item.quantity()))
            );

            Map<String, Object> row = new HashMap<>();
            row.put("PRODUCT_ID", product.id());
            row.put("PRICE", price);
            row.put("QUANTITY", item.quantity());

            productRows.add(row);
        }

        String dealAddUrl = webhookUrl + "crm.deal.add.json";

        Map<String, Object> dealFields = new HashMap<>();
        dealFields.put(
                "TITLE",
                "Интернет-магазин: Заказ от " + request.name()
        );
        dealFields.put("OPPORTUNITY", totalAmount);
        dealFields.put("CURRENCY_ID", "RUB");
        dealFields.put(
                "COMMENTS",
                "Имя: " + request.name()
                        + "\nКонтакт: " + request.contact()
        );

        Map<String, Object> requestBody = Map.of("fields", dealFields);

        try {
            Map<String, Object> dealResponse =
                    restTemplate.postForObject(
                            dealAddUrl, requestBody, Map.class
                    );

            checkBitrixResponse(dealResponse, "создание сделки");

            Long dealId = Long.valueOf(
                    dealResponse.get("result").toString()
            );

            String rowsUrl =
                    webhookUrl + "crm.deal.productrows.set.json";

            Map<String, Object> rowsBody = Map.of(
                    "id", dealId,
                    "rows", productRows
            );

            Map<String, Object> rowsResponse =
                    restTemplate.postForObject(
                            rowsUrl, rowsBody, Map.class
                    );

            checkBitrixResponse(
                    rowsResponse, "добавление товаров в сделку"
            );

            return new OrderResult(dealId, totalAmount);

        } catch (Exception e) {
            throw new RuntimeException(
                    "Ошибка при отправке заказа в Bitrix24: "
                            + e.getMessage(), e
            );
        }
    }

    private void checkBitrixResponse(
            Map<String, Object> response,
            String operation
    ) {
        if (response == null) {
            throw new RuntimeException(
                    "Bitrix24 не вернул ответ при операции: " + operation
            );
        }

        if (response.containsKey("error")) {
            String error = String.valueOf(response.get("error"));
            String description = String.valueOf(
                    response.getOrDefault(
                            "error_description",
                            "Описание отсутствует"
                    )
            );

            throw new RuntimeException(
                    "Ошибка Bitrix24 (" + operation + "): "
                            + error + " — " + description
            );
        }

        if (!response.containsKey("result")) {
            throw new RuntimeException(
                    "В ответе Bitrix24 отсутствует результат операции: "
                            + operation
            );
        }
    }
}
