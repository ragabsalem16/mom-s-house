// Usage: <custom-dock-bottom-bar selected-index="0"></custom-dock-bottom-bar>
// Custom Dock Bottom Bar — Exactly 4 Modern Tabs:
// 0: الرئيسية (Home)
// 1: الطلبات (Orders)
// 2: الإشعارات (Notifications)
// 3: تسجيل الدخول / حساب العميل (Sign In / Profile)

class BeitMamaHeader extends HTMLElement {
    constructor() {
        super();
        this.attachShadow({ mode: 'open' });
        this.shadowRoot.innerHTML = `
            <style>
                :host { display: block; position: sticky; top: 0; z-index: 100; }
                * { box-sizing: border-box; }
                header {
                    min-height: 86px;
                    padding: 8px max(24px, calc((100vw - 1200px) / 2));
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    gap: 24px;
                    direction: rtl;
                    background: #fff;
                    border-bottom: 1px solid #f4eff1;
                    box-shadow: 0 3px 14px rgba(30, 20, 24, .035);
                    font-family: Cairo, system-ui, sans-serif;
                }
                :host-context(:root.dark) header {
                    background: rgba(36, 27, 31, .96);
                    border-bottom-color: #44363B;
                    box-shadow: 0 3px 14px rgba(0, 0, 0, .22);
                }
                .brand {
                    display: inline-flex;
                    flex-direction: row-reverse;
                    align-items: center;
                    gap: 10px;
                    color: #181519;
                    text-decoration: none;
                    flex: 0 0 auto;
                }
                .brand svg { width: 62px; height: 62px; flex: 0 0 auto; }
                .brand-name {
                    display: flex;
                    flex-direction: column;
                    color: #D4145A;
                    font-size: 36px;
                    font-weight: 900;
                    letter-spacing: -.8px;
                    line-height: .87;
                }
                .brand-name strong { color: #211d20; font-size: .94em; }
                :host-context(:root.dark) .brand-name strong { color: #F5EEF0; }
                :host-context(:root.dark) .brand svg path:first-child { fill: #241B1F; }
                .actions {
                    display: flex;
                    flex-direction: row;
                    align-items: center;
                    gap: clamp(10px, 1.3vw, 18px);
                    margin-inline-end: auto;
                    direction: ltr;
                }
                .action {
                    position: relative;
                    display: grid;
                    place-items: center;
                    width: 56px;
                    height: 56px;
                    padding: 0;
                    border: 1px solid #f3eff0;
                    border-radius: 50%;
                    background: #fff;
                    color: #141216;
                    text-decoration: none;
                    cursor: pointer;
                    box-shadow: 0 4px 16px rgba(30, 20, 24, .09);
                    transition: color .2s ease, transform .2s ease, box-shadow .2s ease;
                }
                .action:hover { color: #D4145A; transform: translateY(-2px); }
                .action:focus-visible { outline: 3px solid #E63956; outline-offset: 3px; }
                .action svg { width: 34px; height: 34px; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
                .theme-action {
                    width: 56px;
                    height: 56px;
                }
                .theme-action svg { width: 29px; height: 29px; }
                .theme-action .moon,
                .theme-action .sun {
                    transform-origin: center;
                    transition: opacity .22s ease, transform .28s ease;
                }
                .theme-action .moon { fill: #F7B916; stroke: #F7B916; stroke-width: 1; opacity: 1; }
                .theme-action .sun { fill: #F7B916; stroke: #F7B916; stroke-width: 1.5; opacity: 0; transform: rotate(-90deg) scale(.65); }
                :host-context(:root.dark) .theme-action .moon { opacity: 0; transform: rotate(90deg) scale(.65); }
                :host-context(:root.dark) .theme-action .sun { opacity: 1; transform: rotate(0) scale(1); }
                @media (prefers-reduced-motion: reduce) {
                    .theme-action .moon,
                    .theme-action .sun { transition: none; }
                }
                :host-context(:root.dark) .action {
                    background: #30252A;
                    border-color: #4B3A41;
                    color: #F5EEF0;
                    box-shadow: 0 4px 16px rgba(0, 0, 0, .2);
                }
                :host-context(:root.dark) .action:hover {
                    background: #3B2931;
                    color: #FF9AAF;
                }
                .badge {
                    position: absolute;
                    top: -2px;
                    right: -7px;
                    min-width: 20px;
                    height: 20px;
                    padding: 0 5px;
                    display: none;
                    align-items: center;
                    justify-content: center;
                    border: 2px solid #fff;
                    border-radius: 999px;
                    background: #E50052;
                    color: #fff;
                    font: 800 11px/1 Cairo, sans-serif;
                }
                :host-context(:root.dark) .badge { border-color: #241B1F; }
                @media (max-width: 480px) {
                    header { min-height: 76px; padding: 7px 14px; gap: 10px; }
                    .brand { gap: 5px; }
                    .brand svg { width: 52px; height: 52px; }
                    .brand-name { font-size: 29px; }
                    .actions { gap: 10px; }
                    .action { width: 44px; height: 44px; }
                    .action svg { width: 27px; height: 27px; }
                    .theme-action { width: 46px; height: 46px; }
                    .theme-action svg { width: 25px; height: 25px; }
                    .badge { top: -5px; right: -8px; min-width: 18px; height: 18px; font-size: 10px; }
                }
                @media (max-width: 360px) {
                    .actions { gap: 11px; }
                    .brand-name { font-size: 26px; }
                    .brand svg { width: 46px; height: 46px; }
                }
            </style>
            <header>
                <a class="brand" href="index.html" aria-label="بيت ماما - الرئيسية" dir="rtl">
                    <svg viewBox="0 0 72 72" aria-hidden="true">
                        <path d="M11 33 36 13l25 20v29H11V33Z" fill="#fff" stroke="#D4145A" stroke-width="4" stroke-linejoin="round"/>
                        <path d="M27 62V40h18v22" fill="none" stroke="#D4145A" stroke-width="3.5" stroke-linejoin="round"/>
                        <path d="M36 51s-8-5.8-8-10.2a4.5 4.5 0 0 1 8-2.8 4.5 4.5 0 0 1 8 2.8C44 45.2 36 51 36 51Z" fill="#D4145A"/>
                        <path d="M53 18c1-5 4-9 9-12M54 15c-5-1-8-4-9-9M57 12c5-1 9-4 11-8" fill="none" stroke="#438B37" stroke-width="3" stroke-linecap="round"/>
                        <ellipse cx="48" cy="7" rx="4" ry="7" transform="rotate(-35 48 7)" fill="#6BAE45"/>
                        <ellipse cx="62" cy="8" rx="4" ry="7" transform="rotate(32 62 8)" fill="#398B3F"/>
                    </svg>
                    <span class="brand-name"><span>بيت</span><strong>ماما</strong></span>
                </a>
                <div class="actions" aria-label="أدوات سريعة">
                    <button class="action theme-action" type="button" aria-label="تفعيل الوضع الليلي" title="تفعيل الوضع الليلي" aria-pressed="false">
                        <svg viewBox="0 0 32 32" aria-hidden="true">
                            <path class="moon" d="M22.7 21.3A12.2 12.2 0 0 1 10.6 9.2a12 12 0 1 0 12.1 12.1Z"/>
                            <circle class="sun" cx="16" cy="16" r="6"/>
                            <path class="sun" d="M16 2v3M16 27v3M2 16h3M27 16h3M6.1 6.1l2.1 2.1m15.6 15.6 2.1 2.1m0-19.8-2.1 2.1M8.2 23.8l-2.1 2.1"/>
                        </svg>
                    </button>
                    <button class="action cart-action" type="button" aria-label="سلة التسوق" title="سلة التسوق">
                        <span class="badge" id="cartBadge">0</span>
                        <svg viewBox="0 0 32 32" aria-hidden="true"><circle cx="11" cy="27" r="1.7"/><circle cx="25" cy="27" r="1.7"/><path d="M2 4h4l3.2 16a2 2 0 0 0 2 1.6h12.5a2 2 0 0 0 2-1.6L29 10H7"/></svg>
                    </button>
                    <button class="action notification-action" type="button" aria-label="الإشعارات" title="الإشعارات">
                        <span class="badge" id="notificationBadge">0</span>
                        <svg viewBox="0 0 32 32" aria-hidden="true"><path d="M24 13a8 8 0 0 0-16 0c0 9-4 11-4 11h24s-4-2-4-11M13 29a3 3 0 0 0 6 0"/></svg>
                    </button>
                </div>
            </header>
        `;
    }

