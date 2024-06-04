
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import com.toedter.calendar.JDateChooser;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author Kseroff
 */
public final class AddEditForm {

    public AddEditForm(InterfaceCallback callback, Request client, Map<String, String> keyValueMap,
            String FieldsTypeJson, String tableName, String action, int editRow) {
        // Создание главного окна
        JSONObject jsonObj = new JSONObject(FieldsTypeJson);
        JSONArray jsonArray = jsonObj.getJSONArray("columns");
        JFrame frame = new JFrame("Dynamic Form");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setResizable(false); // Запрет изменения размера окна
        frame.setLayout(new BorderLayout());

        // Панель для текстовых полей
        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        Map<JLabel, JComponent> labelFieldMap = new HashMap<>();

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 5);

        JSONArray columnsArray = new JSONArray();
        int row = 0;
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject fieldObject = jsonArray.getJSONObject(i);
            String columnName = fieldObject.getString("column_name");
            columnsArray.put(columnName);
            String dataType = fieldObject.getString("data_type");

            JLabel label = new JLabel(columnName);
            JComponent fieldComponent;

            if (dataType.contains("date") || dataType.contains("timestamp")) {
                JDateChooser dateChooser = new JDateChooser();
                String value = keyValueMap.get(columnName);
                if (value != null && !value.isEmpty()) {
                    try {
                        Date date = new SimpleDateFormat("yyyy-MM-dd").parse(value);
                        dateChooser.setDate(date);
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
                fieldComponent = dateChooser;
            } else if (dataType.contains("bool")) {
                JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
                JRadioButton yesRadioButton = new JRadioButton("Yes");
                JRadioButton noRadioButton = new JRadioButton("No");
                ButtonGroup group = new ButtonGroup();
                group.add(yesRadioButton);
                group.add(noRadioButton);
                radioPanel.add(yesRadioButton);
                radioPanel.add(noRadioButton);
                String value = keyValueMap.get(columnName);
                if (value != null) {
                    if (value.equalsIgnoreCase("true")) {
                        yesRadioButton.setSelected(true);
                    } else if (value.equalsIgnoreCase("false")) {
                        noRadioButton.setSelected(true);
                    }
                }
                fieldComponent = radioPanel;
            } else if (dataType.contains("int") || dataType.contains("serial") || dataType.contains("decimal") || dataType.contains("numeric")) {
                JTextField textField = new JTextField(20);
                textField.addKeyListener(new KeyAdapter() {
                    @Override
                    public void keyTyped(KeyEvent e) {
                        char c = e.getKeyChar();
                        if (!Character.isDigit(c) && c != KeyEvent.VK_BACK_SPACE && c != KeyEvent.VK_DELETE) {
                            e.consume();
                        }
                    }
                });
                String value = keyValueMap.get(columnName);
                if (value != null) {
                    textField.setText(value);
                }
                fieldComponent = textField;
            } else {
                JTextField textField = new JTextField(20);
                String value = keyValueMap.get(columnName);
                if (value != null) {
                    textField.setText(value);
                }
                fieldComponent = textField;
            }

            labelFieldMap.put(label, fieldComponent);

            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.weightx = 0.1;
            gbc.anchor = GridBagConstraints.WEST;
            rightPanel.add(label, gbc);

            gbc.gridx = 1;
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.EAST;
            rightPanel.add(fieldComponent, gbc);

            row++;
        }

        frame.add(rightPanel, BorderLayout.CENTER);
        // Панель для кнопок "Сохранить" и "Отмена"
        JPanel buttonPanelBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        // Кнопка "Сохранить"
        JButton saveButton = new JButton("Сохранить");
        saveButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Map<String, Object> savedData = new HashMap<>();
                for (Map.Entry<JLabel, JComponent> entry : labelFieldMap.entrySet()) {
                    String key = entry.getKey().getText();
                    JComponent component = entry.getValue();

                    if (component instanceof JTextField) {
                        savedData.put(key, ((JTextField) component).getText());
                    } else if (component instanceof JDateChooser) {
                        Date date = ((JDateChooser) component).getDate();
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                        savedData.put(key, sdf.format(date));
                    } else if (component instanceof JPanel) {
                        for (Component comp : ((JPanel) component).getComponents()) {
                            if (comp instanceof JRadioButton && ((JRadioButton) comp).isSelected()) {
                                savedData.put(key, ((JRadioButton) comp).getText().equals("Yes"));
                            }
                        }
                    }
                }

                // Создание JSON структуры
                JSONObject jsonObject = new JSONObject();
                JSONArray rowsArray = new JSONArray();
                JSONObject rowObject = new JSONObject();

                for (Map.Entry<String, Object> entry : savedData.entrySet()) {
                        rowObject.put(entry.getKey(), entry.getValue());
                }
                rowsArray.put(rowObject);

                jsonObject.put("columns", columnsArray);
                jsonObject.put("rows", rowsArray);

                String jsonData = jsonObject.toString();

                String req;
                if ("ADD".equals(action)) {
                    req = main_form.client.addNewRow(tableName, jsonData);
                } else {
                    JSONObject NewjsonArray = new JSONObject(jsonData);
                    JSONArray NewrowsJS = NewjsonArray.getJSONArray("rows");

                    req = main_form.client.updateRow(tableName, NewrowsJS.toString(), editRow);
                }
                
                if ("true".equals(req)) {
                    callback.onApply(jsonData);
                    frame.dispose();
                } else {
                    JOptionPane.showMessageDialog(null, req, "Error", JOptionPane.WARNING_MESSAGE);
                }
            }
        });
        buttonPanelBottom.add(saveButton);

        // Кнопка "Отмена"
        JButton cancelButton = new JButton("Отмена");
        cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                callback.onCancel();
                frame.dispose();
            }
        });
        buttonPanelBottom.add(cancelButton);

        frame.add(buttonPanelBottom, BorderLayout.SOUTH);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

}
