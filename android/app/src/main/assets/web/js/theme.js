/* بيت ماما — ثيم مشترك للوضع الليلي */
(function () {
    'use strict';

    /* ---------- الوضع الليلي ---------- */
    var saved = null;
    try { saved = localStorage.getItem('beitMamaTheme'); } catch (e) { }
    var prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
    if (saved === 'dark' || (!saved && prefersDark)) {
        document.documentElement.classList.add('dark');
    }
    // تطبيق فوري قبل الرسم لتجنب الوميض
    document.addEventListener('DOMContentLoaded', function () {
        var navbar = document.querySelector('.navbar, .nav-container');
        if (!navbar) return;
        var btn = document.createElement('button');
        btn.className = 'theme-toggle';
        btn.type = 'button';
        btn.setAttribute('aria-pressed', 'false');
        function renderIcon() {
            var dark = document.documentElement.classList.contains('dark');
            btn.textContent = dark ? '☀️' : '🌙';
            btn.setAttribute('aria-label', dark ? 'تفعيل الوضع النهاري' : 'تفعيل الوضع الليلي');
            btn.setAttribute('aria-pressed', String(dark));
            btn.title = dark ? 'تفعيل الوضع النهاري' : 'تفعيل الوضع الليلي';
        }
        btn.addEventListener('click', function () {
            var dark = document.documentElement.classList.toggle('dark');
            try { localStorage.setItem('beitMamaTheme', dark ? 'dark' : 'light'); } catch (e) { }
            renderIcon();
        });
        renderIcon();
        (navbar.querySelector('.nav-actions') || navbar).appendChild(btn);
    });

})();