    connectedCallback() {
        this.shadowRoot.querySelector('.theme-action').addEventListener('click', () => this.toggleTheme());
        this.shadowRoot.querySelector('.cart-action').addEventListener('click', () => {
            if (typeof window.openCart === 'function') window.openCart();
            else window.location.href = 'index.html#foods';
        });
        this.shadowRoot.querySelector('.notification-action').addEventListener('click', () => {
            if (typeof window.toggleNotifications === 'function') window.toggleNotifications();
            else window.location.href = 'notifications.html';
        });
        this.syncBadges();
        window.addEventListener('storage', () => this.syncBadges());
        window.addEventListener('beitMama:notificationsUpdated', () => this.syncBadges());
        this.syncTheme();
    }

    syncTheme() {
        let savedTheme = null;
        try {
            savedTheme = localStorage.getItem('beitMamaTheme');
        } catch (error) {
            console.warn('تعذر قراءة تفضيل المظهر.', error);
        }
        const prefersDark = window.matchMedia?.('(prefers-color-scheme: dark)').matches;
        document.documentElement.classList.toggle('dark', savedTheme === 'dark' || (!savedTheme && prefersDark));
        const isDark = document.documentElement.classList.contains('dark');
        const button = this.shadowRoot.querySelector('.theme-action');
        button.setAttribute('aria-label', isDark ? 'تفعيل الوضع النهاري' : 'تفعيل الوضع الليلي');
        button.title = isDark ? 'تفعيل الوضع النهاري' : 'تفعيل الوضع الليلي';
        button.setAttribute('aria-pressed', String(isDark));
    }

