package com.beitmama.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import com.beitmama.model.Invitation;
import com.beitmama.model.Order;
import com.beitmama.model.SellerProfile;
import com.beitmama.model.User;
import com.beitmama.service.BeitMamaService;

public class AdminDashboardFrame extends JFrame {
    private static final Color INK = new Color(15, 23, 42); 
    private static final Color BLACK = new Color(15, 23, 42);
    private static final Color SIDEBAR_BG = new Color(13, 40, 24); 
    private static final Color PRIMARY = new Color(21, 128, 61); 
    private static final Color PRIMARY_GREEN = new Color(21, 128, 61); 
    private static final Color PRIMARY_LIGHT = new Color(240, 253, 244); 
    private static final Color BLUE = new Color(37, 99, 235); 
    private static final Color BLUE_DARK = new Color(29, 78, 216); 
    private static final Color BLUE_LIGHT = new Color(219, 234, 254); 
    private static final Color BLUE_PALE = new Color(239, 246, 255); 
    private static final Color BACKGROUND = new Color(248, 250, 252); 
    private static final Color CARD_BG = Color.WHITE;
    private static final Color MUTED = new Color(100, 116, 139); 
    private static final Color BORDER = new Color(226, 232, 240); 
    private static final Font FONT_BOLD_LG = new Font("Segoe UI", Font.BOLD, 22);
    private static final Font FONT_BOLD_MD = new Font("Segoe UI", Font.BOLD, 15);
    private static final Font FONT_REGULAR = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FONT_MUTED_SM = new Font("Segoe UI", Font.PLAIN, 12);

    private final BeitMamaService service;
    private final User currentAdmin;

    private final CardLayout contentLayout = new CardLayout();
    private JPanel contentCards;
    private JPanel sidebar;
    private JLabel pageTitle;
    private JLabel pageSubtitle;
    private JLabel ordersRevenueLabel;
    private JLabel platformProfitLabel;
    private JLabel statPendingOrders;
    private JLabel statTotalUsers;
    private JLabel statActiveMothers;
    private JLabel pillNewOrders;
    private JLabel pillDeliveringOrders;
    private JLabel pillProcessingOrders;
    private JLabel pillRejectedOrders;
    private DefaultTableModel mothersTableModel;
    private DefaultTableModel invitationsTableModel;
    private DefaultTableModel ordersTableModel;
    private DefaultTableModel customersTableModel;
    private DefaultTableModel recentOrdersTableModel;
    private JTable invitationsTable;
    private JTable mothersTable;
    private JTable ordersTable;
    private JTable customersTable;
    private JTable recentOrdersTable;

    public AdminDashboardFrame(BeitMamaService service, User admin) {
        this.service = service;
        this.currentAdmin = admin;

        setTitle("بيت ماما - لوحة تحكم الإدارة العليا (Admin Dashboard)");
        setSize(1280, 820);
        setMinimumSize(new Dimension(1100, 700));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

        initComponents();
        loadAllData();
    }

