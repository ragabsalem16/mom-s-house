# 🍲 بيت ماما (Beit Mama)

> نظام إدارة وطلب الأكل البيتي — Java Swing + خادم ويب مدمج + نشر Docker

---

## 📋 نظرة عامة

**بيت ماما** منصة تربط العملاء بالأمهات اللي بيعملوا أكل بيتي. المشروع مكتوب بالكامل بـ **Java** ويشمل:

| المكوّن | التفاصيل |
|---|---|
| **واجهة الإدارة** | Java Swing (تشتغل على سطح المكتب) |
| **خادم الويب** | HTTP Server مدمج في Java |
| **قاعدة البيانات** | SQLite (افتراضي) أو MySQL |
| **النشر** | Docker + Caddy (HTTPS تلقائي) |

---

## 📁 هيكل المشروع

```text
beit-mama/
├── pom.xml                          # Maven — Java 25، الاعتماديات
├── Dockerfile                       # بناء صورة Docker للخادم
├── compose.yaml                     # تشغيل التطبيق + Caddy (HTTPS)
├── Caddyfile                        # تمرير HTTPS → خادم Java
├── .env.example                     # قالب متغيرات البيئة
├── .gitignore                       # يستثني .env وقاعدة البيانات وملف JKS
├── db/
│   ├── beit_mama.db                 # قاعدة SQLite (مستثناة من Git)
│   └── schema.sql                   # مخطط MySQL
├── src/com/beitmama/
│   ├── Main.java                    # نقطة الدخول: DB + Server + Swing
│   ├── model/                       # User, Product, Order, Review, Invitation…
│   ├── dao/                         # DatabaseManager, UserDao, OrderDao…
│   ├── service/                     # BeitMamaService (منطق الأعمال)
│   ├── ui/                          # LoginFrame, AdminDashboardFrame, SwingLauncher
│   ├── server/                      # BeitMamaWebServer (HTTP + REST API)
│   └── util/                        # SecurityUtil (تشفير كلمات المرور)
├── web/                             # صفحات HTML/CSS/JS يقدمها الخادم
│   ├── index.html                   # المتجر — الصفحة الرئيسية
│   ├── login.html                   # تسجيل الدخول وإنشاء الحساب
│   ├── join.html                    # تسجيل الأم (بدعوة)
│   ├── mother-dashboard.html        # لوحة تحكم الأم
│   ├── my-orders.html               # طلبات العميل
│   ├── admin-dashboard.html         # لوحة تحكم المدير
│   ├── profile.html                 # الملف الشخصي
│   ├── account.html                 # إعدادات الحساب
│   ├── privacy-policy.html          # سياسة الخصوصية
│   ├── css/theme.css
│   ├── js/theme.js
│   └── images/
└── android/                         # أصول Gradle وصفحات Android المضمّنة
    ├── build.gradle
    ├── settings.gradle
    ├── GOOGLE_PLAY_GUIDE.md
    └── app/src/main/assets/web/     # نسخة الصفحات المضمّنة في APK
```

---

## ⚙️ متطلبات التشغيل المحلي

