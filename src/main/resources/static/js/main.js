let products = [];
let cart = JSON.parse(localStorage.getItem('cart')) || {};


async function fetchProducts() {
    try {
        const response = await fetch('/api/products');
        if (!response.ok) throw new Error('Ошибка сервера');

        products = await response.json();

        renderProducts();
        renderCart();
    } catch (e) {
        document.getElementById('products-grid').innerHTML = `
            <div class="col-span-2 p-4 bg-red-50 text-red-700 rounded-lg">
                Не удалось загрузить товары из Битрикс24. Проверьте соединение.
            </div>`;
    }
}

function renderProducts() {
    const grid = document.getElementById('products-grid');
    if (products.length === 0) {
        grid.innerHTML = '<p class="text-gray-500">В каталоге пока нет товаров.</p>';
        return;
    }

    grid.innerHTML = products.map(p => `
        <div class="bg-white p-4 rounded-xl shadow-sm border border-gray-100 flex flex-col justify-between hover:shadow-md transition">
            <div>
                <h3 class="font-bold text-lg mb-2">${escapeHtml(p.name)}</h3>
                <p class="text-indigo-600 font-semibold text-xl mb-4">${p.price.toLocaleString()} ₽</p>
            </div>
            <button onclick="addToCart(${p.id})" class="bg-gray-900 text-white py-2 px-4 rounded-lg text-sm font-medium hover:bg-indigo-600 transition">
                В корзину
            </button>
        </div>
    `).join('');
}

function escapeHtml(str) {
    return str.replace(/[&<>'"]/g,
        tag => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[tag] || tag)
    );
}

function addToCart(productId) {
    cart[productId] = (cart[productId] || 0) + 1;
    saveAndRenderCart();
}

function updateQuantity(productId, delta) {
    cart[productId] = (cart[productId] || 0) + delta;
    if (cart[productId] <= 0) {
        delete cart[productId];
    }
    saveAndRenderCart();
}

function saveAndRenderCart() {
    localStorage.setItem('cart', JSON.stringify(cart));
    renderCart();
}


function renderCart() {
    const container = document.getElementById('cart-items');
    const cartCountEl = document.getElementById('cart-count');
    const cartTotalEl = document.getElementById('cart-total');
    const sidebarTotalEl = document.getElementById('sidebar-total');

    const entries = Object.entries(cart);
    let totalCount = 0;
    let totalPrice = 0;

    if (entries.length === 0) {
        container.innerHTML =
            '<p class="text-gray-400 text-sm py-2">Корзина пуста</p>';

        cartCountEl.textContent = '0';
        cartTotalEl.textContent = '0';
        sidebarTotalEl.textContent = '0 ₽';
        return;
    }

    // Пока каталог не загружен, не показываем ложную пустую корзину.
    if (products.length === 0) {
        container.innerHTML =
            '<p class="text-gray-500 text-sm py-2">Загружаем корзину...</p>';

        cartCountEl.textContent = entries.reduce(
            (sum, [, qty]) => sum + Number(qty || 0), 0
        );

        return;
    }

    const rows = entries.map(([id, qty]) => {
        const product = products.find(p => String(p.id) === String(id));
        const quantity = Number(qty);

        if (!product) {
            return `
                <div class="py-2 text-sm text-red-600">
                    Товар с ID ${escapeHtml(String(id))} не найден в каталоге.
                    <button onclick="removeFromCart('${id}')"
                            class="underline ml-1">
                        Удалить
                    </button>
                </div>
            `;
        }

        totalCount += quantity;
        totalPrice += Number(product.price) * quantity;

        return `
            <div class="py-2 flex justify-between items-center">
                <div>
                    <p class="font-medium text-sm">
                        ${escapeHtml(product.name)}
                    </p>
                    <p class="text-xs text-gray-500">
                        ${Number(product.price).toLocaleString()} ₽ × ${quantity}
                    </p>
                </div>
                <div class="flex items-center space-x-2">
                    <button onclick="updateQuantity(${Number(product.id)}, -1)"
                            class="px-2 bg-gray-100 rounded hover:bg-gray-200">-</button>
                    <span class="text-sm font-bold">${quantity}</span>
                    <button onclick="updateQuantity(${Number(product.id)}, 1)"
                            class="px-2 bg-gray-100 rounded hover:bg-gray-200">+</button>
                </div>
            </div>
        `;
    });

    container.innerHTML = rows.join('');

    cartCountEl.textContent = totalCount;
    cartTotalEl.textContent = totalPrice.toLocaleString();
    sidebarTotalEl.textContent = totalPrice.toLocaleString() + ' ₽';
}

function removeFromCart(productId) {
    delete cart[productId];
    saveAndRenderCart();
}


document.getElementById('order-form').addEventListener('submit', async (e) => {
    e.preventDefault();

    const name = document.getElementById('name').value.trim();
    const contact = document.getElementById('contact').value.trim();
    const submitBtn = document.getElementById('submit-btn');
    const resultBox = document.getElementById('order-result');

    const items = Object.entries(cart).map(([productId, quantity]) => ({
        productId: Number(productId),
        quantity: quantity
    }));

    if (items.length === 0) {
        alert('Корзина пуста!');
        return;
    }

    submitBtn.disabled = true;
    submitBtn.textContent = 'Отправка в Битрикс24';
    resultBox.classList.add('hidden');

    try {
        const response = await fetch('/api/order', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, contact, items })
        });

        const data = await response.json();
        if (!response.ok) throw new Error(data.message || 'Ошибка при оформлении заказа');

        cart = {};
        saveAndRenderCart();
        document.getElementById('order-form').reset();


        resultBox.className = 'mt-4 p-3 rounded-lg text-sm bg-green-50 text-green-800 border border-green-200';

        resultBox.innerHTML = `
        <b>Заказ оформлен!</b><br>
        ID сделки в Битрикс24: <b>#${data.orderId}</b><br>
        Сумма заказа: <b>${Number(data.totalAmount).toLocaleString('ru-RU')} ₽</b>`;

        resultBox.classList.remove('hidden');

    } catch (err) {
        resultBox.className = 'mt-4 p-3 rounded-lg text-sm bg-red-50 text-red-800 border border-red-200';
        resultBox.innerHTML = `<b>Ошибка:</b> ${escapeHtml(err.message)}`;
        resultBox.classList.remove('hidden');
    } finally {
        submitBtn.disabled = false;
        submitBtn.textContent = 'Оформить заказ';
    }
});

fetchProducts();