package com.beitmama;

import com.beitmama.dao.DatabaseManager;
import com.beitmama.service.BeitMamaService;
import com.beitmama.server.BeitMamaWebServer;
import com.beitmama.ui.LoginFrame;

import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.util.concurrent.CountDownLatch;

public class Main {
    public static void main(String[] args) {
        System.out.println("=== جاري بدء تشغيل مشروع بيت ماما (Beit Mama) ===");

        
        DatabaseManager.initializeDatabase();
        System.out.println("✔ تم التحقق من قاعدة البيانات بنجاح.");

        
        BeitMamaService service = new BeitMamaService();

        
        int port = configuredPort();
        BeitMamaWebServer server = new BeitMamaWebServer(port, service);
        boolean headless = Boolean.parseBoolean(System.getenv("BEITMAMA_HEADLESS"));
        try {
            server.start();
            System.out.println("✔ خادم الويب يعمل الآن على: http://localhost:" + port + "/");
        } catch (Exception e) {
            System.err.println("فشل تشغيل خادم الويب: " + e.getMessage());
            if (headless) {
                throw new IllegalStateException("Unable to start web server", e);
            }
        }

        if (headless) {
            CountDownLatch shutdown = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                server.stop();
                shutdown.countDown();
            }, "beitmama-shutdown"));
            try {
                shutdown.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                server.stop();
            }
            return;
        }

        
        System.out.println("\n-------------------------------------------");
        System.out.println("اختر طريقة فتح لوحة تحكم الإدارة:");
        System.out.println("1. فتح لوحة التحكم في المتصفح تلقائياً (نفس التصميم الحديث في الصورة)");
        System.out.println("2. الواجهة النصية (Terminal CLI)");
        System.out.println("3. نافذة سطح المكتب (Java Swing Desktop Window)");
        System.out.print("اختيارك [الافتراضي 1]: ");

        java.util.Scanner scanner = new java.util.Scanner(System.in);
        String choice;
        if (scanner.hasNextLine()) {
            choice = scanner.nextLine().trim();
        } else {
            choice = System.getenv().getOrDefault("BEITMAMA_UI", "1");
        }
        if (choice.isEmpty()) choice = "1";

        if ("2".equals(choice)) {
            System.out.println("جاري تشغيل لوحة التحكم في الترمنال...");
            com.beitmama.ui.AdminTerminalUI terminalUI = new com.beitmama.ui.AdminTerminalUI(service);
            terminalUI.start();
        } else if ("3".equals(choice)) {
            if (!GraphicsEnvironment.isHeadless()) {
                SwingUtilities.invokeLater(() -> {
                    try {
                        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                    } catch (Exception ignored) {}
                    LoginFrame login = new LoginFrame(service);
                    login.setVisible(true);
                });
                System.out.println("✔ تم فتح نافذة لوحة التحكم لسطح المكتب (Swing Window).");
            } else {
                System.out.println("النظام يعمل في وضع Headless، سيتم التحويل إلى واجهة الترمنال.");
                com.beitmama.ui.AdminTerminalUI terminalUI = new com.beitmama.ui.AdminTerminalUI(service);
                terminalUI.start();
            }
        } else {
            
            String url = "http://localhost:" + configuredPort() + "/admin-dashboard.html";
            System.out.println("🚀 جاري فتح لوحة التحكم في المتصفح: " + url);
            try {
                if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                    java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
                } else {
                    String os = System.getProperty("os.name").toLowerCase();
                    if (os.contains("mac")) {
                        Runtime.getRuntime().exec(new String[]{"open", url});
                    } else if (os.contains("nix") || os.contains("nux")) {
                        Runtime.getRuntime().exec(new String[]{"xdg-open", url});
                    } else if (os.contains("win")) {
                        Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
                    }
                }
                System.out.println("✔ تم فتح لوحة التحكم في المتصفح بنجاح!");
            } catch (Exception ex) {
                System.out.println("يمكنك فتح هذا الرابط يدوياً في المتصفح: " + url);
            }
            
            System.out.println("\n(الخادم قيد العمل الآن. اضغط Enter في أي وقت للخروج)");
            scanner.nextLine();
        }
    }

    private static int configuredPort() {
        String port = System.getenv("PORT");
        return port == null || port.isBlank()
                ? Integer.getInteger("beitmama.port", 8080)
                : Integer.parseInt(port);
    }
}
