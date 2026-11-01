#!/bin/bash
# ===================================================
# سكريبت نشر بيت ماما على الخادم
# يُشغَّل على خادم Oracle Cloud بعد الإعداد
# ===================================================
set -e

echo "🚀 بدء نشر بيت ماما..."

# تثبيت Docker لو مش موجود
if ! command -v docker &>/dev/null; then
  echo "📦 تثبيت Docker..."
  sudo apt update -y
  sudo apt install -y docker.io docker-compose-v2
  sudo systemctl enable --now docker
  sudo usermod -aG docker "$USER"
fi

# فتح المنافذ
echo "🔓 فتح المنافذ..."
sudo iptables -I INPUT 1 -p tcp --dport 80  -j ACCEPT 2>/dev/null || true
sudo iptables -I INPUT 1 -p tcp --dport 443 -j ACCEPT 2>/dev/null || true
sudo apt install -y iptables-persistent 2>/dev/null || true
sudo netfilter-persistent save 2>/dev/null || true

# جلب أحدث كود من GitHub
if [ -d ~/beit-mama ]; then
  echo "🔄 تحديث الكود..."
  cd ~/beit-mama
  git pull origin main
else
  echo "📥 تنزيل الكود..."
  git clone https://github.com/ragabsalem16/mom-s-house.git ~/beit-mama
  cd ~/beit-mama
fi

# إعداد .env لو مش موجود
if [ ! -f .env ]; then
  cp .env.example .env
  echo ""
  echo "⚠️  اكتب بياناتك في ملف .env:"
  echo "    BEITMAMA_DOMAIN=اسمك.duckdns.org"
  echo "    BEITMAMA_PUBLIC_URL=https://اسمك.duckdns.org"
  echo "    BEITMAMA_ADMIN_PASSWORD=كلمة_مرور_قوية"
  echo ""
  read -p "اضغط Enter بعد تعديل .env..." _
fi

# بناء وتشغيل
echo "🐳 بناء وتشغيل Docker..."
sudo docker compose up -d --build

echo ""
echo "✅ تم النشر!"
echo "📊 الحالة:"
sudo docker compose ps
echo ""
echo "🌐 الموقع متاح على: https://$(grep BEITMAMA_DOMAIN .env | cut -d= -f2)"
