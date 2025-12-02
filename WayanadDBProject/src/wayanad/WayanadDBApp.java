package wayanad;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.List;

/**
 * Wayanad Disaster Management System – Full CRUD (single-file)
 * Java 21 / MySQL 8+
 *
 * - Dashboard with per-table managers (CRUD)
 * - Table Viewer with Load / Search / quick manager buttons
 * - Read-only log tables are viewable but not editable
 * - Supports Transfer_Error_Log (Option-B auto-adjust) viewing
 *
 * NOTE: paste this entire file (parts 1..3) into WayanadDBApp.java
 */
public class WayanadDBApp extends JFrame {

    // ---------- DB CONFIG ----------
    static final String JDBC_URL = "jdbc:mysql://localhost:3306/wayanad_disaster";
    static final String JDBC_USER = "root";
    static final String JDBC_PASSWORD = "Secretkey@19"; // <--- change if needed

    // ---------- VIEWER UI ----------
    private JComboBox<String> tableSelector;
    private JTextField searchField;
    private JButton loadButton, searchButton, backButton;
    private JButton addButton, updateButton, deleteButton; // opens manager
    private JTable table;
    private DefaultTableModel model;
    private String currentTable = "";

    public WayanadDBApp(boolean startDashboard) {
        if (startDashboard) showMainDashboard();
        else showTableViewer();
    }

    // ---------- DASHBOARD ----------
    private void showMainDashboard() {
        getContentPane().removeAll();
        setTitle("Wayanad Disaster Management System – Home");
        setSize(1200, 780);
        setMinimumSize(new Dimension(1100, 700));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JPanel bg = gradientPanel();
        bg.setLayout(new BorderLayout());

        JLabel title = new JLabel("Wayanad Disaster Management System", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));
        title.setForeground(new Color(20, 40, 90));
        title.setBorder(BorderFactory.createEmptyBorder(24, 0, 8, 0));

        JPanel topButtons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        topButtons.setOpaque(false);
        JButton viewerBtn = createStyledButton("🧭 Open Table Viewer");
        JButton statsBtn  = createStyledButton("📊 Statistics Dashboard");
        JButton aboutBtn  = createStyledButton("ℹ About");
        topButtons.add(viewerBtn); topButtons.add(statsBtn); topButtons.add(aboutBtn);

        JPanel grid = new JPanel(new GridLayout(5, 3, 12, 12));
        grid.setOpaque(false);
        grid.setBorder(BorderFactory.createEmptyBorder(18, 24, 24, 24));

        String[] managers = {
                "Person","Victim","Camp",
                "Victim_Camp","Supply","Supply_Movement",
                "Donation","MissingPerson","BodyIdentification",
                "RehabAllocation","RescueTeam","RescueOperation",
                "Activity_Log (Read-only)","Supply_Audit (Read-only)",
                "Transfer_Error_Log (Read-only)"
        };
        for (String t : managers) {
            JButton b = createStyledButton("🗂 Manage " + t);
            b.addActionListener(e -> openManagerByName(t));
            grid.add(b);
        }

        bg.add(title, BorderLayout.NORTH);
        bg.add(topButtons, BorderLayout.CENTER);
        bg.add(grid, BorderLayout.SOUTH);
        add(bg);

        viewerBtn.addActionListener(e -> showTableViewer());
        statsBtn.addActionListener(e -> showStatisticsDashboard());
        aboutBtn.addActionListener(e -> showAboutDialog());

