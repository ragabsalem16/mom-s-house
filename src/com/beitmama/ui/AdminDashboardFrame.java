package com.beitmama.ui;

import com.beitmama.model.*;
import com.beitmama.service.BeitMamaService;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;

public class AdminDashboardFrame extends JFrame {
    private final BeitMamaService service;
    private final User currentAdmin;

    private JTabbedPane tabbedPane;
    private DefaultTableModel mothersTableModel;
    private DefaultTableModel invitationsTableModel;
    private DefaultTableModel ordersTableModel;
    private DefaultTableModel customersTableModel;
    private JTable mothersTable;
    private JTable ordersTable;
    private JTable customersTable;

    public AdminDashboardFrame(BeitMamaService service, User admin) {
        this.service = service;
        this.currentAdmin = admin;

        setTitle("بيت ماما - لوحة تحكم الإدارة العليا (Admin Dashboard)");
        setSize(1100, 700);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // ضبط الواجهة RTL للعربية
        applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

        initComponents();
        loadAllData();
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(245, 130, 32));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));

        JLabel titleLabel = new JLabel("نظام بيت ماما - لوحة تحكم الإدارة", JLabel.RIGHT);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        titleLabel.setForeground(Color.WHITE);

        JLabel welcomeLabel = new JLabel("مرحباً بك: " + currentAdmin.getName() + " | الدور: مدير نظام", JLabel.LEFT);
        welcomeLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        welcomeLabel.setForeground(Color.WHITE);

        headerPanel.add(titleLabel, BorderLayout.CENTER);
        headerPanel.add(welcomeLabel, BorderLayout.WEST);

        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Tabbed Pane
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("SansSerif", Font.BOLD, 14));
        tabbedPane.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

        tabbedPane.addTab("دعوات الأمهات", createInvitationsPanel());
        tabbedPane.addTab("مراجعة واعتماد الأمهات", createMothersPanel());
        tabbedPane.addTab("إدارة الطلبات", createOrdersPanel());
        tabbedPane.addTab("العملاء والمستخدمين", createCustomersPanel());

        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        setContentPane(mainPanel);
    }

    // 1. لوحة الدعوات
    private JPanel createInvitationsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // شريط الأزرار
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnCreateInv = new JButton("➕ إنشاء دعوة جديدة لأم");
        btnCreateInv.setBackground(new Color(40, 167, 69));
        btnCreateInv.setForeground(Color.WHITE);
        btnCreateInv.setFont(new Font("SansSerif", Font.BOLD, 13));
        btnCreateInv.addActionListener(this::handleCreateInvitation);

        JButton btnRevoke = new JButton("🚫 إلغاء الدعوة المحددة");
        btnRevoke.setBackground(new Color(220, 53, 69));
        btnRevoke.setForeground(Color.WHITE);
        btnRevoke.setFont(new Font("SansSerif", Font.BOLD, 13));
        btnRevoke.addActionListener(this::handleRevokeInvitation);

        JButton btnRefresh = new JButton("🔄 تحديث");
        btnRefresh.addActionListener(e -> loadInvitations());

        topBar.add(btnCreateInv);
        topBar.add(btnRevoke);
        topBar.add(btnRefresh);

        panel.add(topBar, BorderLayout.NORTH);

        // جدول الدعوات
        String[] cols = {"المعرف", "البريد / الهاتف", "تاريخ الانتهاء", "تاريخ الاستخدام", "المستخدمة بواسطة", "الحالة"};
        invitationsTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable table = new JTable(invitationsTableModel);
        formatTable(table);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        return panel;
    }

    // 2. لوحة الأمهات
    private JPanel createMothersPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton btnApprove = new JButton("✅ اعتماد الأم والموافقة");
        btnApprove.setBackground(new Color(40, 167, 69));
        btnApprove.setForeground(Color.WHITE);
        btnApprove.setFont(new Font("SansSerif", Font.BOLD, 13));
        btnApprove.addActionListener(e -> handleMotherStatus("APPROVED"));

        JButton btnReject = new JButton("❌ رفض الحساب");
        btnReject.setBackground(new Color(220, 53, 69));
        btnReject.setForeground(Color.WHITE);
        btnReject.setFont(new Font("SansSerif", Font.BOLD, 13));
        btnReject.addActionListener(e -> handleMotherStatus("REJECTED"));

        JButton btnSuspend = new JButton("⚠️ تعليق الحساب مؤقتاً");
        btnSuspend.setBackground(new Color(255, 193, 7));
        btnSuspend.setFont(new Font("SansSerif", Font.BOLD, 13));
        btnSuspend.addActionListener(e -> handleMotherStatus("SUSPENDED"));

        JButton btnRefresh = new JButton("🔄 تحديث");
        btnRefresh.addActionListener(e -> loadMothers());

        actions.add(btnApprove);
        actions.add(btnReject);
        actions.add(btnSuspend);
        actions.add(btnRefresh);

        JTextField phoneSearch = new JTextField(18);
        phoneSearch.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        phoneSearch.setToolTipText("بحث برقم هاتف الأم");
        JPanel search = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        search.add(new JLabel("بحث برقم الهاتف:"));
        search.add(phoneSearch);
        topBar.add(actions, BorderLayout.EAST);
        topBar.add(search, BorderLayout.WEST);
        panel.add(topBar, BorderLayout.NORTH);

        String[] cols = {"المعرف", "الاسم", "البريد الإلكتروني", "الهاتف", "حالة الحساب", "حالة الاعتماد", "عنوان الاستلام"};
        mothersTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        mothersTable = new JTable(mothersTableModel);
        formatTable(mothersTable);
        addPhoneFilter(phoneSearch, mothersTable, 3);
        panel.add(new JScrollPane(mothersTable), BorderLayout.CENTER);

        return panel;
    }

    // 3. لوحة الطلبات
    private JPanel createOrdersPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        JButton btnRefresh = new JButton("🔄 تحديث الطلبات");
        btnRefresh.addActionListener(e -> loadOrders());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.add(btnRefresh);
        JTextField phoneSearch = new JTextField(22);
        phoneSearch.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        phoneSearch.setToolTipText("اكتب رقم هاتف العميل أو جزءاً منه لتصفية الطلبات");
        JPanel search = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        search.add(new JLabel("بحث في الطلبات برقم الهاتف:"));
        search.add(phoneSearch);
        topBar.add(actions, BorderLayout.EAST);
        topBar.add(search, BorderLayout.WEST);

        panel.add(topBar, BorderLayout.NORTH);

        String[] cols = {"رقم الطلب", "العميل", "هاتف العميل", "الأم المنتجة", "الإجمالي", "موعد الاستلام", "الحالة"};
        ordersTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        ordersTable = new JTable(ordersTableModel);
        formatTable(ordersTable);
        addPhoneFilter(phoneSearch, ordersTable, 2);
        panel.add(new JScrollPane(ordersTable), BorderLayout.CENTER);

        return panel;
    }

    // 4. لوحة العملاء
    private JPanel createCustomersPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        JButton btnRefresh = new JButton("🔄 تحديث العملاء");
        btnRefresh.addActionListener(e -> loadCustomers());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.add(btnRefresh);
        JTextField phoneSearch = new JTextField(22);
        phoneSearch.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        phoneSearch.setToolTipText("اكتب رقم الهاتف أو جزءاً منه لتصفية العملاء");
        JPanel search = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        search.add(new JLabel("بحث عن عميل برقم الهاتف:"));
        search.add(phoneSearch);
        topBar.add(actions, BorderLayout.EAST);
        topBar.add(search, BorderLayout.WEST);

        panel.add(topBar, BorderLayout.NORTH);

        String[] cols = {"المعرف", "الاسم", "البريد الإلكتروني", "رقم الهاتف", "الحالة", "تاريخ الانضمام"};
        customersTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        customersTable = new JTable(customersTableModel);
        formatTable(customersTable);
        addPhoneFilter(phoneSearch, customersTable, 3);
        panel.add(new JScrollPane(customersTable), BorderLayout.CENTER);

        return panel;
    }

    private void addPhoneFilter(JTextField searchField, JTable table, int phoneColumn) {
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>((DefaultTableModel) table.getModel());
        table.setRowSorter(sorter);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            private void updateFilter() {
                String query = normalizePhone(searchField.getText());
                if (query.isEmpty()) {
                    sorter.setRowFilter(null);
                    return;
                }
                sorter.setRowFilter(new RowFilter<>() {
                    @Override
                    public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                        return normalizePhone(String.valueOf(entry.getValue(phoneColumn))).contains(query);
                    }
                });
            }

            @Override
            public void insertUpdate(DocumentEvent event) { updateFilter(); }

            @Override
            public void removeUpdate(DocumentEvent event) { updateFilter(); }

            @Override
            public void changedUpdate(DocumentEvent event) { updateFilter(); }
        });
    }

    private String normalizePhone(String value) {
        StringBuilder normalized = new StringBuilder();
        for (char digit : value.toCharArray()) {
            if (digit >= '٠' && digit <= '٩') normalized.append((char) ('0' + digit - '٠'));
            else if (digit >= '۰' && digit <= '۹') normalized.append((char) ('0' + digit - '۰'));
            else if (digit >= '0' && digit <= '9') normalized.append(digit);
        }
        return normalized.toString();
    }

    private void formatTable(JTable table) {
        table.setRowHeight(30);
        table.setFont(new Font("SansSerif", Font.PLAIN, 13));
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(JLabel.RIGHT);
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(rightRenderer);
        }
    }

    private void loadAllData() {
        loadInvitations();
        loadMothers();
        loadOrders();
        loadCustomers();
    }

    private void loadInvitations() {
        invitationsTableModel.setRowCount(0);
        List<Invitation> list = service.getAllInvitations();
        for (Invitation inv : list) {
            String status = "صالحة";
            if (inv.getRevokedAt() != null) status = "ملغاة";
            else if (inv.getUsedAt() != null) status = "مستخدمة";
            else if (!inv.isValid()) status = "منتهية";

            invitationsTableModel.addRow(new Object[]{
                    inv.getId(),
                    inv.getEmailOrPhone(),
                    inv.getExpiresAt(),
                    inv.getUsedAt() != null ? inv.getUsedAt() : "لم تُستخدم",
                    inv.getUsedBy() != null ? inv.getUsedBy() : "-",
                    status
            });
        }
    }

    private void loadMothers() {
        mothersTableModel.setRowCount(0);
        List<User> mothers = service.getAllMothers();
        for (User m : mothers) {
            SellerProfile profile = service.getSellerProfile(m.getId());
            mothersTableModel.addRow(new Object[]{
                    m.getId(),
                    m.getName(),
                    m.getEmail(),
                    m.getPhone(),
                    m.getStatus(),
                    profile != null ? profile.getApprovalStatus() : "N/A",
                    profile != null ? profile.getPickupAddress() : "N/A"
            });
        }
    }

    private void loadOrders() {
        ordersTableModel.setRowCount(0);
        List<Order> orders = service.getAllOrders();
        for (Order o : orders) {
            ordersTableModel.addRow(new Object[]{
                    "#" + o.getId(),
                    o.getCustomerName(),
                    o.getCustomerPhone(),
                    o.getSellerName(),
                    o.getTotalAmount() + " ج.م",
                    o.getPickupTime(),
                    o.getStatus()
            });
        }
    }

    private void loadCustomers() {
        customersTableModel.setRowCount(0);
        List<User> customers = service.getAllCustomers();
        for (User c : customers) {
            customersTableModel.addRow(new Object[]{
                    c.getId(),
                    c.getName(),
                    c.getEmail(),
                    c.getPhone(),
                    c.getStatus(),
                    c.getCreatedAt()
            });
        }
    }

    private void handleCreateInvitation(ActionEvent e) {
        JTextField fieldTarget = new JTextField();
        JSpinner spinnerDays = new JSpinner(new SpinnerNumberModel(7, 1, 90, 1));

        JPanel form = new JPanel(new GridLayout(2, 2, 8, 8));
        form.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        form.add(new JLabel("البريد الإلكتروني أو الهاتف للأم:"));
        form.add(fieldTarget);
        form.add(new JLabel("مدة الصلاحية بالأيام:"));
        form.add(spinnerDays);

        int result = JOptionPane.showConfirmDialog(this, form, "إنشاء دعوة أم جديدة", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            String target = fieldTarget.getText().trim();
            if (target.isEmpty()) {
                JOptionPane.showMessageDialog(this, "يرجى كتابة البريد أو الهاتف");
                return;
            }
            try {
                int days = (int) spinnerDays.getValue();
                Invitation inv = service.createInvitation(currentAdmin.getId(), target, days);
                int port = Integer.getInteger("beitmama.port", 8080);
                String link = "http://localhost:" + port + "/join.html?token=" + inv.getToken();

                JTextArea area = new JTextArea("تم إنشاء الدعوة بنجاح!\n\nرمز الدعوة:\n" + inv.getToken() + "\n\nرابط التسجيل للأم:\n" + link);
                area.setEditable(false);
                area.setLineWrap(true);
                area.setFont(new Font("Monospaced", Font.PLAIN, 12));
                JScrollPane sp = new JScrollPane(area);
                sp.setPreferredSize(new Dimension(500, 150));

                JOptionPane.showMessageDialog(this, sp, "تفاصيل الدعوة", JOptionPane.INFORMATION_MESSAGE);
                loadInvitations();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "خطأ: " + ex.getMessage(), "خطأ", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void handleRevokeInvitation(ActionEvent e) {
        int selected = getSelectedRow(invitationsTableModel);
        if (selected == -1) {
            JOptionPane.showMessageDialog(this, "يرجى تحديد دعوة أولاً");
            return;
        }
        int id = (int) invitationsTableModel.getValueAt(selected, 0);
        int confirm = JOptionPane.showConfirmDialog(this, "هل أنت متأكد من إلغاء الدعوة رقم #" + id + "؟", "تأكيد الإلغاء", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            if (service.revokeInvitation(id)) {
                JOptionPane.showMessageDialog(this, "تم إلغاء الدعوة بنجاح.");
                loadInvitations();
            } else {
                JOptionPane.showMessageDialog(this, "فشل الإلغاء (قد تكون مستخدمة أو ملغاة مسبقاً).", "خطأ", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void handleMotherStatus(String newStatus) {
        int selected = getSelectedRow(mothersTableModel);
        if (selected == -1) {
            JOptionPane.showMessageDialog(this, "يرجى تحديد أم من الجدول أولاً");
            return;
        }
        int motherId = (int) mothersTableModel.getValueAt(selected, 0);
        try {
            service.setMotherApproval(motherId, newStatus);
            JOptionPane.showMessageDialog(this, "تم تحديث حالة الأم إلى: " + newStatus);
            loadMothers();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "خطأ في التحديث: " + ex.getMessage());
        }
    }

    private int getSelectedRow(DefaultTableModel model) {
        // Find focused table
        Component comp = tabbedPane.getSelectedComponent();
        if (comp instanceof JPanel) {
            for (Component c : ((JPanel) comp).getComponents()) {
                if (c instanceof JScrollPane) {
                    JViewport vp = ((JScrollPane) c).getViewport();
                    if (vp.getView() instanceof JTable) {
                        JTable table = (JTable) vp.getView();
                        int selectedViewRow = table.getSelectedRow();
                        return selectedViewRow < 0 ? -1 : table.convertRowIndexToModel(selectedViewRow);
                    }
                }
            }
        }
        return -1;
    }
}
