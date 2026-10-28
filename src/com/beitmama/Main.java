package com.beitmama;

import com.beitmama.dao.DatabaseManager;
import com.beitmama.service.BeitMamaService;
import com.beitmama.server.BeitMamaWebServer;
import com.beitmama.ui.LoginFrame;

import javax.swing.*;
import java.awt.GraphicsEnvironment;

public class Main {
    public static void main(String[] args) {
        System.out.println("=== جاري بدء تشغيل مشروع بيت ماما (Beit Mama) ===");

        // 1. تهيئة قاعدة البيانات والجداول
        DatabaseManager.initializeDatabase();
        System.out.println("✔ تم التحقق من قاعدة البيانات بنجاح.");

        // 2. تشغيل طبقة الخدمات
        BeitMamaService service = new BeitMamaService();

        // 3. تشغيل خادم الويب والموبايل الداخلي (Port 8080)
        try {
            int port = Integer.getInteger("beitmama.port", 8080);
            BeitMamaWebServer server = new BeitMamaWebServer(port, service);
            server.start();
        } catch (Exception e) {
            System.err.println("فشل تشغيل خادم الويب: " + e.getMessage());
        }

        // 4. تشغيل واجهة Java Swing للإدارة (إذا كانت البيئة تدعم العرض الرسومي)
        if (!GraphicsEnvironment.isHeadless()) {
            SwingUtilities.invokeLater(() -> {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {}
                LoginFrame login = new LoginFrame(service);
                login.setVisible(true);
            });
        } else {
            System.out.println("النظام يعمل في وضع Headless (خادم فقط بدون شاشة ديسكتوب).");
        }
    }
}
