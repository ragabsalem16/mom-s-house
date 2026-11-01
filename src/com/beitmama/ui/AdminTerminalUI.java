package com.beitmama.ui;

import com.beitmama.model.*;
import com.beitmama.service.BeitMamaService;

import java.util.List;
import java.util.Scanner;




public class AdminTerminalUI {

    private final BeitMamaService service;
    private final Scanner scanner = new Scanner(System.in);

    public AdminTerminalUI(BeitMamaService service) {
        this.service = service;
    }

    public void start() {
        System.out.println("\n" +
                "  ╔═══════════════════════════════════════════════════════════════════╗\n" +
                "  ║                  🌱 بـيـت مـامـا (Beit Mama)                      ║\n" +
                "  ║             أكل بيتي من قلب ماما - لوحة المتابعة العامة          ║\n" +
                "  ╚═══════════════════════════════════════════════════════════════════╝\n" +
                "       مع كل وجبة في بيت ماما .. أنت بتدعم أسرة منتجة ♡\n");

        
        User admin = null;
        while (admin == null) {
            System.out.print("👤 اسم المستخدم (بريد/هاتف): ");
            if (!scanner.hasNextLine()) { System.out.println("\nلا يوجد إدخال — قم بتشغيل البرنامج من نافذة تفاعلية."); return; }
            String identifier = scanner.nextLine().trim();
            System.out.print("🔑 كلمة المرور: ");
            if (!scanner.hasNextLine()) { System.out.println("\nلا يوجد إدخال — قم بتشغيل البرنامج من نافذة تفاعلية."); return; }
            String password = scanner.nextLine().trim();
            try {
                admin = service.login(identifier, password);
                if (admin == null || !admin.getRole().equalsIgnoreCase("ADMIN")) {
                    System.out.println("⛔ بيانات الدخول غير صحيحة أو ليس لديك صلاحية الإدارة.\n");
                    admin = null;
                }
            } catch (Exception e) {
                System.out.println("⛔ خطأ: " + e.getMessage() + "\n");
            }
        }
        System.out.println("\n✔ أهلاً بك، المدير: " + admin.getName() + "   ● متصل الآن\n");

        boolean running = true;
        while (running) {
            printOverviewDashboard();
            printMenu();
            if (!scanner.hasNextLine()) break;
            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1" -> showMothers();
                case "2" -> showOrders();
                case "3" -> showCustomers();
                case "4" -> showInvitations();
                case "5" -> createInvitation(admin);
                case "6" -> updateMotherStatus();
                case "0" -> {
                    System.out.println("وداعاً! 🌱 بيت ماما - معاً لدعم الأمهات المصرية ♡");
                    running = false;
                }
                default -> System.out.println("⚠️ اختيار غير صحيح، حاول مجدداً.\n");
            }
        }
    }

    private void printOverviewDashboard() {
        List<Order> orders = service.getAllOrders();
        List<User> mothers = service.getAllMothers();
        List<User> customers = service.getAllCustomers();

        double totalRevenue = 0.0;
        int pendingOrders = 0;
        int completedOrders = 0;
        int processingOrders = 0;
        int rejectedOrders = 0;

        for (Order o : orders) {
            double amt = o.getTotalAmount() != null ? o.getTotalAmount().doubleValue() : 0.0;
            totalRevenue += amt;
            String st = o.getStatus() != null ? o.getStatus().toLowerCase() : "";
            if ("pending".equals(st)) pendingOrders++;
            else if ("completed".equals(st)) completedOrders++;
            else if ("preparing".equals(st) || "ready".equals(st) || "confirmed".equals(st)) processingOrders++;
            else if ("rejected".equals(st) || "cancelled".equals(st)) rejectedOrders++;
        }

        System.out.println("┌────────────────────────────────────────────────────────────────────────┐");
        System.out.println("│                       📊 لوحة المتابعة العامة                          │");
        System.out.println("├──────────────────┬──────────────────┬──────────────────┬───────────────┤");
        System.out.printf("│ 💰 إجمالي المبيعات│ 🛍 طلبات معلقة   │ 👥 إجمالي مسجلين │ 🍳 أمهات نشطة │%n");
        System.out.printf("│  %-14.2f ج.م│  %-16d│  %-16d│  %-13d│%n",
                totalRevenue, pendingOrders, (mothers.size() + customers.size()), mothers.size());
        System.out.println("├──────────────────┴──────────────────┴──────────────────┴───────────────┤");
        System.out.printf("│ تفصيل الطلبات: [جديدة: %d]  [تم التوصيل: %d]  [قيد المعالجة: %d]  [مرفوضة: %d] │%n",
                pendingOrders, completedOrders, processingOrders, rejectedOrders);
        System.out.println("└────────────────────────────────────────────────────────────────────────┘");
    }

    private void printMenu() {
        System.out.println("┌─────────────────────────────────┐");
        System.out.println("│           القائمة الرئيسية       │");
        System.out.println("├─────────────────────────────────┤");
        System.out.println("│ 1. عرض الأمهات المسجّلات         │");
        System.out.println("│ 2. عرض الطلبات والأرباح          │");
        System.out.println("│ 3. عرض العملاء                   │");
        System.out.println("│ 4. عرض الدعوات                   │");
        System.out.println("│ 5. إنشاء دعوة جديدة لأم          │");
        System.out.println("│ 6. تحديث حالة اعتماد أم          │");
        System.out.println("│ 0. خروج                          │");
        System.out.println("└─────────────────────────────────┘");
        System.out.print("اختيارك: ");
    }

    private void showMothers() {
        System.out.println("\n── الأمهات المسجّلات ──");
        List<User> mothers = service.getAllMothers();
        if (mothers.isEmpty()) {
            System.out.println("لا توجد أمهات مسجّلات.\n");
            return;
        }
        System.out.printf("%-5s %-20s %-20s %-15s %-10s%n",
                "ID", "الاسم", "البريد", "الهاتف", "الحالة");
        System.out.println("─".repeat(75));
        for (User m : mothers) {
            System.out.printf("%-5d %-20s %-20s %-15s %-10s%n",
                    m.getId(), m.getName(), m.getEmail(), m.getPhone(), m.getStatus());
        }
        System.out.println();
    }

    private void showOrders() {
        System.out.println("\n── الطلبات والأرباح ──");
        List<Order> orders = service.getAllOrders();
        if (orders.isEmpty()) {
            System.out.println("لا توجد طلبات.\n");
            return;
        }
        System.out.printf("%-6s %-15s %-15s %-10s %-12s%n",
                "رقم", "العميل", "الأم", "الإجمالي", "الحالة");
        System.out.println("─".repeat(65));
        double totalRevenue = 0.0;
        for (Order o : orders) {
            double amount = o.getTotalAmount() != null ? o.getTotalAmount().doubleValue() : 0.0;
            totalRevenue += amount;
            System.out.printf("#%-5d %-15s %-15s %-10.2f %-12s%n",
                    o.getId(), o.getCustomerName(), o.getSellerName(),
                    amount, o.getStatus());
        }
        double platformProfit = totalRevenue * 0.10;
        System.out.println("─".repeat(65));
        System.out.printf("💰 إجمالي أرباح ومبيعات الطلبات: %.2f ج.م%n", totalRevenue);
        System.out.printf("📈 أرباح المنصة (العمولة 10%%):   %.2f ج.م%n", platformProfit);
        System.out.println();
    }

    private void showCustomers() {
        System.out.println("\n── العملاء ──");
        List<User> customers = service.getAllCustomers();
        if (customers.isEmpty()) {
            System.out.println("لا توجد عملاء.\n");
            return;
        }
        System.out.printf("%-5s %-20s %-25s %-15s%n",
                "ID", "الاسم", "البريد", "الهاتف");
        System.out.println("─".repeat(70));
        for (User c : customers) {
            System.out.printf("%-5d %-20s %-25s %-15s%n",
                    c.getId(), c.getName(), c.getEmail(), c.getPhone());
        }
        System.out.println();
    }

    private void showInvitations() {
        System.out.println("\n── الدعوات ──");
        List<Invitation> invitations = service.getAllInvitations();
        if (invitations.isEmpty()) {
            System.out.println("لا توجد دعوات.\n");
            return;
        }
        System.out.printf("%-5s %-25s %-12s %-12s%n",
                "ID", "البريد/الهاتف", "تنتهي", "الحالة");
        System.out.println("─".repeat(60));
        for (Invitation inv : invitations) {
            String status = "صالحة";
            if (inv.getRevokedAt() != null) status = "ملغاة";
            else if (inv.getUsedAt() != null) status = "مستخدمة";
            else if (!inv.isValid()) status = "منتهية";
            System.out.printf("%-5d %-25s %-12s %-12s%n",
                    inv.getId(), inv.getEmailOrPhone(), inv.getExpiresAt(), status);
        }
        System.out.println();
    }

    private void createInvitation(User admin) {
        System.out.print("أدخل البريد الإلكتروني أو رقم الهاتف للأم: ");
        String target = scanner.nextLine().trim();
        if (target.isEmpty()) {
            System.out.println("⚠️ لا يمكن ترك الحقل فارغاً.\n");
            return;
        }
        System.out.print("مدة صلاحية الدعوة بالأيام (افتراضي 7): ");
        String daysStr = scanner.nextLine().trim();
        int days = 7;
        try { days = Integer.parseInt(daysStr); } catch (NumberFormatException ignored) {}

        try {
            Invitation inv = service.createInvitation(admin.getId(), target, days);
            System.out.println("\n✔ تم إنشاء الدعوة بنجاح!");
            System.out.println("رمز الدعوة: " + inv.getToken());
            System.out.println("رابط التسجيل: http://localhost:8080/join.html?token=" + inv.getToken());
            System.out.println("تنتهي في: " + inv.getExpiresAt() + "\n");
        } catch (Exception e) {
            System.out.println("⛔ خطأ: " + e.getMessage() + "\n");
        }
    }

    private void updateMotherStatus() {
        System.out.print("أدخل رقم معرّف الأم (ID): ");
        String idStr = scanner.nextLine().trim();
        int motherId;
        try {
            motherId = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            System.out.println("⚠️ معرّف غير صحيح.\n");
            return;
        }

        System.out.println("اختر الحالة الجديدة:");
        System.out.println("  1. APPROVED  (اعتماد)");
        System.out.println("  2. REJECTED  (رفض)");
        System.out.println("  3. SUSPENDED (تعليق)");
        System.out.print("اختيارك: ");
        String choice = scanner.nextLine().trim();
        String newStatus = switch (choice) {
            case "1" -> "APPROVED";
            case "2" -> "REJECTED";
            case "3" -> "SUSPENDED";
            default -> null;
        };
        if (newStatus == null) {
            System.out.println("⚠️ اختيار غير صحيح.\n");
            return;
        }
        try {
            service.setMotherApproval(motherId, newStatus);
            System.out.println("✔ تم تحديث حالة الأم إلى: " + newStatus + "\n");
        } catch (Exception e) {
            System.out.println("⛔ خطأ: " + e.getMessage() + "\n");
        }
    }
}
