(() => {
    const labels = {
        pending: 'جديد', confirmed: 'تم القبول', preparing: 'قيد التجهيز',
        ready: 'جاهز', completed: 'مكتمل', rejected: 'مرفوض', cancelled: 'ملغي'
    };
    let audioContext;
    let orderStoragePrefix = 'beitMama_mother';
    const stateKey = suffix => `${orderStoragePrefix}_${suffix}`;

    const showError = message => {
        const notice = document.getElementById('notice');
        if (!notice) {
            console.error(message);
            return;
        }
        notice.hidden = false;
        notice.textContent = message;
    };

    window.switchDashTab = (tabId, button) => {
        document.querySelectorAll('.tab-panel').forEach(panel => panel.classList.remove('active'));
        document.querySelectorAll('.dash-tab-btn').forEach(tab => {
            tab.classList.remove('active');
            tab.setAttribute('aria-selected', 'false');
        });
        const panel = document.getElementById(tabId);
        if (panel) panel.classList.add('active');
        if (button) {
            button.classList.add('active');
            button.setAttribute('aria-selected', 'true');
        }
    };

    window.logout = async () => {
        try {
            const response = await fetch('/api/logout', { method: 'POST' });
            if (!response.ok) throw new Error('تعذر تسجيل الخروج. حاولي مرة أخرى.');
            location.href = 'login.html';
        } catch (error) {
            showError(error.message);
        }
    };

    async function requireMother() {
        const response = await fetch('/api/me');
        if (!response.ok) {
            location.href = 'login.html';
            return false;
        }
        const { user } = await response.json();
        if (user.role !== 'MOTHER') {
            location.href = user.role === 'ADMIN' ? 'admin-dashboard.html' : 'index.html';
            return false;
        }
        orderStoragePrefix = `beitMama_mother_${user.id}`;
        document.getElementById('motherProfileName').textContent = user.name || 'الأم الطاهية';
        const profileResponse = await fetch('/api/mothers/profile');
        if (!profileResponse.ok) throw new Error('تعذر تحميل بيانات ملفك الشخصي');
        const profile = await profileResponse.json();
        if (profile.profileImageUrl) {
            const preview = document.getElementById('motherProfilePreview');
            preview.src = window.beitMamaResourceUrl(profile.profileImageUrl);
            preview.hidden = false;
            document.getElementById('motherProfilePlaceholder').hidden = true;
        }
        return true;
    }

    async function loadCategories() {
        const response = await fetch('/api/categories');
        if (!response.ok) throw new Error('تعذر تحميل التصنيفات');
        const categories = await response.json();
        const productSelect = document.getElementById('category');
        const filterSelect = document.getElementById('productCategoryFilter');
        const selectedFilter = filterSelect.value;
        productSelect.replaceChildren(new Option('اختاري التصنيف', ''));
        filterSelect.replaceChildren(new Option('كل التصنيفات', ''));
        categories.forEach(category => {
            productSelect.add(new Option(category.name, category.id));
            filterSelect.add(new Option(category.name, category.id));
        });
        filterSelect.value = selectedFilter;
    }

    async function loadProducts() {
        const response = await fetch('/api/products');
        if (!response.ok) throw new Error('تعذر تحميل الوجبات');
        const list = await response.json();
        const categoryFilter = document.getElementById('productCategoryFilter').value;
        const filtered = list.filter(product => !categoryFilter || String(product.categoryId) === categoryFilter);
        const box = document.getElementById('products');
        box.replaceChildren();
        document.getElementById('statProducts').textContent = list.length;
        document.getElementById('statStock').textContent = list.reduce((sum, product) => sum + Number(product.quantityAvailable || 0), 0);
        if (!filtered.length) {
            box.innerHTML = '<div class="empty-state"><span class="big">🍽️</span>لا توجد وجبات في هذا التصنيف بعد.</div>';
            return;
        }
        filtered.forEach(product => {
            const card = document.createElement('article');
            card.className = 'product';
            const heading = document.createElement('h3');
            heading.textContent = product.name;
            const displayPrice = Number(product.customerPrice ?? product.price ?? 0);
            const price = document.createElement('p');
            price.className = 'price-line';
            price.textContent = `${displayPrice} ج.م`;
            const netPrice = document.createElement('p');
            netPrice.className = 'desc';
            netPrice.textContent = `صافي الأم: ${Number(product.price ?? 0).toLocaleString('ar-EG')} ج.م`;
            const category = document.createElement('p');
            category.className = 'desc';
            category.textContent = product.categoryName || '';
            const meta = document.createElement('div');
            meta.className = 'meta';
            const availability = document.createElement('span');
            availability.className = `pill ${product.isAvailable ? 'ok' : 'off'}`;
            availability.textContent = product.isAvailable ? 'معروضة' : 'مخفية';
            const quantity = document.createElement('span');
            quantity.className = 'pill qty';
            quantity.textContent = `الكمية ${product.quantityAvailable}`;
            meta.append(availability, quantity);
            const description = document.createElement('p');
            description.className = 'desc';
            description.textContent = product.description || '';
            const details = document.createElement('p');
            details.className = 'desc';
            details.textContent = [
                product.preparationMinutes ? `وقت التحضير: ${product.preparationMinutes} دقيقة` : '',
                product.freshlyCooked ? 'تُطهى طازجة عند الطلب' : 'جاهزة حسب التوفر',
                product.ingredients ? `المكونات: ${product.ingredients}` : ''
            ].filter(Boolean).join(' — ');
            const actions = document.createElement('div');
            actions.className = 'actions';
            const edit = document.createElement('button');
            edit.className = 'btn-main';
            edit.textContent = 'تعديل';
            edit.onclick = () => editProduct(product);
            const remove = document.createElement('button');
            remove.className = 'btn-ghost';
            remove.textContent = 'حذف';
            remove.onclick = () => deleteProduct(product.id);
            actions.append(edit, remove);
            card.append(heading, category, price, netPrice, meta, description, details);
            if (product.imageUrl) {
                const image = document.createElement('img');
                image.src = window.beitMamaResourceUrl(product.imageUrl);
                image.alt = product.name;
                image.loading = 'lazy';
                image.style.cssText = 'width:100%;max-height:220px;object-fit:cover;border-radius:12px;margin:8px 0';
                card.append(image);
            }
            card.append(actions);
            box.append(card);
        });
    }

    function editProduct(product) {
        document.getElementById('productId').value = product.id;
        document.getElementById('name').value = product.name;
        document.getElementById('category').value = product.categoryId;
        document.getElementById('price').value = product.price;
        document.getElementById('quantity').value = product.quantityAvailable;
        document.getElementById('availableUntil').value = product.availableUntil || '';
        document.getElementById('imageUrl').value = product.imageUrl || '';
        document.getElementById('preparationMinutes').value = product.preparationMinutes || 0;
        document.getElementById('ingredients').value = product.ingredients || '';
        document.getElementById('description').value = product.description || '';
        document.getElementById('available').checked = product.isAvailable;
        document.getElementById('freshlyCooked').checked = Boolean(product.freshlyCooked);
        document.getElementById('formTitle').textContent = 'تعديل وجبة';
        window.switchDashTab('tab-add', document.querySelector('.dash-tab-btn'));
        document.getElementById('productForm').scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    function resetForm() {
        document.getElementById('productForm').reset();
        document.getElementById('productId').value = '';
        document.getElementById('imageUrl').value = '';
        document.getElementById('available').checked = true;
        document.getElementById('preparationMinutes').value = 0;
        document.getElementById('formTitle').textContent = 'إضافة وجبة';
    }

    async function saveProduct(event) {
        event.preventDefault();
        try {
            const id = document.getElementById('productId').value;
            const payload = new URLSearchParams({
                id,
                category_id: document.getElementById('category').value,
                name: document.getElementById('name').value.trim(),
                price: document.getElementById('price').value,
                quantity: document.getElementById('quantity').value,
                available_until: document.getElementById('availableUntil').value,
                image_url: document.getElementById('imageUrl').value,
                description: document.getElementById('description').value.trim(),
                ingredients: document.getElementById('ingredients').value.trim(),
                preparation_minutes: document.getElementById('preparationMinutes').value,
                freshly_cooked: String(document.getElementById('freshlyCooked').checked),
                available: String(document.getElementById('available').checked)
            });
            const response = await fetch('/api/products', { method: id ? 'PUT' : 'POST', body: payload });
            const result = await response.json();
            if (!response.ok || !result.success) throw new Error(result.message || 'تعذر حفظ الوجبة');
            resetForm();
            await loadProducts();
        } catch (error) {
            showError(error.message);
        }
    }

    async function deleteProduct(id) {
        if (!confirm('ستُخفى الوجبة عن المتجر مع الحفاظ على سجل الطلبات. متابعة؟')) return;
        const response = await fetch('/api/products', { method: 'DELETE', body: new URLSearchParams({ id }) });
        const result = await response.json();
        if (!response.ok || !result.success) {
            showError(result.message || 'تعذر حذف الوجبة');
            return;
        }
        await loadProducts();
    }

    function playOrderSound() {
        if (!audioContext) return;
        if (audioContext.state === 'suspended') audioContext.resume();
        const oscillator = audioContext.createOscillator();
        const gain = audioContext.createGain();
        oscillator.type = 'sine';
        oscillator.frequency.value = 880;
        gain.gain.setValueAtTime(0.001, audioContext.currentTime);
        gain.gain.exponentialRampToValueAtTime(0.18, audioContext.currentTime + 0.02);
        gain.gain.exponentialRampToValueAtTime(0.001, audioContext.currentTime + 0.28);
        oscillator.connect(gain);
        gain.connect(audioContext.destination);
        oscillator.start();
        oscillator.stop(audioContext.currentTime + 0.3);
    }

    window.enableOrderSound = () => {
        const AudioContextType = window.AudioContext || window.webkitAudioContext;
        if (!AudioContextType) {
            showError('المتصفح لا يدعم التنبيه الصوتي.');
            return;
        }
        audioContext ||= new AudioContextType();
        audioContext.resume();
        localStorage.setItem(stateKey('orderSoundEnabled'), 'true');
        document.getElementById('soundToggle').textContent = '✓ صوت الطلبات مفعّل';
        playOrderSound();
    };

    function nextAction(status) {
        return {
            pending: ['confirmed', 'قبول'],
            confirmed: ['preparing', 'بدء التجهيز'],
            preparing: ['ready', 'إعلان الجاهزية'],
            ready: ['completed', 'إتمام الطلب']
        }[status];
    }

    function renderOrders(orders) {
        const box = document.getElementById('orders');
        box.replaceChildren();
        if (!orders.length) {
            box.innerHTML = '<div class="empty-state"><span class="big">🧺</span>لا توجد طلبات حتى الآن.</div>';
            return;
        }
        const groups = new Map();
        orders.forEach(order => {
            const category = order.items?.[0]?.categoryName || 'طلبات متنوعة';
            if (!groups.has(category)) groups.set(category, []);
            groups.get(category).push(order);
        });
        groups.forEach((groupOrders, category) => {
            const heading = document.createElement('h3');
            heading.className = 'order-category-heading';
            heading.textContent = category;
            box.append(heading);
            groupOrders.forEach(order => {
                const card = document.createElement('article');
                card.className = 'order';
                const head = document.createElement('div');
                head.className = 'order-head';
                const title = document.createElement('h3');
                title.textContent = `طلب #${order.id}`;
                const status = document.createElement('span');
                status.className = `status-badge status-${order.status}`;
                status.textContent = labels[order.status] || order.status;
                head.append(title, status);
                const detail = document.createElement('p');
                detail.className = 'details';
                detail.textContent = `العميل: ${order.customerName} (${order.customerPhone}) — ${order.total} ج.م — الموعد: ${order.pickupTime}`;
                const contact = document.createElement('p');
                contact.className = 'details';
                contact.textContent = `التواصل عبر: ${order.contactMethod === 'whatsapp' ? 'واتساب' : 'مكالمة هاتفية'} — الدفع: ${order.paymentMethod === 'cash_on_delivery' ? 'نقداً عند التوصيل' : 'نقداً عند الاستلام'}`;
                const method = document.createElement('p');
                method.className = 'details';
                method.textContent = order.deliveryMethod === 'delivery'
                    ? `توصيل إلى: ${order.deliveryAddress || 'غير محدد'}`
                    : `استلام من المطبخ: ${order.sellerAddress || 'العنوان غير محدد'}`;
                const items = document.createElement('p');
                items.className = 'items-line';
                items.textContent = (order.items || []).map(item => `${item.productName || item.name} × ${item.quantity}`).join('، ');
                card.append(head, detail, contact, method, items);
                const action = nextAction(order.status);
                if (action) {
                    const button = document.createElement('button');
                    button.className = 'btn-main';
                    button.textContent = action[1];
                    button.onclick = () => updateStatus(order.id, action[0]);
                    card.append(button);
                }
                if (order.status === 'pending') {
                    const reject = document.createElement('button');
                    reject.className = 'btn-ghost';
                    reject.textContent = 'رفض الطلب';
                    reject.onclick = () => updateStatus(order.id, 'rejected');
                    card.append(reject);
                }
                box.append(card);
            });
        });
    }

    async function loadOrders() {
        const response = await fetch('/api/orders');
        if (!response.ok) throw new Error('تعذر تحميل الطلبات');
        const orders = await response.json();
        const pending = orders.filter(order => order.status === 'pending').length;
        const earnings = orders
            .filter(order => order.status === 'completed')
            .reduce((sum, order) => sum + (Number(order.totalAmount ?? order.total) || 0), 0);
        const netProfit = earnings / 1.25;
        document.getElementById('statEarnings').textContent = earnings.toLocaleString('ar-EG');
        document.getElementById('statNetProfit').textContent = netProfit.toLocaleString('ar-EG');
        document.getElementById('statPlatformDue').textContent = (earnings - netProfit).toLocaleString('ar-EG');
        document.getElementById('statOrders').textContent = orders.length;
        document.getElementById('statPending').textContent = pending;
        const badge = document.getElementById('pendingCount');
        const latestOrderId = orders.reduce((latest, order) => Math.max(latest, Number(order.id) || 0), 0);
        const storedLatestOrderId = Number(localStorage.getItem(stateKey('lastOrderId')) || 0);
        const newPendingOrders = storedLatestOrderId
            ? orders.filter(order => Number(order.id) > storedLatestOrderId && order.status === 'pending')
            : [];
        let sequence = Number(localStorage.getItem(stateKey('orderNotificationNumber')) || 0);
        if (newPendingOrders.length) {
            sequence += newPendingOrders.length;
            localStorage.setItem(stateKey('orderNotificationNumber'), String(sequence));
            showError(`🔔 تنبيه الطلب رقم #${sequence} — لديك ${newPendingOrders.length} طلب جديد.`);
            if (localStorage.getItem(stateKey('orderSoundEnabled')) === 'true') playOrderSound();
        }
        localStorage.setItem(stateKey('lastOrderId'), String(Math.max(latestOrderId, storedLatestOrderId)));
        if (pending) {
            badge.hidden = false;
            badge.textContent = `${pending} | #${sequence}`;
        } else badge.hidden = true;
        const tabBadge = document.getElementById('pendingCountTab');
        tabBadge.style.display = pending ? 'inline-flex' : 'none';
        tabBadge.textContent = String(pending);
        renderOrders(orders);
    }

    async function updateStatus(id, status) {
        const response = await fetch('/api/orders/status', {
            method: 'POST',
            body: new URLSearchParams({ order_id: id, status })
        });
        const result = await response.json();
        if (!response.ok || !result.success) {
            showError(result.message || 'تعذر تحديث حالة الطلب');
            return;
        }
        await loadOrders();
    }

    async function refresh() {
        try {
            await Promise.all([loadCategories(), loadProducts(), loadOrders()]);
        } catch (error) {
            showError(error.message);
        }
    }

    window.saveProduct = saveProduct;
    window.resetForm = resetForm;
    window.loadProducts = () => loadProducts().catch(error => showError(error.message));
    window.saveMotherProfileImage = saveMotherProfileImage;

    (async () => {
        if (!await requireMother()) return;
        if (localStorage.getItem(stateKey('orderSoundEnabled')) === 'true') {
            document.getElementById('soundToggle').textContent = '✓ صوت الطلبات مفعّل';
        }
        await refresh();
        window.setInterval(() => loadOrders().catch(error => showError(error.message)), 5000);
    })();
})();