    toggleTheme() {
        const isDark = document.documentElement.classList.toggle('dark');
        try {
            localStorage.setItem('beitMamaTheme', isDark ? 'dark' : 'light');
        } catch (error) {
            console.error('تعذر حفظ تفضيل المظهر.', error);
        }
        const button = this.shadowRoot.querySelector('.theme-action');
        button.setAttribute('aria-label', isDark ? 'تفعيل الوضع النهاري' : 'تفعيل الوضع الليلي');
        button.title = isDark ? 'تفعيل الوضع النهاري' : 'تفعيل الوضع الليلي';
        button.setAttribute('aria-pressed', String(isDark));
    }

    syncBadges() {
        let notifications = [];
        let cart = [];
        try {
            notifications = JSON.parse(localStorage.getItem('beitMama_notifications') || '[]');
            cart = JSON.parse(localStorage.getItem('beitMama_cart') || '[]');
        } catch (error) {
            console.warn('تعذر قراءة شارات بيت ماما من التخزين المحلي.', error);
        }
        const unread = Array.isArray(notifications) ? notifications.filter(item => item.unread).length : 0;
        const cartCount = Array.isArray(cart) ? cart.reduce((total, item) => total + Number(item.quantity || 0), 0) : 0;
        this.updateBadge('notificationBadge', unread);
        this.updateBadge('cartBadge', cartCount);
    }

    updateBadge(id, count) {
        const badge = this.shadowRoot.getElementById(id);
        badge.textContent = count > 99 ? '99+' : String(count);
        badge.style.display = count > 0 || id === 'cartBadge' ? 'flex' : 'none';
    }
}

