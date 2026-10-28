#!/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$DIR"

# استخدم الجافا المضمنة في المشروع
export JAVA_HOME="$DIR/.jdk/jdk-17.0.10+7/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

# كلمة مرور المدير الافتراضية للوحة التحكم
export BEITMAMA_ADMIN_PASSWORD="${BEITMAMA_ADMIN_PASSWORD:-AdminPassword2026@}"

PORT=8080
if lsof -Pi :8080 -sTCP:LISTEN -t >/dev/null 2>&1 ; then
    PORT=8085
fi

echo "========================================================"
echo "   Mom's House (بيت ماما) - جاري التشغيل..."
echo "========================================================"
echo "📍 المنفذ المستخدم: $PORT"
echo "🌐 رابط الموقع: http://localhost:$PORT/"
echo "⚙️  لوحة تحكم الإدارة: http://localhost:$PORT/admin-dashboard.html"
echo "🔑 بيانات دخول الإدارة:"
echo "   - البريد: admin@beitmama.com"
echo "   - كلمة المرور: $BEITMAMA_ADMIN_PASSWORD"
echo "========================================================"

# فتح لوحة التحكم تلقائياً في المتصفح بعد ثانية
(sleep 2 && open "http://localhost:$PORT/admin-dashboard.html") &

# تشغيل الخادم والواجهة
exec "$JAVA_HOME/bin/java" -Dbeitmama.port=$PORT -cp "target/classes:lib/*" com.beitmama.Main
