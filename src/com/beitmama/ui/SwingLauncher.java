package com.beitmama.ui;

import java.awt.GraphicsEnvironment;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.beitmama.dao.DatabaseManager;
import com.beitmama.service.BeitMamaService;

















public class SwingLauncher {

    public static void main(String[] args) {
        
        DatabaseManager.initializeDatabase();

        
        BeitMamaService service = new BeitMamaService();

        
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }

            if (GraphicsEnvironment.isHeadless()) {
                System.err
                        .println("⛔ البيئة لا تدعم الواجهات الرسومية (Headless). استخدم الواجهة النصية بدلاً من ذلك.");
                System.exit(1);
                return;
            }

            LoginFrame login = new LoginFrame(service, false);
            login.setVisible(true);
        });
    }
}