if (!customElements.get('beit-mama-header')) {
    customElements.define('beit-mama-header', BeitMamaHeader);
}

class CustomDockBottomBar extends HTMLElement {
    static get observedAttributes() {
        return ['selected-index', 'badge-count'];
    }

    constructor() {
        super();
        this.attachShadow({ mode: 'open' });
        this._selectedIndex = 0;
        this._badgeCount = 0;
        this.onTap = null;
        this._productPollTimer = null;

        this.shadowRoot.innerHTML = `
            <style>
                :host {
                    --dock-icon: #684651;
                    --dock-active: #8A0034;
                    --dock-highlight: #FF758F;

                    position: fixed !important;
                    z-index: 1000 !important;
                    left: 50% !important;
                    bottom: calc(12px + env(safe-area-inset-bottom, 0px)) !important;
                    display: block !important;
                    width: min(370px, calc(100vw - 36px)) !important;
                    transform: translateX(-50%) !important;
                    color: var(--dock-icon);
                    font-family: 'Cairo', system-ui, -apple-system, sans-serif;
                    -webkit-tap-highlight-color: transparent;
                    user-select: none;
                }

                :host-context(:root.dark) {
                    --dock-icon: #E8DDE1;
                    --dock-active: #FF9AAF;
                }

                * { box-sizing: border-box; }

                nav {
                    display: grid;
                    grid-template-columns: repeat(4, 1fr);
                    align-items: center;
                    gap: 5px;
                    min-height: 58px;
                    padding: 5px 8px;
                    border: 1px solid rgba(138, 0, 52, 0.18);
                    border-radius: 26px;
                    background: linear-gradient(135deg, rgba(255, 213, 226, .96), rgba(239, 157, 183, .94) 56%, rgba(255, 224, 233, .96));
                    box-shadow: 0 12px 30px rgba(112, 0, 40, .18), inset 0 1px 0 rgba(255, 255, 255, .72);
                    -webkit-backdrop-filter: blur(18px) saturate(150%);
                    backdrop-filter: blur(18px) saturate(150%);
                    direction: rtl;
                }

                :host-context(:root.dark) nav {
                    border-color: rgba(255, 154, 175, .28);
                    background: linear-gradient(135deg, rgba(67, 31, 44, .96), rgba(49, 27, 37, .95) 56%, rgba(73, 35, 49, .96));
                    box-shadow: 0 12px 30px rgba(0, 0, 0, .34), inset 0 1px 0 rgba(255, 255, 255, .08);
                }

                button {
                    position: relative;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    justify-content: center;
                    gap: 3px;
                    width: 100%;
                    height: 46px;
                    padding: 0;
                    border: 0;
                    border-radius: 16px;
                    background: transparent;
                    color: inherit;
                    cursor: pointer;
                    outline: none;
                    transition: all 0.24s cubic-bezier(0.2, 0.85, 0.25, 1);
                }

                button::before {
                    position: absolute;
                    inset: 2px;
                    border: 1px solid transparent;
                    border-radius: 16px;
                    background: transparent;
                    content: "";
                    transition: all 0.26s ease;
                }

                button[aria-current="page"]::before,
                button:hover::before,
                button:focus-visible::before {
                    border-color: rgba(138, 0, 52, .14);
                    background: rgba(255, 255, 255, .46);
                    box-shadow: 0 4px 12px rgba(112, 0, 40, .09);
                }

                button[aria-current="page"]::before {
                    border-color: rgba(138, 0, 52, .2);
                    background: rgba(255, 255, 255, .58);
                }

                :host-context(:root.dark) button[aria-current="page"]::before,
                :host-context(:root.dark) button:hover::before,
                :host-context(:root.dark) button:focus-visible::before {
                    border-color: rgba(255, 154, 175, .38);
                    background: rgba(255, 154, 175, .12);
                }

                .icon {
                    position: relative;
                    z-index: 1;
                    display: grid;
                    place-items: center;
                    width: 23px;
                    height: 23px;
                    color: inherit;
                    transition: color 0.2s ease, transform 0.25s cubic-bezier(0.2, 0.85, 0.25, 1);
                }

                button[aria-current="page"] .icon,
                button:hover .icon,
                button:focus-visible .icon {
                    color: var(--dock-active);
                    transform: translateY(-1px) scale(1.06);
                }

                button[aria-current="page"] .label {
                    color: var(--dock-active);
                    font-weight: 800;
                }

                .label {
                    position: relative;
                    z-index: 1;
                    font-size: 10px;
                    font-weight: 700;
                    color: inherit;
                    text-shadow: 0 1px 3px rgba(250, 247, 248, 0.95);
                    white-space: nowrap;
                    line-height: 1;
                    transition: color 0.2s ease;
                }

                :host-context(:root.dark) .label {
                    text-shadow: 0 1px 3px rgba(25, 20, 22, 0.95);
                }

                svg {
                    display: block;
                    width: 22px;
                    height: 22px;
                    fill: none;
                    stroke: currentColor;
                    stroke-width: 2;
                    stroke-linecap: round;
                    stroke-linejoin: round;
                    filter: drop-shadow(0 1px 2px rgba(250, 247, 248, 0.9));
                }

                :host-context(:root.dark) svg {
                    filter: drop-shadow(0 1px 2px rgba(25, 20, 22, 0.9));
                }

                .badge {
                    position: absolute;
                    top: -2px;
                    left: -4px;
                    background: var(--dock-highlight);
                    color: #fff;
                    font-size: 9.5px;
                    font-weight: 900;
                    min-width: 16px;
                    height: 16px;
                    border-radius: 999px;
                    display: none;
                    align-items: center;
                    justify-content: center;
                    padding: 0 3px;
                    border: 1.5px solid #FAF7F8;
                    box-shadow: 0 2px 6px rgba(230, 57, 86, 0.6);
                }

                :host-context(:root.dark) .badge { border-color: #191416; }

                .avatar-icon {
                    width: 22px;
                    height: 22px;
                    border-radius: 50%;
                    border: 1.8px solid rgba(255, 255, 255, 0.85);
                    background: linear-gradient(135deg, #FF9AAF, #A61B4B);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 11px;
                    color: #fff;
                }

                @media (min-width: 768px) {
                    :host {
                        width: clamp(400px, 42vw, 560px) !important;
                    }
                    button {
                        height: 52px;
                    }
                    .label {
                        font-size: 12px;
                    }
                }
            </style>

            <nav aria-label="الشريط السفلي">
                <!-- 0: Home (الرئيسية) -->
                <button type="button" data-index="0" aria-label="الرئيسية">
                    <span class="icon">
                        <svg viewBox="0 0 24 24">
                            <path d="m3 10 9-7 9 7"></path>
                            <path d="M5.5 9v11h13V9"></path>
                            <path d="M9.5 20v-7h5v7"></path>
                        </svg>
                    </span>
                    <span class="label">الرئيسية</span>
                </button>

                <!-- 1: Orders (الطلبات) -->
                <button type="button" data-index="1" aria-label="الطلبات">
                    <span class="icon">
                        <svg viewBox="0 0 24 24">
                            <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z"></path>
                            <path d="M3 6h18"></path>
                            <path d="M16 10a4 4 0 0 1-8 0"></path>
                        </svg>
                    </span>
                    <span class="label">الطلبات</span>
                </button>

                <!-- 2: Notifications (الإشعارات) -->
                <button type="button" data-index="2" aria-label="الإشعارات">
                    <span class="icon">
                        <span class="badge" id="dockNotifBadge">0</span>
                        <svg viewBox="0 0 24 24">
                            <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path>
                            <path d="M13.73 21a2 2 0 0 1-3.46 0"></path>
                        </svg>
                    </span>
                    <span class="label">الإشعارات</span>
                </button>

                <!-- 3: Profile / Customer Account / Login (حساب العميل / تسجيل الدخول) -->
                <button type="button" data-index="3" aria-label="تسجيل الدخول أو حساب العميل">
                    <span class="icon">
                        <div class="avatar-icon">👤</div>
                    </span>
                    <span class="label" id="profileTabLabel">تسجيل الدخول</span>
                </button>
            </nav>
        `;

        this.shadowRoot.querySelectorAll('button[data-index]').forEach((button) => {
            button.addEventListener('click', (ev) => {
                const index = Number(button.dataset.index);
                this.selectedIndex = index;
                if (typeof this.onTap === 'function') {
                    this.onTap(index);
                } else {
                    this.handleDefaultNavigation(index);
                }
                this.dispatchEvent(new CustomEvent('selection-change', {
                    bubbles: true,
                    composed: true,
                    detail: { index }
                }));
            });
        });
    }

