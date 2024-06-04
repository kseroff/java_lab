import org.json.JSONArray;
import org.json.JSONObject;
import com.toedter.calendar.JDateChooser;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Kseroff
 */
public final class FilterForm extends JFrame {

    private List<JPanel> filterPanels = new ArrayList<>();
    private InterfaceCallback callback;

    public FilterForm(InterfaceCallback callback, String json, String FillFilterFields) {
        this.callback = callback;

        JSONObject jsonObj = new JSONObject(json);
        JSONArray jsonArray = jsonObj.getJSONArray("columns");

        setTitle("Filter Form");
        setSize(600, 150);
        setResizable(false);
        setLayout(new BorderLayout());
        
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        add(scrollPane, BorderLayout.CENTER);

        for (int i = 0; i < jsonArray.length(); i++) {
            JPanel MainFiledPanel = new JPanel(new GridLayout(1, 2));
            mainPanel.add(MainFiledPanel);

            JSONObject jsonObject = jsonArray.getJSONObject(i);
            String columnName = jsonObject.getString("column_name");
            String dataType = jsonObject.getString("data_type");

            JPanel filterPanel = new JPanel(new GridLayout(0, 1));
            filterPanels.add(filterPanel);
            JPanel checkBoxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JCheckBox checkBox = new JCheckBox(columnName);
            checkBoxPanel.add(checkBox, BorderLayout.WEST);

            if (!dataType.equals("bool")) {
                JButton addButton = new JButton("+");
                addButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        addFilterField(filterPanel, dataType, MainFiledPanel);
                        revalidate();
                        repaint();
                    }
                });
                checkBoxPanel.add(addButton, BorderLayout.EAST);
                addButton.doClick();
            } else {
                JPanel fieldPanel = new JPanel();
                JRadioButton yesRadioButton = new JRadioButton("Yes");
                JRadioButton noRadioButton = new JRadioButton("No");
                yesRadioButton.doClick();
                fieldPanel.add(yesRadioButton);
                fieldPanel.add(noRadioButton);
                filterPanel.add(fieldPanel, BorderLayout.WEST);
            }
            MainFiledPanel.add(checkBoxPanel);
            MainFiledPanel.add(filterPanel);
        }
        

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2));
        JButton applyButton = new JButton("Применить");
        applyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String query = buildQuery();
                callback.onApply(query);
                dispose();
            }
        });
        buttonPanel.add(applyButton);

        JButton exitButton = new JButton("Отмена");
        exitButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                callback.onCancel();
                dispose();
            }
        });
        buttonPanel.add(exitButton);
        add(buttonPanel, BorderLayout.SOUTH);
        
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setVisible(true);
        
        createFilterFieldsFromConditions(FillFilterFields);
    }

    private void addFilterField(JPanel filterPanel, String dataType, JPanel MainFiledPanel) {
        JPanel fieldPanel = new JPanel();
        fieldPanel.setLayout(new BoxLayout(fieldPanel, BoxLayout.X_AXIS));

        if (dataType.contains("int") || dataType.contains("serial")
                || dataType.contains("decimal") || dataType.contains("numeric")) {
            JTextField minValueField = new JTextField();
            minValueField.setPreferredSize(new Dimension(100, 30));            
            minValueField.addKeyListener(new KeyAdapter() {
                    @Override
                    public void keyTyped(KeyEvent e) {
                        char c = e.getKeyChar();
                        JTextField textField = (JTextField) e.getComponent();
                        String text = textField.getText();
                        if (!Character.isDigit(c) && c != KeyEvent.VK_BACK_SPACE && c != KeyEvent.VK_DELETE && c != '.' 
                            || (c == '.' && text.contains("."))) {
                            e.consume();
                        }
                    }
                });
            JTextField maxValueField = new JTextField();
            maxValueField.setPreferredSize(new Dimension(100, 30));
            maxValueField.addKeyListener(new KeyAdapter() {
                    @Override
                    public void keyTyped(KeyEvent e) {
                        char c = e.getKeyChar();
                        JTextField textField = (JTextField) e.getComponent();
                        String text = textField.getText();
                        if (!Character.isDigit(c) && c != KeyEvent.VK_BACK_SPACE && c != KeyEvent.VK_DELETE && c != '.' 
                            || (c == '.' && text.contains("."))) {
                            e.consume();
                        }
                    }
                });
            
            fieldPanel.add(minValueField);
            fieldPanel.add(maxValueField);
        } else if (dataType.equals("date") || dataType.equals("timestamp")) {
            JDateChooser minDateChooser = new JDateChooser();
            minDateChooser.setPreferredSize(new Dimension(100, 30));
            JDateChooser maxDateChooser = new JDateChooser();
            maxDateChooser.setPreferredSize(new Dimension(100, 30));
            fieldPanel.add(minDateChooser);
            fieldPanel.add(maxDateChooser);
        } else {
            JTextField textField = new JTextField();
            textField.setPreferredSize(new Dimension(200, 30));
            fieldPanel.add(textField);
        }

        fieldPanel.setPreferredSize(new Dimension(fieldPanel.getWidth(), fieldPanel.getHeight() + 35));
        MainFiledPanel.setPreferredSize(new Dimension(MainFiledPanel.getWidth(), MainFiledPanel.getHeight() + fieldPanel.getHeight()));
        JButton removeButton = new JButton("-");
        removeButton.addActionListener(new ActionListener() {
            @Override
           public void actionPerformed(ActionEvent e) {
                if (filterPanel.getComponentCount() > 1) {
                    MainFiledPanel.setPreferredSize(new Dimension(MainFiledPanel.getWidth(), MainFiledPanel.getHeight() - (fieldPanel.getHeight() * 2)));
                    setSize(getWidth(), getHeight() - fieldPanel.getHeight());
                    filterPanel.remove(fieldPanel);
                    filterPanel.revalidate();
                    filterPanel.repaint();
                }
                else{
                    Component[] components = fieldPanel.getComponents();
                    for (Component component : components) {
                         if (component instanceof JTextField) {
                            JTextField textField = (JTextField) component;
                            textField.setText("");
                        } else if (component instanceof JDateChooser) {
                            JDateChooser dateChooser = (JDateChooser) component;
                             dateChooser.setDate(null);
                        }
                    }               
                }
            }
        });

        fieldPanel.add(removeButton);
        filterPanel.add(fieldPanel);
        setSize(getWidth(), getHeight() + 35);
        filterPanel.revalidate();
        filterPanel.repaint();
    }

   private String buildQuery() {
    StringBuilder queryBuilder = new StringBuilder();
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    boolean firstCondition = true;

    for (int i = 0; i < filterPanels.size(); i++) {
        JPanel filterPanel = filterPanels.get(i);
        JPanel checkBoxPanel = (JPanel) filterPanel.getParent().getComponent(0);
        JCheckBox checkBox = (JCheckBox) checkBoxPanel.getComponent(0);
        if (checkBox.isSelected()) {
            Component[] components = filterPanel.getComponents();
            StringBuilder condition = new StringBuilder();
            boolean isFirstFieldInPanel = true;
            for (Component component : components) {
                if (component instanceof JPanel) {
                    JPanel fieldPanel = (JPanel) component;
                    Component[] fields = fieldPanel.getComponents();
                    String minValue = null;
                    String maxValue = null;
                    boolean isRange = false;
                    int textFieldCount = 0;
                    for (Component field : fields) {
                        if (field instanceof JTextField) {
                            textFieldCount++;
                        }
                    }
                    for (Component field : fields) {
                        if (field instanceof JTextField) {
                            JTextField textField = (JTextField) field;
                            String text = textField.getText();
                            if (!text.isEmpty()) {
                                if (textFieldCount == 2 ) {
                                    if (minValue == null) {
                                        minValue = text;
                                    } else {
                                        maxValue = text;
                                        isRange = true;
                                    }
                                } else if (textFieldCount == 1) {
                                    minValue = text;
                                }
                               
                            }
                        } else if (field instanceof JDateChooser) {
                            JDateChooser dateChooser = (JDateChooser) field;
                            if (dateChooser.getDate() != null) {
                                if (minValue == null) {
                                    minValue = dateFormat.format(dateChooser.getDate());
                                } else {
                                    maxValue = dateFormat.format(dateChooser.getDate());
                                    isRange = true;
                                }
                            }
                        } else if (field instanceof JRadioButton) {
                            JRadioButton radioButton = (JRadioButton) field;
                            if (radioButton.isSelected()) {
                                minValue = radioButton.getText();
                            }
                        }
                    }
                    
                    if (minValue != null && maxValue != null) {
                        try {
                            double minVal = Double.parseDouble(minValue);
                            double maxVal = Double.parseDouble(maxValue);
                            if (minVal > maxVal) {
                                String temp = minValue;
                                minValue = maxValue;
                                maxValue = temp;
                            }
                        } catch (NumberFormatException e) {
                            e.printStackTrace();
                        }
                    }
                    
                    if (minValue != null) {
                        if (!isFirstFieldInPanel) {
                            condition.append(" OR ");
                        } else {
                            condition.append("(");
                            isFirstFieldInPanel = false;
                        }
                        if (isRange && !minValue.equals(maxValue)) {
                            condition.append(checkBox.getText()).append(" BETWEEN '").append(minValue).append("' AND '").append(maxValue).append("'");
                        } else {
                            condition.append(checkBox.getText()).append(" = '").append(minValue).append("'");
                        }
                    }
                }
            }
            if (condition.length() > 0) {
                condition.append(")");
                if (!firstCondition && queryBuilder.indexOf("AND") != queryBuilder.length() - 4) {
                    queryBuilder.append(" AND ");
                }
                queryBuilder.append(condition);
                firstCondition = false;
            }
        }
    }

    return queryBuilder.toString();
}

   public void createFilterFieldsFromConditions(String conditions) {
    String conditionRegex = "\\((.*?)\\)";
    Pattern conditionPattern = Pattern.compile(conditionRegex);
    Matcher conditionMatcher = conditionPattern.matcher(conditions);
    int i = 0;
    
        while (conditionMatcher.find()) {
            String conditionGroup = conditionMatcher.group(1); //группа
            Pattern fieldPattern = Pattern.compile("^\\s*([^\\s=]+)\\s*");
            Matcher fieldMatcher = fieldPattern.matcher(conditionGroup);
            
            String[] conditionsArray = conditionGroup.split("\\s*(?i)OR\\s*");
            List<String> cleanedConditions = new ArrayList<>();
            for (String c : conditionsArray) {
                cleanedConditions.add(c.trim()); // условия
            }
            
                if (fieldMatcher.find()){
                    String fieldName = fieldMatcher.group(1); //название
  
                    for (int j = i; j < filterPanels.size(); j++) {
                        JPanel filterPanel = filterPanels.get(j);
                        JPanel checkBoxPanel = (JPanel) filterPanel.getParent().getComponent(0);
                        // включение чекбокса
                        JCheckBox checkBox = (JCheckBox) checkBoxPanel.getComponent(0);
                        if(checkBox.getText().equals(fieldName)){
                            checkBox.setSelected(true);
                           
                            
                           for (int z = 0; z < cleanedConditions.size(); z++) {
                               
                                if(z>=1){
                                    JButton addButton = null;
                                    for (Component component : checkBoxPanel.getComponents()) {
                                        if (component instanceof JButton) {
                                             addButton = (JButton) component;
                                             break;
                                        }
                                    }
                                    if (addButton != null) addButton.doClick();
                                }
                               
                                Pattern pattern = Pattern.compile("^(.*?)\\s+(BETWEEN|=)\\s+'(.*?)'\\s+AND\\s+'(.*?)'$");
                                Matcher matcher = pattern.matcher(cleanedConditions.get(z));
                                
                                Component component = filterPanel.getComponents()[z];
                                Component[] fields = null;
                                if (component instanceof JPanel) {
                                    JPanel fieldPanel = (JPanel) component;
                                    fields = fieldPanel.getComponents();
                                }

                                if (matcher.find()) {
                                    String minValue = matcher.group(3);
                                    String maxValue = matcher.group(4);
                                   
                                    if (fields[0] instanceof JTextField){
                                        JTextField textChooser1 = (JTextField) fields[0];
                                        JTextField textChooser2 = (JTextField) fields[1];
                                        textChooser1.setText(minValue);
                                        textChooser2.setText(maxValue);
                                    } else if (fields[0] instanceof JDateChooser) {
                                        JDateChooser dateChooser1 = (JDateChooser) fields[0];
                                        JDateChooser dateChooser2 = (JDateChooser) fields[1];
                                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                                        try {
                                            Date date1 = sdf.parse(minValue);
                                            Date date2 = sdf.parse(maxValue);
                                            dateChooser1.setDate(date1);
                                            dateChooser2.setDate(date2);
                                        } catch (ParseException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    
                                } else { // Если не удалось разобрать по шаблону, значит оператор = и только одно значение
                                    pattern = Pattern.compile("^(.*?)\\s+=\\s+'(.*?)'$");
                                    matcher = pattern.matcher(cleanedConditions.get(z));

                                    if (matcher.find()) {
                                        String value = matcher.group(2);

                                    if (fields[0] instanceof JTextField){
                                        JTextField textChooser = (JTextField) fields[0];
                                         textChooser.setText(value);
                                    } else if (fields[0] instanceof JRadioButton){
                                        JRadioButton yesRadioButton = (JRadioButton) fields[0];
                                        JRadioButton noRadioButton = (JRadioButton) fields[1];
                                        if (value.equalsIgnoreCase("Yes")) {
                                            yesRadioButton.setSelected(true);
                                        } else {
                                            noRadioButton.setSelected(true);
                                       }
                                    }
                                        
                                    }
                                }
                                
                            }
                                  
                            break;
                        }
                    }
                
                
                }
            
            i++;
        }
    }

}
