# 🍲 بيت ماما (Beit Mama)

> نظام إدارة وطلب الأكل البيتي — Java Swing وخادم API وواجهة ويب وFirestore

---

## 📋 نظرة عامة

**بيت ماما** منصة تربط العملاء بالأمهات اللي بيعملوا أكل بيتي. يتكون من:

| المكوّن | التفاصيل |
|---|---|
| **واجهة الإدارة** | Java Swing (تشتغل على سطح المكتب) |
| **الموقع وواجهة API** | Render Free، على رابط واحد |
| **قاعدة البيانات** | Cloud Firestore (مشروع Firebase) |

---

## 📁 هيكل المشروع

```text
beit-mama/
├── pom.xml                          # Maven — Java 25، الاعتماديات
├── render.yaml                     # إعداد نشر الموقع وAPI على Render Free
├── Dockerfile                      # بناء وتشغيل Java والواجهة معاً
├── firestore.rules                 # منع الوصول المباشر من المتصفح إلى Firestore
├── .gitignore
├── src/com/beitmama/
│   ├── Main.java                    # نقطة الدخول: DB + Server + Swing
│   ├── model/                       # User, Product, Order, Review, Invitation…
│   ├── dao/                         # DatabaseManager, UserDao, OrderDao…
│   ├── service/                     # BeitMamaService (منطق الأعمال)
│   ├── ui/                          # LoginFrame, AdminDashboardFrame, SwingLauncher
│   ├── server/                      # BeitMamaWebServer (HTTP + REST API)
│   └── util/                        # SecurityUtil (تشفير كلمات المرور)
├── web/                             # صفحات HTML/CSS/JS الثابتة
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
| **Firebase CLI وGoogle Cloud CLI** | غير مطلوبين لنشر Render |
| **Google Cloud credentials** | مطلوبة في Render كملف سري للوصول إلى Firestore |

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
export FIREBASE_PROJECT_ID="your-firebase-project-id"
export GOOGLE_APPLICATION_CREDENTIALS="/absolute/path/to/service-account.json"
mvn exec:java -Dexec.mainClass=com.beitmama.Main
```

يبدأ خادم Java المحلي على `http://localhost:8080/`، ويحتاج بيانات اعتماد Firestore.

**بيانات المدير الافتراضية للتطوير فقط عند إنشاء قاعدة بيانات جديدة:**
| الحقل | القيمة |
|---|---|
| البريد | `admin@beitmama.com` |
| كلمة المرور | `admin123` |

لا تستخدم كلمة المرور الافتراضية في الإنتاج. استخدم `BEITMAMA_ADMIN_PASSWORD` بطول 16 حرفاً على الأقل.

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

## 🚀 نشر الموقع الكامل مجاناً على Render مع Firestore

يستضيف Render الواجهة وخادم Java على رابط واحد؛ بذلك تعمل الجلسات وتسجيل الدخول والطلبات من نفس النطاق. تُخزَّن بيانات التطبيق في Firestore. هذا الإعداد لا يحتاج Google Cloud CLI أو Firebase CLI أو تفعيل فوترة Google Cloud.

### 1 — تجهيز Firestore
1. افتح مشروع Firebase `beitmama` وأنشئ قاعدة Cloud Firestore بوضع Native إن لم تكن موجودة.
2. من إعدادات مشروع Firebase افتح **Service accounts**، وأنشئ مفتاحاً خاصاً بصيغة JSON. لا تضع المفتاح في المشروع أو GitHub ولا ترسله في المحادثة.

### 2 — نشر الموقع وخادم Java على Render
1. ارفع ملفات المشروع المطلوبة إلى مستودع GitHub الذي ستربطه بـRender، مع التأكد من عدم رفع مفتاح JSON أو أي كلمة مرور.
2. سجّل الدخول إلى Render، واختر **New → Blueprint**، ثم اربط مستودع GitHub. سيقرأ Render [render.yaml](./render.yaml) ويبني التطبيق من [Dockerfile](./Dockerfile).
3. من إعدادات الخدمة في Render، أضف **Secret File** باسم `firebase-service-account.json`، والصق محتوى مفتاح حساب الخدمة. لا ترسل المفتاح لأحد.
4. أضف متغير `BEITMAMA_ADMIN_PASSWORD` من إعدادات Render بقيمة سرية فريدة لا تقل عن 16 حرفاً.
5. ابدأ النشر، ثم افتح رابط Render الذي يظهر في لوحة الخدمة. هذا هو رابط الموقع الكامل؛ رابط Firebase Hosting الحالي يستضيف الواجهة الثابتة فقط.

الإعداد مضبوط على Firebase Project ID باسم `beitmama`. إذا كان معرف مشروعك مختلفاً، غيّر القيمة في [render.yaml](./render.yaml) قبل النشر. تسجيل الدخول باستخدام Google اختياري، ويحتاج ضبط OAuth منفصلاً.

### حدود المجانية
- Render Free قد يوقف الخدمة بعد فترة من عدم الاستخدام؛ أول زيارة بعدها قد تستغرق وقتاً حتى يبدأ الخادم.
- رفع الصور والفيديو معطل؛ نظام ملفات Render مؤقت.
- حصص Render وFirestore محدودة وقد تتغير؛ راجع شروط الخطة وحصص Firebase، ولا يوجد ضمان لاستضافة غير محدودة أو توفر دائم.

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
| **جلسات الدخول** | Cookie محمية بـ `HttpOnly`؛ واجهة الموقع وAPI على نفس رابط Render |
| **دعوات الأمهات** | حماية من الاستخدام المتزامن (Concurrency-safe transactions) |
| **Firestore** | المتصفح ممنوع من القراءة والكتابة مباشرة؛ الاتصال بقاعدة البيانات من خادم Java فقط |

---

## 🗄️ قاعدة البيانات والملفات

يستخدم خادم Java Cloud Firestore للحسابات والملفات الشخصية والتصنيفات والوجبات والطلبات وعناصر الطلب والتقييمات والدعوات. لا توجد عملية استيراد تلقائية لبيانات SQLite السابقة؛ يلزم استيراد منفصل قبل استخدام بيانات قديمة.

---

## 📱 تطبيق Android

مجلد `android/` يحتوي على ملفات Gradle وصفحات الويب المضمّنة في APK.

> نشر Render يخص واجهة `web/` وخادم Java. أصول Android منفصلة وتبقى على تكوينها الحالي؛ لا تنسخ تغييرات الويب إليها إلا عند تنفيذ تحديث Android بشكل مستقل.

راجع [`android/GOOGLE_PLAY_GUIDE.md`](./android/GOOGLE_PLAY_GUIDE.md) لإرشادات النشر على Google Play.

---

## ⚠️ ملاحظات مهمة

- **لا ترفع** مفاتيح حساب الخدمة أو أي أسرار إلى مستودع عام
- **كلمة مرور المدير الافتراضية** (`admin123`) للاستخدام المحلي فقط — غيّرها دائماً في بيئة الإنتاج
- **حصص الاستضافة المجانية** محدودة وقد تتغير؛ راجع شروط Render وFirebase الحالية
- **جلسات الدخول** محفوظة في الذاكرة وتنتهي عند إعادة تشغيل الخادم
- **استعادة كلمة المرور** عبر البريد/SMS تتطلب إعداد مزود خارجي (غير مضمّن حالياً)

---

## 📜 الرخصة

مشروع تعليمي — للاستخدام والتطوير بحرية.
