(function () {
    const API_BASE_URL = '';
    const apiBase = API_BASE_URL.trim().replace(/\/+$/, '');
    const firebaseHosting = /\.web\.app$|\.firebaseapp\.com$/.test(window.location.hostname);
    const nativeFetch = window.fetch.bind(window);

    if (firebaseHosting) {
        const showStaticHostingNotice = function () {
            if (document.getElementById('static-hosting-notice')) return;
            const notice = document.createElement('aside');
            notice.id = 'static-hosting-notice';
            notice.setAttribute('role', 'status');
            notice.textContent = 'نسخة عرض مجانية: تسجيل الدخول والطلبات والبيانات المباشرة غير مفعّلة في هذا الموقع.';
            notice.style.cssText = 'position:sticky;top:0;z-index:10000;padding:10px 16px;background:#fff3cd;color:#664d03;text-align:center;border-bottom:1px solid #ffecb5;font:600 14px/1.5 sans-serif';
            document.body.prepend(notice);
        };
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', showStaticHostingNotice, { once: true });
        } else {
            showStaticHostingNotice();
        }
    }

    window.beitMamaResourceUrl = function (resource) {
        return apiBase && typeof resource === 'string' && resource.startsWith('/uploads/')
            ? apiBase + resource : resource;
    };

    window.fetch = function (input, init) {
        const requestUrl = input instanceof Request ? input.url : String(input);
        const url = new URL(requestUrl, window.location.href);
        if (!url.pathname.startsWith('/api/')) return nativeFetch(input, init);

        const crossOriginApi = Boolean(apiBase);
        if (crossOriginApi && url.origin === window.location.origin) {
            url.href = apiBase + url.pathname + url.search + url.hash;
        }
        const options = Object.assign({}, init, {
            credentials: crossOriginApi ? 'include' : 'same-origin'
        });
        if (input instanceof Request) {
            return nativeFetch(new Request(url.href, input), options);
        }
        return nativeFetch(url.href, options);
    };

    if (apiBase) {
        document.addEventListener('click', function (event) {
            const link = event.target instanceof Element ? event.target.closest('a[href^="/api/"]') : null;
            if (!link) return;
            event.preventDefault();
            window.location.assign(apiBase + link.getAttribute('href'));
        });
    }
})();