| المتطلب | الإصدار |
|---|---|
| **Java JDK** | 25 أو أحدث ([Oracle JDK](https://www.oracle.com/java/technologies/downloads/) / [Temurin](https://adoptium.net/)) |
| **Maven** | 3.9+ (أو استخدم Maven Wrapper لو موجود) |

تحقق من التثبيت:
```bash
java -version
mvn -version
```

---

## 🚀 التشغيل المحلي

### 1. بناء المشروع
```bash
cd beit-mama
mvn compile
```

### 2. تشغيل التطبيق
```bash
mvn exec:java -Dexec.mainClass=com.beitmama.Main
```

عند التشغيل سيفتح التطبيق:
- **نافذة Java Swing** لتسجيل دخول المدير
- **خادم الويب** على: 👉 `http://localhost:8080/`

**بيانات الدخول المحلية الافتراضية:**
| الحقل | القيمة |
|---|---|
| البريد | `admin@beitmama.com` |
| كلمة المرور | `admin123` |

> ⚠️ **تحذير:** لا تستخدم كلمة المرور الافتراضية على خادم عام — غيّرها في `.env` عند النشر.

### تغيير المنفذ (اختياري)
```bash
mvn exec:java -Dexec.mainClass=com.beitmama.Main -Dbeitmama.port=8081
```

### بناء JAR للتوزيع
```bash
mvn package
# الناتج: target/beit-mama-1.0.0.jar
```

---

## 🐳 النشر على خادم Linux (Docker + HTTPS مجاني)

### المتطلبات
- خادم Linux (مثل [Oracle Cloud Always Free](https://www.oracle.com/cloud/free/)) — ARM أو x86
- اسم نطاق مجاني من [DuckDNS](https://www.duckdns.org/)
- Docker و Docker Compose مثبّتان على الخادم

---

### الخطوة 1 — تجهيز الخادم

```bash
# على الخادم
sudo apt update
sudo apt install -y docker.io docker-compose-v2
sudo systemctl enable --now docker
```

افتح المنافذ في جدار حماية Oracle Cloud:
- **TCP 22** — SSH
- **TCP 80** — HTTP (يعيد التوجيه إلى HTTPS)
- **TCP 443** — HTTPS

---

### الخطوة 2 — نقل ملفات المشروع

**من جهازك:**
```bash
cd /path/to/beit-mama

tar -czf /tmp/beitmama-deploy.tar.gz \
  --exclude=./target \
  --exclude=./bin \
  --exclude=./lib \
  --exclude=./db/beit_mama.db \
  --exclude='*.jks' \
  --exclude=./.env \
  --exclude=./android/.gradle \
  .

scp -i /path/to/ssh-key /tmp/beitmama-deploy.tar.gz ubuntu@SERVER_IP:/home/ubuntu/
```

**على الخادم:**
```bash
mkdir -p ~/beitmama
tar -xzf ~/beitmama-deploy.tar.gz -C ~/beitmama
cd ~/beitmama
```

---

### الخطوة 3 — إعداد `.env`

```bash
cp .env.example .env
nano .env
```

املأ القيم التالية:

```env
BEITMAMA_DOMAIN=your-subdomain.duckdns.org
BEITMAMA_PUBLIC_URL=https://your-subdomain.duckdns.org
BEITMAMA_ADMIN_PASSWORD=PasswordQowiStrong16Chars!
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
```

| المتغير | الوصف |
|---|---|
| `BEITMAMA_DOMAIN` | اسم DuckDNS الكامل (بدون `https://`) |
| `BEITMAMA_PUBLIC_URL` | رابط HTTPS الكامل |
| `BEITMAMA_ADMIN_PASSWORD` | كلمة مرور المدير — لا تقل عن 16 حرفاً |
| `GOOGLE_CLIENT_ID` | اختياري — لتسجيل الدخول بـ Google |
| `GOOGLE_CLIENT_SECRET` | اختياري — لتسجيل الدخول بـ Google |

---

### الخطوة 4 — التشغيل

```bash
sudo docker compose up -d --build
```

**التحقق من الحالة:**
```bash
sudo docker compose ps
sudo docker compose logs -f
```

بعد دقيقتين تقريباً يفتح Caddy شهادة HTTPS تلقائياً ويصبح الموقع متاحاً على `https://your-subdomain.duckdns.org`.

---

### أوامر مفيدة

```bash
# إيقاف الخدمات
sudo docker compose down

# إعادة البناء بعد تعديل الكود
sudo docker compose up -d --build

# عرض اللوج المباشر
sudo docker compose logs -f beitmama

# النسخ الاحتياطي لقاعدة البيانات
sudo docker compose exec beitmama cp /data/beit_mama.db /tmp/backup.db
sudo docker cp beitmama:/tmp/backup.db ./beit_mama_backup.db
```

---

## ✨ ميزات المنصة

### العملاء
- تصفح الوجبات والأمهات، والبحث والتصفية حسب التصنيف والسعر والتقييم
- سلة طلبات تجمع وجبات من أم واحدة مع حساب الإجمالي والتحقق من المخزون
- اختيار الاستلام أو التوصيل مع حفظ العنوان ووسيلة التواصل
- تقييم الطلبات المكتملة (مرة واحدة لكل طلب)
- تحديث حالة الطلبات تلقائياً

### الأمهات
- إضافة وتعديل وحذف الوجبات مع تحديد الكمية وتاريخ انتهاء التوفر
- متابعة الطلبات الواردة وتحديث حالتها
- ظهور اسم المنطقة فقط في المتجر — العنوان الكامل يظهر للعميل بعد اعتماد الطلب فقط

### المدير (Swing Desktop)
- إنشاء دعوات الأمهات وإلغاؤها
- اعتماد حسابات الأمهات ومراجعة البيانات
- البحث عن المستخدمين ومتابعة الطلبات والإحصائيات

---

## 🔒 الأمان

| الميزة | التفاصيل |
|---|---|
| **كلمات المرور** | SHA-256 مع Salt عشوائي لكل مستخدم |
| **رموز الدعوة** | تُخزَّن كـ Hash فقط (SHA-256) — الرمز الأصلي لا يُحفظ |
| **منع التلاعب بالأسعار** | الأسعار والمجاميع تُحسب في الخادم داخل `OrderDao` |
| **جلسات الدخول** | Cookie محمية بـ `HttpOnly` و`SameSite` |
| **دعوات الأمهات** | حماية من الاستخدام المتزامن (Concurrency-safe transactions) |

---

## 🗄️ قاعدة البيانات

### SQLite (افتراضي — يعمل بدون إعداد)
الملف يُنشأ تلقائياً عند أول تشغيل في:
- **محلياً:** `db/beit_mama.db`
- **على Docker:** وحدة تخزين دائمة `beit_mama_data:/data/beit_mama.db`

### MySQL (اختياري)
1. أنشئ قاعدة البيانات باستخدام [`db/schema.sql`](./db/schema.sql)
2. عدّل إعداد الاتصال في [`src/com/beitmama/dao/DatabaseManager.java`](./src/com/beitmama/dao/DatabaseManager.java)

---

## 📱 تطبيق Android

مجلد `android/` يحتوي على ملفات Gradle وصفحات الويب المضمّنة في APK.

> عند تعديل أي صفحة HTML تُستخدم في النسختين، تذكر تحديث النسخة في `android/app/src/main/assets/web/` أيضاً.

راجع [`android/GOOGLE_PLAY_GUIDE.md`](./android/GOOGLE_PLAY_GUIDE.md) لإرشادات النشر على Google Play.

---

## ⚠️ ملاحظات مهمة

- **لا ترفع** ملف `.env` أو `db/beit_mama.db` أو ملفات `*.jks` إلى مستودع عام — كلها مستثناة في `.gitignore`
- **كلمة مرور المدير الافتراضية** (`admin123`) للاستخدام المحلي فقط — غيّرها دائماً في بيئة الإنتاج
- **الاستضافة المجانية** (Oracle Cloud) تخضع لسياسة المزود وقد تتغير؛ احتفظ بنسخ احتياطية منتظمة
- **جلسات الدخول** محفوظة في الذاكرة وتنتهي عند إعادة تشغيل الخادم
- **استعادة كلمة المرور** عبر البريد/SMS تتطلب إعداد مزود خارجي (غير مضمّن حالياً)

---

## 📜 الرخصة

مشروع تعليمي — للاستخدام والتطوير بحرية.
