package bitrix24_shop.demo.service;

import bitrix24_shop.demo.model.CartItem;
import bitrix24_shop.demo.model.OrderRequest;
import bitrix24_shop.demo.model.Product;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null || !response.containsKey("result")) {
                return List.of();
            }

            List<Map<String, Object>> resultList = (List<Map<String, Object>>) response.get("result");
            List<Product> products = new ArrayList<>();

            for (Map<String, Object> item : resultList) {
                Long id = Long.valueOf(item.get("ID").toString());
                String name = (String) item.get("NAME");

                Double price = 0.0;
                Object priceObj = item.get("PRICE");
                if (priceObj != null) {
                    price = Double.parseDouble(priceObj.toString());
                }

                products.add(new Product(id, name, price));
            }

            return products;
        } catch (Exception e) {
            throw new RuntimeException("Не удалось получить товары из Битрикс24: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Long createOrder(OrderRequest request) {
        List<Product> catalog = getProducts();

        Map<Long, Product> productMap = new HashMap<>();
        for (Product p : catalog) {
            productMap.put(p.id(), p);
        }

        double totalAmount = 0.0;
        List<Map<String, Object>> productRows = new ArrayList<>();

        for (CartItem item : request.items()) {
            Product product = productMap.get(item.productId());
            if (product == null) {
                throw new IllegalArgumentException("Товар с ID " + item.productId() + " не найден в каталоге");
            }

            double price = product.price();
            totalAmount += price * item.quantity();

            Map<String, Object> row = new HashMap<>();
            row.put("PRODUCT_ID", product.id());
            row.put("PRICE", price);
            row.put("QUANTITY", item.quantity());
            productRows.add(row);
        }

        String dealAddUrl = webhookUrl + "crm.deal.add.json";

        Map<String, Object> dealFields = new HashMap<>();
        dealFields.put("TITLE", "Интернет-магазин: Заказ от " + request.name());
        dealFields.put("OPPORTUNITY", totalAmount);
        dealFields.put("CURRENCY_ID", "RUB");
        dealFields.put("COMMENTS", "Имя: " + request.name() + "<br>Контакт: " + request.contact());

        Map<String, Object> requestBody = Map.of("fields", dealFields);

        try {
            Map<String, Object> dealResponse = restTemplate.postForObject(dealAddUrl, requestBody, Map.class);

            if (dealResponse == null || !dealResponse.containsKey("result")) {
                throw new RuntimeException("Не удалось создать сделку в Битрикс24");
            }

            Long dealId = Long.valueOf(dealResponse.get("result").toString());

            String rowsUrl = webhookUrl + "crm.deal.productrows.set.json";
            Map<String, Object> rowsBody = Map.of(
                    "id", dealId,
                    "rows", productRows
            );

            restTemplate.postForObject(rowsUrl, rowsBody, Map.class);

            return dealId;

        } catch (Exception e) {
            throw new RuntimeException("Ошибка при отправке заказа в Битрикс24: " + e.getMessage());
        }
    }
}