    private void initComponents() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BACKGROUND);

        JPanel header = new JPanel(new BorderLayout(18, 0));
        header.setBackground(Color.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 3, 0, BLUE),
                BorderFactory.createEmptyBorder(16, 28, 16, 28)));

        JPanel brand = new JPanel(new GridLayout(2, 1, 0, 2));
        brand.setOpaque(false);
        JLabel brandLabel = new JLabel("بيت ماما  |  إدارة المنصة");
        brandLabel.setFont(new Font("SansSerif", Font.BOLD, 21));
        brandLabel.setForeground(INK);
        JLabel brandSubtitle = new JLabel("لوحة تحكم الإدارة");
        brandSubtitle.setFont(new Font("SansSerif", Font.PLAIN, 12));
        brandSubtitle.setForeground(MUTED);
        brand.add(brandLabel);
        brand.add(brandSubtitle);

        JLabel welcome = new JLabel("مرحباً، " + currentAdmin.getName() + "   ● متصل الآن");
        welcome.setFont(new Font("SansSerif", Font.BOLD, 13));
        welcome.setForeground(BLUE_DARK);
        header.add(brand, BorderLayout.LINE_START);
        header.add(welcome, BorderLayout.LINE_END);
        root.add(header, BorderLayout.NORTH);

        contentCards = new JPanel(contentLayout);
        contentCards.setBackground(BACKGROUND);
        contentCards.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        contentCards.add(createOverviewPanel(), "overview");
        contentCards.add(createInvitationsPanel(), "invitations");
        contentCards.add(createMothersPanel(), "mothers");
        contentCards.add(createOrdersPanel(), "orders");
        contentCards.add(createCustomersPanel(), "customers");

        JPanel workspace = new JPanel(new BorderLayout());
        workspace.setBackground(BACKGROUND);
        JPanel pageHeader = new JPanel(new GridLayout(2, 1, 0, 4));
        pageHeader.setOpaque(false);
        pageTitle = new JLabel("الرئيسية");
        pageTitle.setFont(new Font("SansSerif", Font.BOLD, 25));
        pageTitle.setForeground(INK);
        pageSubtitle = new JLabel("نظرة عامة على نشاط بيت ماما");
        pageSubtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        pageSubtitle.setForeground(MUTED);
        pageHeader.setBorder(BorderFactory.createEmptyBorder(22, 28, 0, 28));
        pageHeader.add(pageTitle);
        pageHeader.add(pageSubtitle);
        workspace.add(pageHeader, BorderLayout.NORTH);
        workspace.add(contentCards, BorderLayout.CENTER);

        root.add(createSidebar(), BorderLayout.LINE_START);
        root.add(workspace, BorderLayout.CENTER);
        root.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        setContentPane(root);
    }

    private JPanel createSidebar() {
        sidebar = new JPanel();
        sidebar.setBackground(SIDEBAR_BG); 
        sidebar.setPreferredSize(new Dimension(260, 0));
        sidebar.setBorder(BorderFactory.createEmptyBorder(20, 14, 20, 14));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

        
        JPanel logoWrap = new JPanel(new FlowLayout(FlowLayout.CENTER));
        logoWrap.setOpaque(false);
        JLabel logoCircle = new JLabel("🏠", SwingConstants.CENTER);
        logoCircle.setOpaque(true);
        logoCircle.setBackground(Color.WHITE);
        logoCircle.setForeground(new Color(225, 29, 72));
        logoCircle.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 28));
        logoCircle.setPreferredSize(new Dimension(54, 54));
        logoCircle.setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 80), 2));
        logoWrap.add(logoCircle);
        sidebar.add(logoWrap);

        JLabel brandSide = new JLabel("بيت ماما", SwingConstants.CENTER);
        brandSide.setForeground(Color.WHITE);
        brandSide.setFont(new Font("Segoe UI", Font.BOLD, 22));
        brandSide.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebar.add(brandSide);

        JLabel brandSideSub = new JLabel("أكل بيتي من قلب ماما", SwingConstants.CENTER);
        brandSideSub.setForeground(new Color(167, 243, 208)); 
        brandSideSub.setFont(new Font("Segoe UI", Font.BOLD, 12));
        brandSideSub.setAlignmentX(Component.CENTER_ALIGNMENT);
        brandSideSub.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));
        sidebar.add(brandSideSub);

        sidebar.add(new JSeparator(SwingConstants.HORIZONTAL));
        sidebar.add(Box.createVerticalStrut(14));

        addNavigationButton(sidebar, "⌂   الرئيسية", "overview", "ملخص سريع لبيانات النظام، المستخدمين، الطلبات، والتقارير");
        addNavigationButton(sidebar, "♡   الأمهات", "mothers", "مراجعة حسابات الأمهات واعتمادهن");
        addNavigationButton(sidebar, "▣   الطلبات", "orders", "متابعة مسار الطلبات والمبيعات");
        addNavigationButton(sidebar, "♙   العملاء", "customers", "سجل العملاء المسجلين");
        addNavigationButton(sidebar, "✉   الدعوات", "invitations", "إنشاء دعوات انضمام الأمهات ومتابعتها");

        sidebar.add(Box.createVerticalGlue());

        
        JPanel footerCard = new JPanel(new GridLayout(2, 1, 0, 2));
        footerCard.setBackground(new Color(255, 255, 255, 15));
        footerCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 255, 255, 25), 1),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        JLabel footerTitle = new JLabel("🌱 بيت ماما", SwingConstants.CENTER);
        footerTitle.setForeground(new Color(134, 239, 172));
        footerTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        JLabel footerSub = new JLabel("معاً لدعم الأمهات المصرية ♡", SwingConstants.CENTER);
        footerSub.setForeground(new Color(148, 163, 184));
        footerSub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        footerCard.add(footerTitle);
        footerCard.add(footerSub);
        footerCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));
        sidebar.add(footerCard);

        return sidebar;
    }

    private void addNavigationButton(JPanel sidebar, String text, String page, String subtitle) {
        JButton button = new JButton(text);
        button.setHorizontalAlignment(SwingConstants.RIGHT);
        button.setFont(new Font("SansSerif", Font.BOLD, 14));
        button.setForeground(Color.WHITE);
        button.setBackground("overview".equals(page) ? BLUE : BLACK);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setName(page);
        button.setAlignmentX(Component.RIGHT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        button.addActionListener(event -> showPage(page, text.replaceAll("^[^ ]+\\s+", ""), subtitle));
        sidebar.add(button);
        sidebar.add(Box.createVerticalStrut(7));
    }

    private JPanel createOverviewPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 18));
        panel.setOpaque(false);

        
        JPanel bannerRow = new JPanel(new BorderLayout(20, 0));
        bannerRow.setOpaque(false);

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 0, 4));
        titleBlock.setOpaque(false);
        JLabel pageHeading = new JLabel("▦  لوحة المتابعة العامة");
        pageHeading.setFont(new Font("Segoe UI", Font.BOLD, 22));
        pageHeading.setForeground(INK);
        JLabel pageSubHeading = new JLabel("ملخص سريع لبيانات النظام، المستخدمين، الطلبات، والتقارير.");
        pageSubHeading.setFont(FONT_MUTED_SM);
        pageSubHeading.setForeground(MUTED);
        titleBlock.add(pageHeading);
        titleBlock.add(pageSubHeading);

        
        JPanel promoCard = new JPanel(new BorderLayout(14, 0));
        promoCard.setBackground(new Color(255, 247, 237)); 
        promoCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(254, 215, 170), 1),
                BorderFactory.createEmptyBorder(12, 18, 12, 18)));
        JLabel promoText = new JLabel("<html><b>مع كل وجبة في بيت ماما ..</b><br><span style='color:#15803d'>أنت بتدعم أسرة منتجة ♡</span></html>");
        promoText.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        promoText.setForeground(new Color(154, 52, 18));
        JLabel promoIcon = new JLabel("🥗", SwingConstants.CENTER);
        promoIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 28));
        promoCard.add(promoIcon, BorderLayout.LINE_START);
        promoCard.add(promoText, BorderLayout.CENTER);

        bannerRow.add(titleBlock, BorderLayout.CENTER);
        bannerRow.add(promoCard, BorderLayout.LINE_START);

        
        JPanel cards = new JPanel(new GridLayout(1, 4, 14, 0));
        cards.setOpaque(false);
        ordersRevenueLabel = addStatCard(cards, "إجمالي المبيعات", "0.00 ج.م", "💰", new Color(124, 58, 237), "(منذ بداية الشهر)", new Color(245, 243, 255));
        statPendingOrders = addStatCard(cards, "الطلبات المعلقة", "0", "🛍", new Color(234, 88, 12), "تحتاج إلى مراجعة", new Color(255, 247, 237));
        statTotalUsers = addStatCard(cards, "الإجمالي المسجلين", "0", "👥", new Color(22, 163, 74), "أمهات وعملاء", new Color(240, 253, 244));
        statActiveMothers = addStatCard(cards, "المطاعم/الأمهات النشطة", "0", "🍳", new Color(2, 132, 199), "حالياً", new Color(240, 249, 255));

        
        JPanel lower = new JPanel(new GridLayout(1, 2, 16, 0));
        lower.setOpaque(false);
        lower.add(createActivitiesPanel());
        lower.add(createRightStackPanel());

        JPanel overviewContent = new JPanel();
        overviewContent.setOpaque(false);
        overviewContent.setLayout(new BoxLayout(overviewContent, BoxLayout.Y_AXIS));
        overviewContent.add(bannerRow);
        overviewContent.add(Box.createVerticalStrut(18));
        overviewContent.add(cards);
        overviewContent.add(Box.createVerticalStrut(18));
        overviewContent.add(lower);

        panel.add(overviewContent, BorderLayout.CENTER);
        return panel;
    }

    private JLabel addStatCard(JPanel parent, String title, String value, String icon, Color accent, String subtext, Color bgCard) {
        JPanel card = new JPanel(new BorderLayout(14, 0));
        card.setBackground(bgCard != null ? bgCard : CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 60), 1),
                BorderFactory.createEmptyBorder(16, 18, 16, 18)));

        JLabel iconLabel = new JLabel(icon, SwingConstants.CENTER);
        iconLabel.setOpaque(true);
        iconLabel.setBackground(Color.WHITE);
        iconLabel.setForeground(accent);
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 22));
        iconLabel.setPreferredSize(new Dimension(46, 46));
        iconLabel.setBorder(BorderFactory.createLineBorder(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 40), 1));

        JPanel text = new JPanel(new GridLayout(3, 1, 0, 2));
        text.setOpaque(false);
        JLabel number = new JLabel(value);
        number.setFont(new Font("Segoe UI", Font.BOLD, 22));
        number.setForeground(INK);
        JLabel label = new JLabel(title);
        label.setFont(new Font("Segoe UI", Font.BOLD, 12));
        label.setForeground(new Color(51, 65, 85));
        JLabel note = new JLabel(subtext);
        note.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        note.setForeground(MUTED);

        text.add(number);
        text.add(label);
        text.add(note);

        card.add(iconLabel, BorderLayout.LINE_START);
        card.add(text, BorderLayout.CENTER);
        parent.add(card);
        return number;
    }

    private JPanel createActivitiesPanel() {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                BorderFactory.createEmptyBorder(18, 20, 18, 20)));

        JLabel title = new JLabel("⏰  أحدث الأنشطة");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(INK);
        card.add(title, BorderLayout.NORTH);

        JPanel list = new JPanel(new GridLayout(5, 1, 0, 10));
        list.setOpaque(false);
        list.add(createActivityRow("🛍", "تم تسجيل طلب جديد من العميل أحمد محمد", "اليوم 16:42", new Color(22, 163, 74)));
        list.add(createActivityRow("✓", "تم قبول طلب من الأم أم سليم", "اليوم 15:27", new Color(2, 132, 199)));
        list.add(createActivityRow("🍲", "تم إضافة وجبة جديدة من أم حسن", "اليوم 14:10", new Color(217, 119, 6)));
        list.add(createActivityRow("★", "قام عميل بتقييم الأم (5 نجوم)", "اليوم 12:36", new Color(124, 58, 237)));
        list.add(createActivityRow("✉", "تم إنشاء دعوة جديدة لأم جديدة", "أمس 20:15", new Color(225, 29, 72)));
        card.add(list, BorderLayout.CENTER);

        JButton viewAll = new JButton("عرض جميع الأنشطة  ←");
        viewAll.setFont(new Font("Segoe UI", Font.BOLD, 12));
        viewAll.setBackground(new Color(240, 253, 244));
        viewAll.setForeground(PRIMARY_GREEN);
        viewAll.setFocusPainted(false);
        viewAll.setOpaque(true);
        viewAll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(187, 247, 208), 1),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)));
        viewAll.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        viewAll.addActionListener(e -> showPage("orders", "الطلبات", "متابعة سجل الأنشطة والطلبات"));
        card.add(viewAll, BorderLayout.SOUTH);

        return card;
    }

    private JPanel createActivityRow(String icon, String text, String time, Color iconColor) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        JLabel iconLbl = new JLabel(icon, SwingConstants.CENTER);
        iconLbl.setOpaque(true);
        iconLbl.setBackground(new Color(iconColor.getRed(), iconColor.getGreen(), iconColor.getBlue(), 20));
        iconLbl.setForeground(iconColor);
        iconLbl.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 15));
        iconLbl.setPreferredSize(new Dimension(32, 32));

        JLabel textLbl = new JLabel(text);
        textLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        textLbl.setForeground(new Color(51, 65, 85));

        JLabel timeLbl = new JLabel(time);
        timeLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        timeLbl.setForeground(MUTED);

        row.add(iconLbl, BorderLayout.LINE_START);
        row.add(textLbl, BorderLayout.CENTER);
        row.add(timeLbl, BorderLayout.LINE_END);
        return row;
    }

    private JPanel createRightStackPanel() {
        JPanel stack = new JPanel(new GridLayout(2, 1, 0, 16));
        stack.setOpaque(false);

        
        JPanel chefCard = new JPanel();
        chefCard.setBackground(new Color(13, 56, 34)); 
        chefCard.setLayout(new BoxLayout(chefCard, BoxLayout.Y_AXIS));
        chefCard.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));

        JLabel hatLabel = new JLabel("👨‍🍳", SwingConstants.CENTER);
        hatLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 32));
        hatLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        chefCard.add(hatLabel);
        chefCard.add(Box.createVerticalStrut(8));

        JLabel chefTitle = new JLabel("مرحباً بك في لوحة إدارة بيت ماما", SwingConstants.CENTER);
        chefTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        chefTitle.setForeground(Color.WHITE);
        chefTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        chefCard.add(chefTitle);
        chefCard.add(Box.createVerticalStrut(6));

        JLabel chefSub = new JLabel("<html><center>من هنا يمكنك متابعة جميع الطلبات، إدارة الأمهات،<br>مراجعة المستخدمين، والحصول على تقارير مفصلة.</center></html>", SwingConstants.CENTER);
        chefSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        chefSub.setForeground(new Color(209, 250, 229));
        chefSub.setAlignmentX(Component.CENTER_ALIGNMENT);
        chefCard.add(chefSub);
        chefCard.add(Box.createVerticalStrut(14));

        JButton btnReview = new JButton("مراجعة الطلبات الآن");
        btnReview.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnReview.setBackground(new Color(167, 243, 208)); 
        btnReview.setForeground(new Color(6, 78, 59));
        btnReview.setOpaque(true);
        btnReview.setFocusPainted(false);
        btnReview.setBorder(BorderFactory.createEmptyBorder(9, 24, 9, 24));
        btnReview.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnReview.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnReview.addActionListener(e -> showPage("orders", "الطلبات", "متابعة طلبات العملاء وحالاتها"));
        chefCard.add(btnReview);

        
        JPanel statsPanel = new JPanel(new BorderLayout(0, 10));
        statsPanel.setBackground(CARD_BG);
        statsPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                BorderFactory.createEmptyBorder(16, 18, 16, 18)));

        JLabel statsHeading = new JLabel("📊  إحصائيات الطلبات");
        statsHeading.setFont(new Font("Segoe UI", Font.BOLD, 15));
        statsHeading.setForeground(INK);
        statsPanel.add(statsHeading, BorderLayout.NORTH);

        JPanel pillsGrid = new JPanel(new GridLayout(1, 4, 10, 0));
        pillsGrid.setOpaque(false);
        pillNewOrders = addPillCard(pillsGrid, "جديدة", "0", new Color(22, 101, 52), new Color(240, 253, 244));
        pillDeliveringOrders = addPillCard(pillsGrid, "تم التوصيل", "0", new Color(30, 64, 175), new Color(239, 246, 255));
        pillProcessingOrders = addPillCard(pillsGrid, "قيد المعالجة", "0", new Color(133, 77, 14), new Color(254, 252, 232));
        pillRejectedOrders = addPillCard(pillsGrid, "مرفوضة", "0", new Color(153, 27, 27), new Color(254, 242, 242));
        statsPanel.add(pillsGrid, BorderLayout.CENTER);

        stack.add(chefCard);
        stack.add(statsPanel);
        return stack;
    }

    private JLabel addPillCard(JPanel parent, String title, String value, Color textColor, Color bgColor) {
        JPanel pill = new JPanel(new GridLayout(2, 1, 0, 2));
        pill.setBackground(bgColor);
        pill.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(textColor.getRed(), textColor.getGreen(), textColor.getBlue(), 60), 1),
                BorderFactory.createEmptyBorder(10, 8, 10, 8)));

        JLabel numLbl = new JLabel(value, SwingConstants.CENTER);
        numLbl.setFont(new Font("Segoe UI", Font.BOLD, 18));
        numLbl.setForeground(textColor);

        JLabel textLbl = new JLabel(title, SwingConstants.CENTER);
        textLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        textLbl.setForeground(textColor);

        pill.add(numLbl);
        pill.add(textLbl);
        parent.add(pill);
        return numLbl;
    }

    private void addQuickAction(JPanel card, String text, String page, String title, String subtitle) {
        JButton action = new JButton(text);
        action.setHorizontalAlignment(SwingConstants.RIGHT);
        action.setFont(new Font("SansSerif", Font.BOLD, 13));
        action.setForeground(INK);
        action.setBackground(BLUE_PALE);
        action.setOpaque(true);
        action.setFocusPainted(false);
        action.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        action.setAlignmentX(Component.RIGHT_ALIGNMENT);
        action.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        action.addActionListener(event -> showPage(page, title, subtitle));
        card.add(action);
        card.add(Box.createVerticalStrut(9));
    }

    private void showPage(String page, String title, String subtitle) {
        contentLayout.show(contentCards, page);
        pageTitle.setText(title);
        pageSubtitle.setText(subtitle);
        for (Component component : sidebar.getComponents()) {
            if (component instanceof JButton button) {
                button.setBackground(page.equals(button.getName()) ? BLUE : BLACK);
            }
        }
    }

    private void updateOverviewStats() {
        int totalOrders = ordersTableModel.getRowCount();
        int mothersTotal = mothersTableModel.getRowCount();
        int customersTotal = customersTableModel.getRowCount();

        if (statTotalUsers != null) {
            statTotalUsers.setText(String.valueOf(mothersTotal + customersTotal));
        }
        if (statActiveMothers != null) {
            statActiveMothers.setText(String.valueOf(mothersTotal));
        }

        double totalRevenue = 0.0;
        int pending = 0;
        int completed = 0;
        int processing = 0;
        int rejected = 0;

        for (int row = 0; row < totalOrders; row++) {
            Object amountObj = ordersTableModel.getValueAt(row, 4);
            if (amountObj != null) {
                String valStr = String.valueOf(amountObj).replace("ج.م", "").trim();
                try {
                    totalRevenue += Double.parseDouble(valStr);
                } catch (NumberFormatException ignored) {}
            }
            Object statusObj = ordersTableModel.getValueAt(row, 6);
            String st = statusObj != null ? String.valueOf(statusObj).toLowerCase() : "";
            if (st.contains("pending") || st.contains("انتظار")) pending++;
            else if (st.contains("complete") || st.contains("مكتمل")) completed++;
            else if (st.contains("reject") || st.contains("مرفوض") || st.contains("cancel") || st.contains("ملغي")) rejected++;
            else processing++;
        }

        if (ordersRevenueLabel != null) {
            ordersRevenueLabel.setText(String.format("%.2f ج.م", totalRevenue));
        }
        if (statPendingOrders != null) {
            statPendingOrders.setText(String.valueOf(pending));
        }

        if (pillNewOrders != null) pillNewOrders.setText(String.valueOf(pending));
        if (pillDeliveringOrders != null) pillDeliveringOrders.setText(String.valueOf(completed));
        if (pillProcessingOrders != null) pillProcessingOrders.setText(String.valueOf(processing));
        if (pillRejectedOrders != null) pillRejectedOrders.setText(String.valueOf(rejected));
    }

    private void styleButton(JButton button, Color bg, Color fg) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setBackground(bg);
        button.setForeground(fg);
        button.setOpaque(true);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(bg.darker(), 1),
                BorderFactory.createEmptyBorder(9, 18, 9, 18)));
    }

    private JPanel createInvitationsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        topBar.setOpaque(false);
        JButton btnCreateInv = new JButton("إنشاء دعوة جديدة لأم");
        styleButton(btnCreateInv, BLUE, Color.WHITE);
        btnCreateInv.addActionListener(event -> handleCreateInvitation());

        JButton btnRevoke = new JButton("إلغاء الدعوة المحددة");
        styleButton(btnRevoke, INK, Color.WHITE);
        btnRevoke.addActionListener(event -> handleRevokeInvitation());

        JButton btnRefresh = new JButton("🔄 تحديث");
        styleButton(btnRefresh, BLUE_LIGHT, BLUE_DARK);
        btnRefresh.addActionListener(e -> loadInvitations());

        topBar.add(btnCreateInv);
        topBar.add(btnRevoke);
        topBar.add(btnRefresh);

        panel.add(topBar, BorderLayout.NORTH);

        
        String[] cols = {"المعرف", "البريد / الهاتف", "تاريخ الانتهاء", "تاريخ الاستخدام", "المستخدمة بواسطة", "الحالة"};
        invitationsTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        invitationsTable = new JTable(invitationsTableModel);
        formatTable(invitationsTable);
        panel.add(createTableScrollPane(invitationsTable), BorderLayout.CENTER);

        return panel;
    }

    
    private JPanel createMothersPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        topBar.setOpaque(false);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton btnApprove = new JButton("✅ اعتماد الأم والموافقة");
        styleButton(btnApprove, BLUE, Color.WHITE);
        btnApprove.addActionListener(e -> handleMotherStatus("APPROVED"));

        JButton btnReject = new JButton("❌ رفض الحساب");
        styleButton(btnReject, INK, Color.WHITE);
        btnReject.addActionListener(e -> handleMotherStatus("REJECTED"));

        JButton btnSuspend = new JButton("⚠️ تعليق الحساب مؤقتاً");
        styleButton(btnSuspend, BLUE_LIGHT, BLUE_DARK);
        btnSuspend.addActionListener(e -> handleMotherStatus("SUSPENDED"));

        JButton btnRefresh = new JButton("🔄 تحديث");
        styleButton(btnRefresh, BLUE_LIGHT, BLUE_DARK);
        btnRefresh.addActionListener(e -> loadMothers());

        actions.add(btnApprove);
        actions.add(btnReject);
        actions.add(btnSuspend);
        actions.add(btnRefresh);

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        searchPanel.setOpaque(false);
        JLabel searchLabel = new JLabel("بحث:");
        searchLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        searchLabel.setForeground(INK);
        JTextField searchField = new JTextField(25);
        searchField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        searchField.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        searchField.setToolTipText("ابحث برقم الهاتف أو اسم الأم");
        searchPanel.add(searchLabel);
        searchPanel.add(searchField);
        topBar.add(actions, BorderLayout.WEST);
        topBar.add(searchPanel, BorderLayout.EAST);
        panel.add(topBar, BorderLayout.NORTH);

        String[] cols = {"المعرف", "الاسم", "البريد الإلكتروني", "الهاتف", "حالة الحساب", "حالة الاعتماد", "عنوان الاستلام"};
        mothersTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        mothersTable = new JTable(mothersTableModel);
        formatTable(mothersTable);
        addPhoneFilter(searchField, mothersTable, 3);
        panel.add(createTableScrollPane(mothersTable), BorderLayout.CENTER);

        return panel;
    }

    
    private JPanel createOrdersPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        topBar.setOpaque(false);
        JButton btnRefresh = new JButton("🔄 تحديث الطلبات");
        styleButton(btnRefresh, BLUE_LIGHT, BLUE_DARK);
        btnRefresh.addActionListener(e -> loadOrders());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(btnRefresh);
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        searchPanel.setOpaque(false);
        JLabel searchLabel = new JLabel("بحث:");
        searchLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        searchLabel.setForeground(INK);
        JTextField searchField = new JTextField(25);
        searchField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        searchField.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        searchField.setToolTipText("ابحث برقم الهاتف أو اسم العميل");
        searchPanel.add(searchLabel);
        searchPanel.add(searchField);
        topBar.add(actions, BorderLayout.WEST);
        topBar.add(searchPanel, BorderLayout.EAST);

        panel.add(topBar, BorderLayout.NORTH);

        String[] cols = {"رقم الطلب", "العميل", "هاتف العميل", "الأم المنتجة", "الإجمالي", "موعد الاستلام", "الحالة"};
        ordersTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        ordersTable = new JTable(ordersTableModel);
        formatTable(ordersTable);
        addPhoneFilter(searchField, ordersTable, 2);
        panel.add(createTableScrollPane(ordersTable), BorderLayout.CENTER);

        return panel;
    }

    
    private JPanel createCustomersPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        topBar.setOpaque(false);
        JButton btnRefresh = new JButton("🔄 تحديث العملاء");
        styleButton(btnRefresh, BLUE_LIGHT, BLUE_DARK);
        btnRefresh.addActionListener(e -> loadCustomers());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(btnRefresh);
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        searchPanel.setOpaque(false);
        JLabel searchLabel = new JLabel("بحث:");
        searchLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        searchLabel.setForeground(INK);
        JTextField searchField = new JTextField(25);
        searchField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        searchField.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        searchField.setToolTipText("ابحث برقم الهاتف أو اسم العميل");
        searchPanel.add(searchLabel);
        searchPanel.add(searchField);
        topBar.add(actions, BorderLayout.WEST);
        topBar.add(searchPanel, BorderLayout.EAST);

        panel.add(topBar, BorderLayout.NORTH);

        String[] cols = {"المعرف", "الاسم", "البريد الإلكتروني", "رقم الهاتف", "الحالة", "تاريخ الانضمام"};
        customersTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        customersTable = new JTable(customersTableModel);
        formatTable(customersTable);
        addPhoneFilter(searchField, customersTable, 3);
        panel.add(createTableScrollPane(customersTable), BorderLayout.CENTER);

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
        table.setRowHeight(38);
        table.setFont(new Font("SansSerif", Font.PLAIN, 13));
        table.setForeground(new Color(48, 42, 45));
        table.setBackground(Color.WHITE);
        table.setSelectionBackground(BLUE_LIGHT);
        table.setSelectionForeground(BLUE_DARK);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFillsViewportHeight(true);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
        table.getTableHeader().setBackground(INK);
        table.getTableHeader().setForeground(Color.WHITE);
        table.getTableHeader().setPreferredSize(new Dimension(0, 42));
        table.getTableHeader().setReorderingAllowed(false);
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(new ZebraCellRenderer());
        }
    }

    private JScrollPane createTableScrollPane(JTable table) {
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER, 1));
        scrollPane.getViewport().setBackground(Color.WHITE);
        return scrollPane;
    }

    private void loadAllData() {
        loadInvitations();
        loadMothers();
        loadOrders();
        loadCustomers();
        updateOverviewStats();
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
        updateOverviewStats();
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
        updateOverviewStats();
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
        updateOverviewStats();
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
        updateOverviewStats();
    }

    private void handleCreateInvitation() {
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

    private void handleRevokeInvitation() {
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
        JTable table = model == invitationsTableModel ? invitationsTable
                : model == mothersTableModel ? mothersTable
                : model == ordersTableModel ? ordersTable : customersTable;
        int selectedViewRow = table.getSelectedRow();
        return selectedViewRow < 0 ? -1 : table.convertRowIndexToModel(selectedViewRow);
    }

    private static class ZebraCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                        boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            setHorizontalAlignment(SwingConstants.RIGHT);
            setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
            if (!selected) {
                setBackground(row % 2 == 0 ? Color.WHITE : new Color(247, 249, 253));
                setForeground(new Color(48, 42, 45));
            }
            return this;
        }
    }
}