    connectedCallback() {
        if (this.hasAttribute('selected-index')) {
            this.selectedIndex = Number(this.getAttribute('selected-index'));
        } else {
            this._renderSelection();
        }
        this._syncAuthState();
        this._syncBadgeFromStorage();
        this._pollFollowedMothers();
        this._productPollTimer = window.setInterval(() => this._pollFollowedMothers(), 15000);
        window.addEventListener('storage', () => this._syncBadgeFromStorage());
        window.addEventListener('beitMama:notificationsUpdated', () => {
            this._syncBadgeFromStorage();
            document.querySelector('beit-mama-header')?.syncBadges();
        });
    }

    disconnectedCallback() {
        if (this._productPollTimer !== null) {
            window.clearInterval(this._productPollTimer);
        }
    }

    attributeChangedCallback(name, oldValue, newValue) {
        if (name === 'selected-index' && oldValue !== newValue && newValue !== null) {
            this.selectedIndex = Number(newValue);
        } else if (name === 'badge-count') {
            this.setBadgeCount(Number(newValue || 0));
        }
    }

    get selectedIndex() {
        return this._selectedIndex;
    }

    set selectedIndex(value) {
        const index = Number(value);
        if (!Number.isInteger(index) || index < 0 || index > 3) {
            return;
        }
        this._selectedIndex = index;
        if (this.getAttribute('selected-index') !== String(index)) {
            this.setAttribute('selected-index', String(index));
        }
        this._renderSelection();
    }