        revalidate(); repaint();
    }

    private JPanel gradientPanel() {
        return new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                GradientPaint gp = new GradientPaint(0, 0, new Color(160,200,255),
                        0, getHeight(), new Color(240,248,255));
                g2.setPaint(gp); g2.fillRect(0,0,getWidth(),getHeight());
            }
        };
    }
    private JButton createStyledButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        btn.setForeground(Color.WHITE);
        btn.setBackground(new Color(70, 130, 255));
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(10,16,10,16));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e){ btn.setBackground(new Color(50,110,255)); }
            public void mouseExited(MouseEvent e){ btn.setBackground(new Color(70,130,255)); }
        });
        return btn;
    }

    // ---------- TABLE VIEWER ----------
    private void showTableViewer() {
        getContentPane().removeAll();
        setTitle("Database Table Viewer");
        setSize(1280, 800);
        setMinimumSize(new Dimension(1100, 700));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(225, 240, 255));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        top.setBackground(new Color(190, 220, 255));
        top.setBorder(BorderFactory.createTitledBorder("Search & View Tables"));

        tableSelector = new JComboBox<>(new String[]{
                "Person","Victim","Camp","Victim_Camp","RescueTeam","RescueOperation",
                "Supply","Supply_Movement","Donation","MissingPerson","BodyIdentification",
                "RehabAllocation","Activity_Log","Supply_Audit","Transfer_Error_Log"
        });
        loadButton = new JButton("Load");
        searchField = new JTextField(18);
        searchButton = new JButton("Search");
        backButton = new JButton("Home");
        addButton = new JButton("➕ Add");
        updateButton = new JButton("✏ Update");
        deleteButton = new JButton("🗑 Delete");

        for (JButton b : new JButton[]{loadButton, searchButton, addButton, updateButton, deleteButton}) {
            b.setBackground(new Color(100,150,255)); b.setForeground(Color.WHITE); b.setFocusPainted(false);
        }
        backButton.setBackground(new Color(255,120,100)); backButton.setForeground(Color.WHITE);

        top.add(new JLabel("Table:")); top.add(tableSelector); top.add(loadButton);
        top.add(new JLabel("Search:")); top.add(searchField); top.add(searchButton);
        top.add(addButton); top.add(updateButton); top.add(deleteButton);
        top.add(backButton);

        model = new DefaultTableModel();
        table = new JTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setBackground(new Color(240,248,255));
        table.setGridColor(new Color(180,200,240));
        table.setSelectionBackground(new Color(160,200,255));
        JScrollPane scroll = new JScrollPane(table);
        scroll.getViewport().setBackground(new Color(240,248,255));

        add(top, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        loadButton.addActionListener(e -> loadTableData());
        searchButton.addActionListener(e -> searchInCurrentTable());
        backButton.addActionListener(e -> showMainDashboard());

        // Launch CRUD manager windows from viewer buttons
        ActionListener launch = e -> {
            String name = Objects.toString(tableSelector.getSelectedItem(), "");
            if (name.endsWith("(Read-only)")) name = name.split(" ")[0];
            openManagerByName(name);
        };
        addButton.addActionListener(launch);
        updateButton.addActionListener(launch);
        deleteButton.addActionListener(launch);

        revalidate(); repaint();
    }

    private void loadTableData() {
        currentTable = (String) tableSelector.getSelectedItem();
        model.setRowCount(0); model.setColumnCount(0);
        try (Connection con = getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM " + currentTable)) {
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            for (int i=1;i<=cols;i++) model.addColumn(meta.getColumnName(i));
            int count=0;
            while (rs.next()) {
                Object[] row = new Object[cols];
                for (int i=1;i<=cols;i++) row[i-1]=rs.getObject(i);
                model.addRow(row); count++;
            }
            JOptionPane.showMessageDialog(this, "Loaded " + count + " rows from " + currentTable);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error loading table: " + ex.getMessage());
        }
    }

    private void searchInCurrentTable() {
        String search = searchField.getText().trim();
        if (search.isEmpty()) { JOptionPane.showMessageDialog(this, "Enter a search term"); return; }
        currentTable = (String) tableSelector.getSelectedItem();
        model.setRowCount(0); model.setColumnCount(0);
        try (Connection con = getConnection(); Statement st = con.createStatement()) {
            ResultSetMetaData meta = con.createStatement()
                    .executeQuery("SELECT * FROM " + currentTable + " LIMIT 1").getMetaData();
            int colCount = meta.getColumnCount();
            StringBuilder concat = new StringBuilder("CONCAT(");
            for (int i=1;i<=colCount;i++){ concat.append(meta.getColumnName(i)); if (i<colCount) concat.append(", '|', "); }
            concat.append(")");
            String query = "SELECT * FROM " + currentTable + " WHERE LOWER(" + concat + ") LIKE LOWER('%" +
                    search.replace("'", "''") + "%')";
            ResultSet rs = st.executeQuery(query);
            for (int i=1;i<=colCount;i++) model.addColumn(meta.getColumnName(i));
            int hits=0;
            while (rs.next()) {
                Object[] row = new Object[colCount];
                for (int i=1;i<=colCount;i++) row[i-1]=rs.getObject(i);
                model.addRow(row); hits++;
            }
            JOptionPane.showMessageDialog(this, "Found " + hits + " records.");
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Search Error: " + ex.getMessage());
        }
    }
    // ---------- STATS ----------
    private void showStatisticsDashboard() {
        getContentPane().removeAll();
        setTitle("Wayanad – Statistics");
        setSize(1000, 700);
        setLocationRelativeTo(null);
        getContentPane().setBackground(new Color(225, 240, 255));
        setLayout(new BorderLayout());

        JLabel title = new JLabel("📊 Wayanad Disaster Summary", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(new Color(20, 60, 120));
        title.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        JPanel statsPanel = new JPanel(new GridLayout(2, 3, 20, 20));
        statsPanel.setBackground(new Color(225, 240, 255));
        statsPanel.setBorder(BorderFactory.createEmptyBorder(20, 60, 20, 60));

        String[][] stats = {
                {"Total Victims", "SELECT COUNT(*) FROM Victim"},
                {"Rescue Teams", "SELECT COUNT(*) FROM RescueTeam"},
                {"Relief Camps", "SELECT COUNT(*) FROM Camp"},
                {"Total Donations", "SELECT COUNT(*) FROM Donation"},
                {"Missing Persons", "SELECT COUNT(*) FROM MissingPerson"},
                {"Rehab Allocations", "SELECT COUNT(*) FROM RehabAllocation"}
        };
        for (String[] s : stats) statsPanel.add(createStatCard(s[0], fetchCount(s[1])));

        JButton backBtn = createStyledButton("⬅ Back");
        backBtn.setBackground(new Color(255,120,100));
        backBtn.addActionListener(e -> showMainDashboard());

        add(title, BorderLayout.NORTH);
        add(statsPanel, BorderLayout.CENTER);
        add(backBtn, BorderLayout.SOUTH);

        revalidate(); repaint();
    }
    private JPanel createStatCard(String label, String value) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(new Color(190, 220, 255));
        card.setBorder(BorderFactory.createLineBorder(new Color(100, 140, 220), 2, true));
        JLabel l = new JLabel(label, SwingConstants.CENTER); l.setFont(new Font("Segoe UI", Font.BOLD, 16));
        JLabel v = new JLabel(value, SwingConstants.CENTER); v.setFont(new Font("Segoe UI", Font.BOLD, 26));
        v.setForeground(new Color(10, 50, 160));
        card.add(l, BorderLayout.NORTH); card.add(v, BorderLayout.CENTER);
        return card;
    }
    private String fetchCount(String q) {
        try (Connection c=getConnection(); Statement s=c.createStatement(); ResultSet r=s.executeQuery(q)) {
            if (r.next()) return String.valueOf(r.getInt(1));
        } catch (SQLException e){ return "Error"; }
        return "0";
    }

    // ---------- ABOUT ----------
    private void showAboutDialog() {
        String msg = """
                🏫 Wayanad Disaster Management System

                Separate management windows for ALL tables (full CRUD).
                Triggers run inside MySQL (e.g., Supply_Audit; Camp occupancy).
                """;
        JTextArea t = new JTextArea(msg);
        t.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        t.setEditable(false);
        t.setBackground(new Color(235, 245, 255));
        t.setMargin(new Insets(10,10,10,10));
        JOptionPane.showMessageDialog(this, new JScrollPane(t), "About", JOptionPane.INFORMATION_MESSAGE);
    }

    // ---------- DB ----------
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
    }

    // ---------- OPEN MANAGERS ----------
    private void openManagerByName(String label) {
        if (label.contains("(Read-only)")) label = label.split(" ")[0];
        switch (label) {
            case "Person" -> openManager(personSpec());
            case "Victim" -> openManager(victimSpec());
            case "Camp" -> openManager(campSpec());
            case "Victim_Camp" -> openManager(victimCampSpec());
            case "Supply" -> openManager(supplySpec());
            case "Supply_Movement" -> openManager(supplyMovementSpec());
            case "Donation" -> openManager(donationSpec());
            case "MissingPerson" -> openManager(missingSpec());
            case "BodyIdentification" -> openManager(bodyIdSpec());
            case "RehabAllocation" -> openManager(rehabSpec());
            case "RescueTeam" -> openManager(teamSpec());
            case "RescueOperation" -> openManager(opSpec());
            case "Activity_Log" -> openManager(activityLogSpec());
            case "Supply_Audit" -> openManager(supplyAuditSpec());
            case "Transfer_Error_Log" -> openManager(errorLogSpec());
            default -> JOptionPane.showMessageDialog(this, "Unknown manager: " + label);
        }
    }
    private void openManager(TableSpec spec) { new RecordManager(this, spec).setVisible(true); }

    // ---------- TABLE SPECS ----------
    enum InputType { TEXT, INT, DECIMAL, DATE, DATETIME, ENUM, BOOL, FK }

    static class FieldSpec {
        final String name, label; final InputType type;
        final boolean nullable; final String enumCSV; final String fkQuery;
        boolean readonly=false;
        FieldSpec(String n, String l, InputType t, boolean nul, String e, String fk){name=n;label=l;type=t;nullable=nul;enumCSV=e;fkQuery=fk;}
        FieldSpec readonly(){ this.readonly=true; return this; }
    }
    static class TableSpec {
        final String table; final String pk; final boolean pkAuto;
        final java.util.List<FieldSpec> fields = new ArrayList<>();
        boolean readOnly=false;
        TableSpec(String t,String p,boolean a){table=t;pk=p;pkAuto=a;}
        TableSpec field(FieldSpec f){fields.add(f); return this;}
        TableSpec readOnly(boolean ro){readOnly=ro; return this;}
    }

    private TableSpec personSpec(){ return new TableSpec("Person","person_id", false)
            .field(new FieldSpec("person_id","Person ID",InputType.INT,false,null,null))
            .field(new FieldSpec("name","Name",InputType.TEXT,false,null,null))
            .field(new FieldSpec("age","Age",InputType.INT,true,null,null))
            .field(new FieldSpec("gender","Gender",InputType.ENUM,true,"M,F,O",null))
            .field(new FieldSpec("aadhar_no","Aadhar",InputType.TEXT,true,null,null))
            .field(new FieldSpec("phone","Phone",InputType.TEXT,true,null,null))
            .field(new FieldSpec("role","Role",InputType.ENUM,false,"victim,volunteer,rescuer,donor,admin",null))
            .field(new FieldSpec("created_on","Created On",InputType.DATETIME,true,null,null).readonly());
    }
    private TableSpec victimSpec(){ return new TableSpec("Victim","victim_id", false)
            .field(new FieldSpec("victim_id","Victim (Person ID)",InputType.FK,false,null,
                    "SELECT person_id AS id, CONCAT(person_id,' - ',name) AS label FROM Person WHERE role='victim'"))
            .field(new FieldSpec("family_id","Family ID",InputType.TEXT,true,null,null))
            .field(new FieldSpec("status","Status",InputType.ENUM,true,"missing,rescued,deceased,hospitalized,relocated",null))
            .field(new FieldSpec("injuries","Injuries",InputType.TEXT,true,null,null))
            .field(new FieldSpec("displaced","Displaced",InputType.BOOL,true,null,null))
            .field(new FieldSpec("registration_date","Reg Date",InputType.DATE,true,null,null));
    }
    private TableSpec campSpec(){ return new TableSpec("Camp","camp_id", true)
            .field(new FieldSpec("camp_id","Camp ID (auto)",InputType.INT,true,null,null).readonly())
            .field(new FieldSpec("name","Name",InputType.TEXT,false,null,null))
            .field(new FieldSpec("location","Location",InputType.TEXT,true,null,null))
            .field(new FieldSpec("capacity","Capacity",InputType.INT,true,null,null))
            .field(new FieldSpec("established_date","Established",InputType.DATE,true,null,null))
            .field(new FieldSpec("current_occupancy","Current Occupancy",InputType.INT,true,null,null).readonly());
    }
    private TableSpec victimCampSpec(){ return new TableSpec("Victim_Camp","vc_id", true)
            .field(new FieldSpec("vc_id","VC ID (auto)",InputType.INT,true,null,null).readonly())
            .field(new FieldSpec("victim_id","Victim",InputType.FK,false,null,"SELECT victim_id AS id, victim_id AS label FROM Victim"))
            .field(new FieldSpec("camp_id","Camp",InputType.FK,false,null,"SELECT camp_id AS id, CONCAT(camp_id,' - ',name) AS label FROM Camp"))
            .field(new FieldSpec("date_admitted","Date Admitted",InputType.DATE,true,null,null))
            .field(new FieldSpec("date_released","Date Released",InputType.DATE,true,null,null));
    }
    private TableSpec supplySpec(){ return new TableSpec("Supply","supply_id", true)
            .field(new FieldSpec("supply_id","Supply ID (auto)",InputType.INT,true,null,null).readonly())
            .field(new FieldSpec("name","Name",InputType.TEXT,false,null,null))
            .field(new FieldSpec("quantity_on_hand","Quantity",InputType.INT,false,null,null))
            .field(new FieldSpec("unit","Unit",InputType.TEXT,true,null,null));
    }
    private TableSpec supplyMovementSpec(){ return new TableSpec("Supply_Movement","move_id", true)
            .field(new FieldSpec("move_id","Move ID (auto)",InputType.INT,true,null,null).readonly())
            .field(new FieldSpec("supply_id","Supply",InputType.FK,false,null,"SELECT supply_id AS id, CONCAT(supply_id,' - ',name) AS label FROM Supply"))
            .field(new FieldSpec("from_location","From Location",InputType.TEXT,true,null,null))
            .field(new FieldSpec("to_camp_id","To Camp",InputType.FK,true,null,"SELECT camp_id AS id, CONCAT(camp_id,' - ',name) AS label FROM Camp"))
            .field(new FieldSpec("qty","Quantity",InputType.INT,false,null,null))
            .field(new FieldSpec("moved_on","Moved On (YYYY-MM-DD HH:MM:SS)",InputType.DATETIME,true,null,null));
    }
    private TableSpec donationSpec(){ return new TableSpec("Donation","donation_id", true)
            .field(new FieldSpec("donation_id","Donation ID (auto)",InputType.INT,true,null,null).readonly())
            .field(new FieldSpec("donor_id","Donor (Person)",InputType.FK,false,null,"SELECT person_id AS id, CONCAT(person_id,' - ',name) AS label FROM Person WHERE role='donor'"))
            .field(new FieldSpec("amount","Amount",InputType.DECIMAL,false,null,null))
            .field(new FieldSpec("donation_type","Type",InputType.TEXT,true,null,null))
            .field(new FieldSpec("date_received","Date",InputType.DATE,true,null,null))
            .field(new FieldSpec("notes","Notes",InputType.TEXT,true,null,null));
    }
    private TableSpec missingSpec(){ return new TableSpec("MissingPerson","missing_id", true)
            .field(new FieldSpec("missing_id","Missing ID (auto)",InputType.INT,true,null,null).readonly())
            .field(new FieldSpec("name","Name",InputType.TEXT,false,null,null))
            .field(new FieldSpec("age","Age",InputType.INT,true,null,null))
            .field(new FieldSpec("gender","Gender",InputType.ENUM,true,"M,F,O",null))
            .field(new FieldSpec("last_seen_location","Last Seen Location",InputType.TEXT,true,null,null))
            .field(new FieldSpec("date_reported","Date Reported",InputType.DATE,true,null,null))
            .field(new FieldSpec("status","Status",InputType.ENUM,true,"missing,found,identified",null));
    }
    private TableSpec bodyIdSpec(){ return new TableSpec("BodyIdentification","body_id", true)
            .field(new FieldSpec("body_id","Body ID (auto)",InputType.INT,true,null,null).readonly())
            .field(new FieldSpec("missing_id","Missing Ref",InputType.FK,true,null,"SELECT missing_id AS id, CONCAT(missing_id,' - ',name) AS label FROM MissingPerson"))
            .field(new FieldSpec("dna_sample_id","DNA Sample ID",InputType.TEXT,true,null,null))
            .field(new FieldSpec("identified","Identified",InputType.BOOL,true,null,null))
            .field(new FieldSpec("id_date","Identified Date",InputType.DATE,true,null,null))
            .field(new FieldSpec("notes","Notes",InputType.TEXT,true,null,null));
    }
    private TableSpec rehabSpec(){ return new TableSpec("RehabAllocation","alloc_id", true)
            .field(new FieldSpec("alloc_id","Alloc ID (auto)",InputType.INT,true,null,null).readonly())
            .field(new FieldSpec("victim_id","Victim",InputType.FK,false,null,"SELECT victim_id AS id, victim_id AS label FROM Victim"))
            .field(new FieldSpec("house_id","House ID",InputType.TEXT,true,null,null))
            .field(new FieldSpec("allocation_date","Allocation Date",InputType.DATE,true,null,null))
            .field(new FieldSpec("amount_granted","Amount Granted",InputType.DECIMAL,true,null,null));
    }
    private TableSpec teamSpec(){ return new TableSpec("RescueTeam","team_id", true)
            .field(new FieldSpec("team_id","Team ID (auto)",InputType.INT,true,null,null).readonly())
            .field(new FieldSpec("agency","Agency",InputType.TEXT,false,null,null))
            .field(new FieldSpec("team_type","Team Type",InputType.TEXT,true,null,null))
            .field(new FieldSpec("contact","Contact",InputType.TEXT,true,null,null));
    }
    private TableSpec opSpec(){ return new TableSpec("RescueOperation","op_id", true)
            .field(new FieldSpec("op_id","Op ID (auto)",InputType.INT,true,null,null).readonly())
            .field(new FieldSpec("team_id","Team",InputType.FK,false,null,"SELECT team_id AS id, CONCAT(team_id,' - ',agency) AS label FROM RescueTeam"))
            .field(new FieldSpec("location","Location",InputType.TEXT,true,null,null))
            .field(new FieldSpec("start_time","Start Time (YYYY-MM-DD HH:MM:SS)",InputType.DATETIME,true,null,null))
            .field(new FieldSpec("end_time","End Time (YYYY-MM-DD HH:MM:SS)",InputType.DATETIME,true,null,null))
            .field(new FieldSpec("personnel_count","Personnel Count",InputType.INT,true,null,null))
            .field(new FieldSpec("notes","Notes",InputType.TEXT,true,null,null));
    }
    private TableSpec activityLogSpec(){ return new TableSpec("Activity_Log","log_id", true)
            .readOnly(true)
            .field(new FieldSpec("log_id","Log ID",InputType.INT,true,null,null).readonly())
            .field(new FieldSpec("table_name","Table",InputType.TEXT,true,null,null).readonly())
            .field(new FieldSpec("action_type","Action",InputType.ENUM,true,"INSERT,UPDATE,DELETE",null).readonly())
            .field(new FieldSpec("record_id","Record ID",InputType.INT,true,null,null).readonly())
            .field(new FieldSpec("action_timestamp","Timestamp",InputType.DATETIME,true,null,null).readonly());
    }
    private TableSpec supplyAuditSpec(){ return new TableSpec("Supply_Audit","audit_id", true)
            .readOnly(true)
            .field(new FieldSpec("audit_id","Audit ID",InputType.INT,true,null,null).readonly())
            .field(new FieldSpec("supply_id","Supply",InputType.INT,true,null,null).readonly())
            .field(new FieldSpec("change_qty","Change",InputType.INT,true,null,null).readonly())
            .field(new FieldSpec("new_qty","New Qty",InputType.INT,true,null,null).readonly())
            .field(new FieldSpec("changed_on","Changed On",InputType.DATETIME,true,null,null).readonly())
            .field(new FieldSpec("reason","Reason",InputType.TEXT,true,null,null).readonly());
    }

    // NEW: Transfer_Error_Log spec (read-only)
    private TableSpec errorLogSpec(){
        return new TableSpec("Transfer_Error_Log","log_id", true)
                .readOnly(true)
                .field(new FieldSpec("log_id","Log ID",InputType.INT,true,null,null).readonly())
                .field(new FieldSpec("supply_id","Supply ID",InputType.INT,true,null,null).readonly())
                .field(new FieldSpec("requested_qty","Requested Qty",InputType.INT,true,null,null).readonly())
                .field(new FieldSpec("adjusted_qty","Adjusted Qty",InputType.INT,true,null,null).readonly())
                .field(new FieldSpec("message","Message",InputType.TEXT,true,null,null).readonly())
                .field(new FieldSpec("log_time","Log Time",InputType.DATETIME,true,null,null).readonly());
    }

    // ---------- MANAGER WINDOW ----------
    class RecordManager extends JDialog {
        final TableSpec spec;
        JTable grid; DefaultTableModel gridModel;
        Map<String, JComponent> inputs = new LinkedHashMap<>();

        RecordManager(JFrame owner, TableSpec spec) {
            super(owner, "Manage " + spec.table, true);
            this.spec = spec;
            buildUI();
            loadGrid();
            setMinimumSize(new Dimension(1000, 680));
            setLocationRelativeTo(owner);
            pack(); // ensure actions visible
        }

        private void buildUI() {
            setLayout(new BorderLayout(8,8));

            // Form
            JPanel form = new JPanel(new GridBagLayout());
            form.setBorder(BorderFactory.createTitledBorder("Form – " + spec.table));
            GridBagConstraints gc = new GridBagConstraints();
            gc.insets = new Insets(4,6,4,6);
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.weightx=1;

            int r=0;
            for (FieldSpec f : spec.fields) {
                gc.gridx=0; gc.gridy=r; gc.weightx=0;
                JLabel lab = new JLabel(f.label + (f.nullable ? " (opt)" : ""));
                lab.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                form.add(lab, gc);
                gc.gridx=1; gc.weightx=1;
                JComponent comp = createInput(f);
                comp.setEnabled(!f.readonly && !spec.readOnly);
                form.add(comp, gc);
                inputs.put(f.name, comp);
                r++;
            }

            // Action buttons
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
            actions.setBorder(BorderFactory.createTitledBorder("Actions"));
            JButton addBtn = createStyledButton("➕ Add");
            JButton updBtn = createStyledButton("✏ Update");
            JButton delBtn = createStyledButton("🗑 Delete");
            JButton clrBtn = createStyledButton("⟲ Clear");
            JButton refBtn = createStyledButton("🔄 Refresh");
            if (spec.readOnly){ addBtn.setEnabled(false); updBtn.setEnabled(false); delBtn.setEnabled(false); }
            actions.add(addBtn); actions.add(updBtn); actions.add(delBtn); actions.add(clrBtn); actions.add(refBtn);

            // Grid
            gridModel = new DefaultTableModel();
            grid = new JTable(gridModel);
            grid.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            JScrollPane sp = new JScrollPane(grid);

            // Layout: keep actions visible
            JPanel centerPanel = new JPanel(new BorderLayout());
            centerPanel.add(actions, BorderLayout.NORTH);
            centerPanel.add(sp, BorderLayout.CENTER);

            add(form, BorderLayout.NORTH);
            add(centerPanel, BorderLayout.CENTER);

            // Listeners
            addBtn.addActionListener(e -> doInsert());
            updBtn.addActionListener(e -> doUpdate());
            delBtn.addActionListener(e -> doDelete());
            clrBtn.addActionListener(e -> clearForm());
            refBtn.addActionListener(e -> loadGrid());

            grid.getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) fillFormFromSelection();
            });
        }

        private JComponent createInput(FieldSpec f) {
            switch (f.type) {
                case ENUM -> { JComboBox<String> cb = new JComboBox<>(f.enumCSV.split("\\s*,\\s*")); cb.setSelectedIndex(-1); return cb; }
                case BOOL -> { return new JCheckBox("Yes"); }
                case INT, DECIMAL, TEXT -> { return new JTextField(); }
                case DATE -> { JTextField tf = new JTextField(); tf.setToolTipText("YYYY-MM-DD"); return tf; }
                case DATETIME -> { JTextField tf = new JTextField(); tf.setToolTipText("YYYY-MM-DD HH:MM:SS"); return tf; }
                case FK -> {
                    JComboBox<Item> cb = new JComboBox<>();
                    cb.setRenderer(new DefaultListCellRenderer(){
                        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                            if (value instanceof Item it) setText(it.label);
                            return this;
                        }
                    });
                    loadFK(cb, f.fkQuery); cb.setSelectedIndex(-1); return cb;
                }
            }
            return new JTextField();
        }

        private void loadFK(JComboBox<Item> cb, String query) {
            if (query == null) return;
            try (Connection con = getConnection(); Statement st = con.createStatement(); ResultSet rs = st.executeQuery(query)) {
                while (rs.next()) cb.addItem(new Item(rs.getObject("id"), Objects.toString(rs.getObject("label"), "")));
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(this, "FK load failed: " + e.getMessage());
            }
        }

        private void loadGrid() {
            gridModel.setRowCount(0); gridModel.setColumnCount(0);
            try (Connection con = getConnection();
                 Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM " + spec.table + " ORDER BY 1 DESC")) {
                ResultSetMetaData m = rs.getMetaData();
                int c = m.getColumnCount();
                for (int i=1;i<=c;i++) gridModel.addColumn(m.getColumnName(i));
                while (rs.next()) {
                    Object[] row = new Object[c];
                    for (int i=1;i<=c;i++) row[i-1]=rs.getObject(i);
                    gridModel.addRow(row);
                }
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(this, "Load failed: " + e.getMessage());
            }
        }

        private void clearForm() {
            for (FieldSpec f : spec.fields) {
                JComponent comp = inputs.get(f.name);
                if (comp instanceof JTextField tf) tf.setText("");
                else if (comp instanceof JComboBox<?> cb) cb.setSelectedIndex(-1);
                else if (comp instanceof JCheckBox ch) ch.setSelected(false);
            }
            grid.clearSelection();
        }

        private void fillFormFromSelection() {
            int r = grid.getSelectedRow();
            if (r < 0) return;
            for (FieldSpec f : spec.fields) {
                int col = gridModel.findColumn(f.name);
                if (col < 0) continue;
                Object val = gridModel.getValueAt(r, col);
                JComponent comp = inputs.get(f.name);
                try {
                    switch (f.type) {
                        case TEXT, INT, DECIMAL, DATE, DATETIME -> ((JTextField)comp).setText(Objects.toString(val,""));
                        case ENUM -> { @SuppressWarnings("unchecked") JComboBox<String> cb = (JComboBox<String>) comp; cb.setSelectedItem(Objects.toString(val,"")); }
                        case BOOL -> ((JCheckBox)comp).setSelected(val!=null && (val.equals(true) || "1".equals(val.toString()) || "true".equalsIgnoreCase(val.toString())));
                        case FK -> { @SuppressWarnings("unchecked") JComboBox<Item> cb = (JComboBox<Item>) comp; selectFK(cb, val); }
                    }
                } catch (Exception ignore) {}
            }
        }
        private void selectFK(JComboBox<Item> cb, Object id) {
            for (int i=0;i<cb.getItemCount();i++) {
                Item it = cb.getItemAt(i);
                if (Objects.equals(String.valueOf(it.id), String.valueOf(id))) { cb.setSelectedIndex(i); return; }
            }
            cb.setSelectedIndex(-1);
        }

        private void doInsert() {
            if (spec.readOnly) return;
            try (Connection con = getConnection()) {
                List<FieldSpec> insertables = new ArrayList<>();
                for (FieldSpec f : spec.fields) {
                    if (f.readonly) continue;
                    if (f.name.equals(spec.pk) && spec.pkAuto) continue;
                    insertables.add(f);
                }
                StringBuilder cols = new StringBuilder();
                StringBuilder qs = new StringBuilder();
                for (int i=0;i<insertables.size();i++) {
                    cols.append(insertables.get(i).name); qs.append("?");
                    if (i<insertables.size()-1) { cols.append(","); qs.append(","); }
                }
                String sql = "INSERT INTO " + spec.table + " (" + cols + ") VALUES (" + qs + ")";
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    setParams(ps, insertables);
                    int n = ps.executeUpdate();
                    JOptionPane.showMessageDialog(this, "Inserted " + n + " row(s).");
                    loadGrid();
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Insert failed: " + ex.getMessage());
            }
        }

        private void doUpdate() {
            if (spec.readOnly) return;
            int r = grid.getSelectedRow();
            if (r < 0) { JOptionPane.showMessageDialog(this, "Select a row to update."); return; }
            Object pkVal = gridModel.getValueAt(r, gridModel.findColumn(spec.pk));
            try (Connection con = getConnection()) {
                List<FieldSpec> updatable = new ArrayList<>();
                for (FieldSpec f : spec.fields) if (!f.readonly && !f.name.equals(spec.pk)) updatable.add(f);
                StringBuilder set = new StringBuilder();
                for (int i=0;i<updatable.size();i++) {
                    set.append(updatable.get(i).name).append("=?");
                    if (i<updatable.size()-1) set.append(",");
                }
                String sql = "UPDATE " + spec.table + " SET " + set + " WHERE " + spec.pk + "=?";
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    int idx = setParams(ps, updatable);
                    ps.setObject(idx+1, pkVal);
                    int n = ps.executeUpdate();
                    JOptionPane.showMessageDialog(this, "Updated " + n + " row(s).");
                    loadGrid();
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Update failed: " + ex.getMessage());
            }
        }

        private void doDelete() {
            if (spec.readOnly) return;
            int r = grid.getSelectedRow();
            if (r < 0) { JOptionPane.showMessageDialog(this, "Select a row to delete."); return; }
            Object pkVal = gridModel.getValueAt(r, gridModel.findColumn(spec.pk));
            if (JOptionPane.showConfirmDialog(this, "Delete " + spec.table + " where " + spec.pk + "=" + pkVal + " ?",
                    "Confirm", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            try (Connection con = getConnection();
                 PreparedStatement ps = con.prepareStatement("DELETE FROM " + spec.table + " WHERE " + spec.pk + "=?")) {
                ps.setObject(1, pkVal);
                int n = ps.executeUpdate();
                JOptionPane.showMessageDialog(this, "Deleted " + n + " row(s).");
                loadGrid();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Delete failed: " + ex.getMessage());
            }
        }

        private int setParams(PreparedStatement ps, List<FieldSpec> fields) throws SQLException {
            int idx=1;
            for (FieldSpec f : fields) {
                Object v = readInputValue(f);
                if (v == null && !f.nullable) throw new SQLException("Field '" + f.label + "' is required.");
                switch (f.type) {
                    case INT -> { if (v==null) ps.setNull(idx++, Types.INTEGER); else ps.setInt(idx++, Integer.parseInt(v.toString())); }
                    case DECIMAL -> { if (v==null) ps.setNull(idx++, Types.DECIMAL); else ps.setBigDecimal(idx++, new BigDecimal(v.toString())); }
                    case BOOL -> { if (v==null) ps.setNull(idx++, Types.TINYINT); else ps.setBoolean(idx++, (Boolean)v); }
                    case DATE, DATETIME, TEXT, ENUM -> { if (v==null) ps.setNull(idx++, Types.VARCHAR); else ps.setString(idx++, v.toString()); }
                    case FK -> { if (v==null) ps.setNull(idx++, Types.INTEGER); else ps.setObject(idx++, ((Item)v).id); }
                }
            }
            return idx-1;
        }

        private Object readInputValue(FieldSpec f) {
            JComponent c = inputs.get(f.name);
            switch (f.type) {
                case TEXT, INT, DECIMAL, DATE, DATETIME -> {
                    String s = ((JTextField)c).getText().trim();
                    if (s.isEmpty()) return null; return s;
                }
                case ENUM -> {
                    @SuppressWarnings("unchecked") JComboBox<String> cb = (JComboBox<String>) c;
                    return cb.getSelectedItem() == null ? null : cb.getSelectedItem().toString();
                }
                case BOOL -> { JCheckBox ch = (JCheckBox) c; return ch.isSelected(); }
                case FK -> { @SuppressWarnings("unchecked") JComboBox<Item> cb = (JComboBox<Item>) c; return cb.getSelectedItem(); }
            }
            return null;
        }
    }

    // FK item holder
    static class Item {
        final Object id; final String label;
        Item(Object id, String label){ this.id=id; this.label=label; }
        public String toString(){ return label; }
    }

    // ---------- MAIN ----------
    public static void main(String[] args) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            SwingUtilities.invokeLater(() -> new WayanadDBApp(true).setVisible(true));
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Failed to start application:\n" + e.getMessage());
        }
    }
}
