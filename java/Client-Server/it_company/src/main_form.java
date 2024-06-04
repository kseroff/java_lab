
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;

/**
 * @author Kseroff
 */
public class main_form extends javax.swing.JFrame {

    final int limit;
    int deleteRow;
    boolean wasAtTop;
    int offset;
    int numberСolumns;
    String FieldsTypeJson;
    JTable TableNow;
    Map<String, Map<String, String>> FilterInfo;
    String SearchValue;

    public static Request client;

    public main_form() {
        this.limit = 50;
        setResizable(false);
        initComponents();

        client = new Request("localhost", 12345);

        this.FilterInfo = new HashMap<>();
        this.offset = 0;
        this.deleteRow = 0;
        this.numberСolumns = 0;
        this.FieldsTypeJson = "";
        this.SearchValue = "";
        this.wasAtTop = true;

        buttonFilters.addActionListener(new OpenFilterButton());
        buttonAdd.addActionListener(new OpenAddEditButton());
        checkboxFilters.addItemListener(new CheckboxFilter());
        buttonSearch.addActionListener(new ButtonSearch());
        buttonDelete.addActionListener(new DeleteRowTableButton());
        jTabbedPane.addChangeListener(new PanelChanged());
        buttonUpdate.addActionListener(new ButtonUpdate());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                client.close();
                dispose();
            }
        });

        createDynamicPanels(client.getTableNames());
    }

    private void createDynamicPanels(String jsonString) {
        // Удаляем все существующие панели(если они есть неожиданно)
        jTabbedPane.removeAll();

        JSONArray jsonArray = new JSONArray(jsonString);

        for (int i = 0; i < jsonArray.length(); i++) {
            String tableName = jsonArray.getString(i);
            JPanel panel = new JPanel(new BorderLayout());
            JTable table = new JTable();
            table.setAutoCreateRowSorter(true);
            table.setModel(new DefaultTableModel() {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false; // Запрет редактирования ячеек
                }
            });

            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        JTable target = (JTable) e.getSource();
                        int row = target.getSelectedRow();
                        DefaultTableModel model = (DefaultTableModel) target.getModel();
                        Map<String, String> keyValueMap = new HashMap<>();

                        // Получение данных из выбранной строки и добавление их в Map
                        for (int col = 0; col < model.getColumnCount(); col++) {
                            String columnName = model.getColumnName(col);
                            String cellValue = model.getValueAt(row, col).toString();
                            keyValueMap.put(columnName, cellValue);
                        }

                        AddEditForm addform = new AddEditForm(new InterfaceCallback() {
                            @Override
                            public void onApply(String query) {
                                JSONObject jsonArray = new JSONObject(query);
                                JSONArray rows = jsonArray.getJSONArray("rows");

                                JSONObject rowJson = rows.getJSONObject(0); // Предполагаем, что rows содержит только одну строку
                                for (int i = 0; i < model.getColumnCount(); i++) {
                                    Object value = rowJson.opt(model.getColumnName(i)); // Получаем значение по имени столбца
                                    if (value != null) {
                                        model.setValueAt(value, row, i);
                                    }
                                }
                            }

                            @Override
                            public void onCancel() {
                            }
                        }, client, keyValueMap, FieldsTypeJson, getActiveTabTitle(), "EDIT", row);

                    }
                }
            });

            JScrollPane scrollPane = new JScrollPane(table);

            // Добавляем AdjustmentListener для вертикального скроллбара
            JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
            verticalScrollBar.addAdjustmentListener((AdjustmentEvent e) -> {
                if (!e.getValueIsAdjusting() && (tableName == null ? getActiveTabTitle() == null : tableName.equals(getActiveTabTitle()))) {
                    DefaultTableModel model = (DefaultTableModel) TableNow.getModel();
                    int extent = verticalScrollBar.getModel().getExtent();
                    int maximum = verticalScrollBar.getMaximum();
                    int value = verticalScrollBar.getValue();

                    if ((value + extent == maximum) && numberСolumns > offset + limit) {
                        wasAtTop = false;
                        offset += limit;

                        String Jsonresult = client.getDataTable(tableName, offset, SearchValue);
                        JSONObject jsonResult = new JSONObject(Jsonresult);
                        if (jsonResult.has("row_count")) {
                            this.numberСolumns = jsonResult.getInt("row_count");
                        }
                        addRowTable(Jsonresult, false);

                        if ((offset - limit * 2) > -1) {
                            deleteRow += limit;
                            int rowHeight = table.getRowHeight();
                            int rowsToDelete = Math.min(limit, model.getRowCount());
                            for (int j = 0; j < limit; j++) {
                                model.removeRow(0);
                            }
                            int adjustment = rowsToDelete * rowHeight;
                            int newScrollValue = Math.max(value - adjustment, 0);
                            SwingUtilities.invokeLater(() -> verticalScrollBar.setValue(newScrollValue));
                        }

                    } else if (value == 0 && deleteRow > 0) {
                        deleteRow -= limit;
                        if(!wasAtTop)
                            offset -= limit;
                        offset -= limit;

                        wasAtTop = true;
                        int rowHeight = table.getRowHeight();

                        String Jsonresult = client.getDataTable(tableName, offset, SearchValue);
                        JSONObject jsonResult = new JSONObject(Jsonresult);
                        if (jsonResult.has("row_count")) {
                            this.numberСolumns = jsonResult.getInt("row_count");
                        }
                        addRowTable(Jsonresult, true);

                        int adjustment = limit * rowHeight;
                        int newScrollValue = value + adjustment;

                        SwingUtilities.invokeLater(() -> verticalScrollBar.setValue(newScrollValue));

                        for (int j = 1; j <= limit; j++) {
                            model.removeRow(model.getRowCount() - 1);
                        }

                    }
                }
            });

            panel.add(scrollPane, BorderLayout.CENTER);
            jTabbedPane.addTab(tableName, panel);
        }
    }

    private void FillTableWithInfor() {
        offset = 0;
        wasAtTop = true;
        jPanelInfo.setVisible(false);

        checkboxFilters.setState(false);
        String tableName = getActiveTabTitle();

        String InfoJson = client.getDataTable(tableName, offset, "");

        FieldsTypeJson = client.getTypeTable(tableName);
        JSONObject jsonArray = new JSONObject(FieldsTypeJson);
//        this.numberСolumns = jsonArray.getInt("number_rows");

        jsonArray = new JSONObject(InfoJson);
        JSONArray columns = jsonArray.getJSONArray("columns");

        DefaultTableModel model = (DefaultTableModel) TableNow.getModel();
        model.setRowCount(0);
        model.setColumnCount(0);

        for (int i = 0; i < columns.length(); i++) {
            model.addColumn(columns.getString(i));
        }

        addRowTable(InfoJson, false);

        if (checkboxFilters.getState()) {
            SearchQuery(getActiveTabTitle());
        }
    }

   public void addRowTable(String infoJson, boolean addToStart) {
    JSONObject jsonObject = new JSONObject(infoJson);
    JSONArray columns = jsonObject.getJSONArray("columns");
    JSONArray rows = jsonObject.getJSONArray("rows");

    DefaultTableModel model = (DefaultTableModel) TableNow.getModel();
    Map<String, Integer> columnIndices = new HashMap<>();

    // Получаем индексы столбцов по их названиям
    for (int columnIndex = 0; columnIndex < columns.length(); columnIndex++) {
        String columnName = columns.getString(columnIndex);
        columnIndices.put(columnName, columnIndex);
    }

    for (int i = 0; i < rows.length(); i++) {
        JSONObject rowJsonObject = rows.getJSONObject(i);
        Object[] rowData = new Object[columns.length()];
        for (int j = 0; j < columns.length(); j++) {
            String columnName = columns.getString(j);
            int columnIndex = columnIndices.get(columnName);
            rowData[columnIndex] = rowJsonObject.get(columnName);
        }

        if (addToStart) {
            model.insertRow(i, rowData);
        } else {
            model.addRow(rowData);
        }
    }
}

    public static String getActiveTabTitle() {
        int index = jTabbedPane.getSelectedIndex();
        if (index != -1) {
            return jTabbedPane.getTitleAt(index);
        } else {
            return null; // Если нет активной вкладки
        }
    }

    private void SearchQuery(String tableName) {
        String searchText = textFieldSearch.getText().trim();
        StringBuilder queryBuilder = new StringBuilder("");
        if (!searchText.equals("")) {
            TableModel model = TableNow.getModel();
            queryBuilder.append("(");

            Map<String, Object> columnInfo = new HashMap<>();

            JSONObject jsonObject = new JSONObject(FieldsTypeJson);
            JSONArray columns = jsonObject.getJSONArray("columns");

            boolean isNumeric = searchText.matches("-?\\d+");

            for (int i = 0; i < columns.length(); i++) {
                JSONObject column = columns.getJSONObject(i);
                String columnName = column.getString("column_name");
                String dataType = column.getString("data_type");
                columnInfo.put(columnName, dataType);
            }

            for (int col = 0; col < model.getColumnCount(); col++) {
                String columnName = model.getColumnName(col);
                String dataType = (String) columnInfo.get(columnName);
                if (dataType.equals("bool")) {
                    continue;
                } else if (dataType.contains("int") || dataType.contains("serial")
                        || dataType.contains("decimal") || dataType.contains("numeric")) {
                    if (isNumeric) {
                        if (queryBuilder.length() > 1) {
                            queryBuilder.append(" OR ");
                        }
                        queryBuilder.append("CAST(").append(columnName).append(" AS TEXT) LIKE '%").append(searchText).append("%'");
                    }
                } else if (dataType.equals("date") || dataType.equals("timestamp")) {
                    if (isNumeric) {
                        if (queryBuilder.length() > 1) {
                            queryBuilder.append(" OR ");
                        }
                        queryBuilder.append("TO_CHAR(").append(columnName).append(", 'YYYY-MM-DD') LIKE '%").append(searchText).append("%'");
                    }
                } else {
                    if (queryBuilder.length() > 1) {
                        queryBuilder.append(" OR ");
                    }
                    queryBuilder.append(columnName).append(" LIKE '%").append(searchText).append("%'");
                }
            }

            // Завершаем построение строки запроса
            queryBuilder.append(")");
        }

        if (checkboxFilters.getState()) {
            if (!(queryBuilder.toString().equals("") || queryBuilder == null)) {
                queryBuilder.append(" AND ");
            }
            Map<String, String> result = FilterInfo.get(tableName);
            if (result != null) {
                queryBuilder.append(result.get("FilterQuery") == null ? "" : result.get("FilterQuery"));
            }
        }

        String result = queryBuilder.toString();
        SearchValue = result;
        if ("".equals(result)) {
            FillTableWithInfor();
        } else {
            DefaultTableModel model = (DefaultTableModel) TableNow.getModel();
            model.setRowCount(0);
            wasAtTop = true;
            String Jsonresult = client.getDataTable(tableName, 0, result);
            JSONObject jsonResult = new JSONObject(Jsonresult);
            if (jsonResult.has("row_count")) {
                this.numberСolumns = jsonResult.getInt("row_count");
            }
            addRowTable(Jsonresult, false);
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel2 = new javax.swing.JPanel();
        buttonDelete = new java.awt.Button();
        buttonAdd = new java.awt.Button();
        buttonSearch = new java.awt.Button();
        textFieldSearch = new java.awt.TextField();
        buttonFilters = new java.awt.Button();
        checkboxFilters = new java.awt.Checkbox();
        jPanelInfo = new javax.swing.JPanel();
        buttonUpdate = new java.awt.Button();
        labelInfo = new javax.swing.JLabel();
        filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 0));
        label1 = new java.awt.Label();
        jTabbedPane = new javax.swing.JTabbedPane();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("rootFrame");
        setBackground(new java.awt.Color(255, 255, 255));

        buttonDelete.setActionCommand("buttonDelete");
        buttonDelete.setLabel("Удалить");

        buttonAdd.setActionCommand("buttonAdd");
        buttonAdd.setLabel("Добавить");

        buttonSearch.setActionCommand("buttonSearch");
        buttonSearch.setLabel("Найти");

        textFieldSearch.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        textFieldSearch.setFont(new java.awt.Font("Dialog", 0, 20)); // NOI18N

        buttonFilters.setActionCommand("buttonFilters");
        buttonFilters.setLabel("Фильтры");

        checkboxFilters.setLabel("Включить фильтр");

        jPanelInfo.setBorder(javax.swing.BorderFactory.createEtchedBorder(new java.awt.Color(255, 0, 51), null));
        jPanelInfo.setToolTipText("");

        buttonUpdate.setLabel("Обновить");

        javax.swing.GroupLayout jPanelInfoLayout = new javax.swing.GroupLayout(jPanelInfo);
        jPanelInfo.setLayout(jPanelInfoLayout);
        jPanelInfoLayout.setHorizontalGroup(
            jPanelInfoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelInfoLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(labelInfo, javax.swing.GroupLayout.DEFAULT_SIZE, 203, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(buttonUpdate, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        jPanelInfoLayout.setVerticalGroup(
            jPanelInfoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelInfoLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelInfoLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(buttonUpdate, javax.swing.GroupLayout.DEFAULT_SIZE, 34, Short.MAX_VALUE)
                    .addComponent(labelInfo, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );

        buttonUpdate.getAccessibleContext().setAccessibleName("buttonUpdate");

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(buttonFilters, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(checkboxFilters, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 482, Short.MAX_VALUE)
                        .addComponent(filler1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(120, 120, 120)
                        .addComponent(buttonAdd, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(buttonDelete, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(label1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addComponent(textFieldSearch, javax.swing.GroupLayout.PREFERRED_SIZE, 368, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(buttonSearch, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jPanelInfo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(13, 13, 13)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(jPanelInfo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                        .addComponent(label1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(4, 4, 4)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(textFieldSearch, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(buttonSearch, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)))
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(buttonFilters, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(checkboxFilters, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(filler1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(32, 32, 32))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(buttonDelete, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(buttonAdd, javax.swing.GroupLayout.PREFERRED_SIZE, 40, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addContainerGap())))
        );

        buttonFilters.getAccessibleContext().setAccessibleName("buttonFilters");
        jPanelInfo.getAccessibleContext().setAccessibleName("jPanelInfo");

        jTabbedPane.setBackground(new java.awt.Color(0, 0, 0));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jTabbedPane)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jTabbedPane, javax.swing.GroupLayout.DEFAULT_SIZE, 322, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    class OpenFilterButton implements ActionListener {

        @Override
        public void actionPerformed(java.awt.event.ActionEvent evt) {

            String tableName = getActiveTabTitle();

            Map<String, String> result = FilterInfo.get(tableName);
            String FillFilterFields = "";
            if (result != null) {
                FillFilterFields = result.get("FilterQuery") == null ? "" : result.get("FilterQuery");
            }

            FilterForm filterForm = new FilterForm(new InterfaceCallback() {
                @Override
                public void onApply(String query) {
                    Map<String, String> value = new HashMap<>();
                    value.put("FilterQuery", query);
                    FilterInfo.put(tableName, value);

                    if (checkboxFilters.getState()) {
                        SearchQuery(tableName);
                    }
                }

                @Override
                public void onCancel() {
                    // закрытие формы
                }
            }, FieldsTypeJson, FillFilterFields);
        }
    }

    class OpenAddEditButton implements ActionListener {

        @Override
        public void actionPerformed(java.awt.event.ActionEvent evt) {

            DefaultTableModel model = (DefaultTableModel) TableNow.getModel();
            Map<String, String> keyValueMap = new HashMap<>();
            for (int col = 0; col < model.getColumnCount(); col++) {
                String columnName = model.getColumnName(col);
                keyValueMap.put(columnName, "");
            }

            AddEditForm addform = new AddEditForm(new InterfaceCallback() {
                @Override
                public void onApply(String query) {
                    addRowTable(query, false);
                }

                @Override
                public void onCancel() {
                }
            }, client, keyValueMap, FieldsTypeJson, getActiveTabTitle(), "ADD", 0);
        }
    }

    class CheckboxFilter implements ItemListener {

        @Override
        public void itemStateChanged(java.awt.event.ItemEvent evt) {
            SearchQuery(getActiveTabTitle());
        }
    }

    class ButtonSearch implements ActionListener {

        @Override
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            SearchQuery(getActiveTabTitle());
        }
    }
    
    class ButtonUpdate implements ActionListener {

        @Override
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            FillTableWithInfor();
        }
    }

    class DeleteRowTableButton implements ActionListener {

        @Override
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            int[] selected = TableNow.getSelectedRows();
            String[] a = Arrays.stream(selected)
                    .map(i -> i + deleteRow)
                    .mapToObj(String::valueOf)
                    .toArray(String[]::new);
            String QuerySelected = Arrays.toString(a);

            if ("true".equals(client.deleteRow(getActiveTabTitle(), QuerySelected))) {

                DefaultTableModel model = (DefaultTableModel) TableNow.getModel();
                for (int i = selected.length - 1; i >= 0; i--) {
                    model.removeRow(selected[i]);
                }
            }
        }
    }

    class PanelChanged implements ChangeListener {

        @Override
        public void stateChanged(ChangeEvent evt) {
            // очистка предыдущей таблицы
            if (TableNow != null) {
                DefaultTableModel model = (DefaultTableModel) TableNow.getModel();
                model.setRowCount(0);
                model.setColumnCount(0);
            }
            jPanelInfo.setVisible(false);
            TableNow = getTableByTabName(getActiveTabTitle());
            FillTableWithInfor();
        }
    }

    public JTable getTableByTabName(String tabName) {
        int tabIndex = jTabbedPane.indexOfTab(tabName);
        if (tabIndex == -1) {
            return null; // Таблица не найдена
        }

        Component component = jTabbedPane.getComponentAt(tabIndex);
        if (component instanceof JPanel) {
            JPanel panel = (JPanel) component;
            for (Component comp : panel.getComponents()) {
                if (comp instanceof JScrollPane) {
                    JScrollPane scrollPane = (JScrollPane) comp;
                    JViewport viewport = scrollPane.getViewport();
                    Component view = viewport.getView();
                    if (view instanceof JTable) {
                        return (JTable) view;
                    }
                }
            }
        }

        return null; // Таблица не найдена
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(main_form.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(main_form.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(main_form.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(main_form.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new main_form().setVisible(true);

            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private java.awt.Button buttonAdd;
    private java.awt.Button buttonDelete;
    private java.awt.Button buttonFilters;
    private java.awt.Button buttonSearch;
    private java.awt.Button buttonUpdate;
    private java.awt.Checkbox checkboxFilters;
    private javax.swing.Box.Filler filler1;
    private javax.swing.JPanel jPanel2;
    protected static javax.swing.JPanel jPanelInfo;
    private static javax.swing.JTabbedPane jTabbedPane;
    private java.awt.Label label1;
    protected static javax.swing.JLabel labelInfo;
    private java.awt.TextField textFieldSearch;
    // End of variables declaration//GEN-END:variables
}