    setBadgeCount(count) {
        this._badgeCount = count;
        const badge = this.shadowRoot.getElementById('dockNotifBadge');
        if (!badge) return;
        if (count > 0) {
            badge.style.display = 'flex';
            badge.textContent = count > 99 ? '99+' : count;
        } else {
            badge.style.display = 'none';
        }
    }

    setProfileLabel(text) {
        const lbl = this.shadowRoot.getElementById('profileTabLabel');
        if (lbl && text) lbl.textContent = text;
    }

    _renderSelection() {
        this.shadowRoot.querySelectorAll('button[data-index]').forEach((button) => {
            const selected = Number(button.dataset.index) === this._selectedIndex;
            if (selected) {
                button.setAttribute('aria-current', 'page');
            } else {
                button.removeAttribute('aria-current');
            }
        });
    }

    async _syncAuthState() {
        try {
            const res = await fetch('/api/me');
            if (res.ok) {
                const data = await res.json();
                if (data && data.user) {
                    this.setProfileLabel('حسابي');
                    return;
                }
            }
        } catch (error) {
            console.warn('تعذر التحقق من جلسة العميل.', error);
        }
        this.setProfileLabel('تسجيل الدخول');
    }

    _syncBadgeFromStorage() {
        try {
            const notifs = JSON.parse(localStorage.getItem('beitMama_notifications') || '[]');
            const unread = notifs.filter(n => n.unread).length;
            this.setBadgeCount(unread);
        } catch (error) {
            console.warn('تعذر تحديث شارة الإشعارات.', error);
        }
    }

