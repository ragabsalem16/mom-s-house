package com.beitmama.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import com.beitmama.model.User;
import com.beitmama.service.BeitMamaService;

public class LoginFrame extends JFrame {
    private final BeitMamaService service;
    
    private final boolean exitOnClose;
    private JTextField emailField;
    private JPasswordField passwordField;

    public LoginFrame(BeitMamaService service) {
        this(service, true);
    }

    public LoginFrame(BeitMamaService service, boolean exitOnClose) {
        this.service = service;
        this.exitOnClose = exitOnClose;

        setTitle("بيت ماما - تسجيل الدخول إلى لوحة التحكم");
        setSize(420, 320);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(exitOnClose ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

        initComponents();
    }

    private void initComponents() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));

        
        JLabel titleLabel = new JLabel("تسجيل الدخول - لوحة الإدارة", JLabel.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        titleLabel.setForeground(new Color(245, 130, 32));
        panel.add(titleLabel, BorderLayout.NORTH);

        
        JPanel fieldsPanel = new JPanel(new GridLayout(4, 1, 8, 8));
        fieldsPanel.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

        JLabel lblEmail = new JLabel("البريد الإلكتروني:");
        emailField = new JTextField("admin@beitmama.com");

        JLabel lblPass = new JLabel("كلمة المرور:");
        passwordField = new JPasswordField("admin123");

        fieldsPanel.add(lblEmail);
        fieldsPanel.add(emailField);
        fieldsPanel.add(lblPass);
        fieldsPanel.add(passwordField);

        panel.add(fieldsPanel, BorderLayout.CENTER);

        
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton btnLogin = new JButton("تسجيل الدخول");
        btnLogin.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnLogin.setBackground(new Color(245, 130, 32));
        btnLogin.setForeground(Color.WHITE);
        btnLogin.setPreferredSize(new Dimension(200, 38));

        btnLogin.addActionListener(e -> performLogin());

        btnPanel.add(btnLogin);
        panel.add(btnPanel, BorderLayout.SOUTH);

        setContentPane(panel);
    }

    private void performLogin() {
        String email = emailField.getText().trim();
        String pass = new String(passwordField.getPassword());

        try {
            User user = service.login(email, pass);
            if (user == null) {
                JOptionPane.showMessageDialog(this, "بيانات الدخول غير صحيحة", "خطأ", JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (!"ADMIN".equals(user.getRole())) {
                JOptionPane.showMessageDialog(this, "هذه اللوحة مخصصة لإدارة النظام فقط.", "تنبيه", JOptionPane.WARNING_MESSAGE);
                return;
            }

            dispose();
            SwingUtilities.invokeLater(() -> {
                AdminDashboardFrame dashboard = new AdminDashboardFrame(service, user);
                dashboard.setVisible(true);
            });

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "خطأ", JOptionPane.ERROR_MESSAGE);
        }
    }
}