    async _pollFollowedMothers() {
        let followed;
        try {
            followed = JSON.parse(localStorage.getItem('beitMama_followedMothers') || '[]');
        } catch (error) {
            console.warn('تعذر قراءة قائمة الأمهات المتابَعة.', error);
            return;
        }
        if (!Array.isArray(followed) || followed.length === 0) return;

        try {
            const response = await fetch('/api/products');
            if (!response.ok) throw new Error(`تعذر تحميل الوجبات الجديدة (${response.status}).`);
            const products = await response.json();
            if (!Array.isArray(products)) throw new Error('استجابة قائمة الوجبات غير صالحة.');

            const storageKey = 'beitMama_seenProductIds';
            const storedIds = localStorage.getItem(storageKey);
            const seenIds = new Set(JSON.parse(storedIds || '[]').map(String));
            if (storedIds === null || seenIds.size === 0) {
                products.forEach(product => seenIds.add(String(product.id)));
                localStorage.setItem(storageKey, JSON.stringify([...seenIds]));
                return;
            }

            const notifications = JSON.parse(localStorage.getItem('beitMama_notifications') || '[]');
            const knownNotifications = new Set(notifications.map(item => item.id));
            products.forEach(product => {
                const id = String(product.id);
                const mother = product.sellerName || 'أم طاهية';
                if (seenIds.has(id)) return;
                seenIds.add(id);
                if (!followed.includes(mother)) return;

                const notificationId = `dish_${id}`;
                if (knownNotifications.has(notificationId)) return;
                notifications.unshift({
                    id: notificationId,
                    title: `أكلة جديدة من ${mother}!`,
                    message: `نشرت ${mother} وجبة جديدة: ${product.name} بسعر ${product.price} جنيه.`,
                    time: new Date().toLocaleString('ar-EG'),
                    unread: true,
                    productId: product.id
                });
            });

            localStorage.setItem(storageKey, JSON.stringify([...seenIds].slice(-2000)));
            localStorage.setItem('beitMama_notifications', JSON.stringify(notifications.slice(0, 100)));
            window.dispatchEvent(new CustomEvent('beitMama:notificationsUpdated'));
            this._syncBadgeFromStorage();
            document.querySelector('beit-mama-header')?.syncBadges();
        } catch (error) {
            console.warn('تعذر التحقق من وجبات الأمهات المتابَعة.', error);
        }
    }

    handleDefaultNavigation(index) {
        const path = window.location.pathname;
        if (index === 0) {
            if (path.endsWith('index.html') || path === '/' || path.endsWith('/')) {
                window.scrollTo({ top: 0, behavior: 'smooth' });
            } else {
                window.location.href = 'index.html';
            }
        } else if (index === 1) {
            if (path.endsWith('my-orders.html')) {
                window.scrollTo({ top: 0, behavior: 'smooth' });
            } else {
                window.location.href = 'my-orders.html';
            }
        } else if (index === 2) {
            if (path.endsWith('notifications.html')) {
                window.scrollTo({ top: 0, behavior: 'smooth' });
            } else {
                window.location.href = 'notifications.html';
            }
        } else if (index === 3) {
            if (path.endsWith('profile.html')) {
                window.scrollTo({ top: 0, behavior: 'smooth' });
                return;
            }
            fetch('/api/me')
                .then(r => r.json())
                .then(data => {
                    if (data && data.user) {
                        if (data.user.role === 'ADMIN') window.location.href = 'admin-dashboard.html';
                        else if (data.user.role === 'MOTHER') window.location.href = 'mother-dashboard.html';
                        else window.location.href = 'profile.html';
                    } else {
                        window.location.href = 'login.html';
                    }
                })
                .catch(() => {
                    window.location.href = 'login.html';
                });
        }
    }
}

if (!customElements.get('custom-dock-bottom-bar')) {
    customElements.define('custom-dock-bottom-bar', CustomDockBottomBar);
}