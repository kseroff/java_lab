package postgres;

import java.io.*;
import static java.lang.System.in;
import java.net.*;
import java.sql.*;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Postgres {

    public static class DataBase {

        private Connection connection;
        private String url = "jdbc:postgresql://localhost:5432/project2?characterEncoding=UTF-8";
        private String user = "kseroff";
        private String password = "1111";
        private int limit;

        private List<ClientHandler> onlineUsers;

        public DataBase() throws SQLException {
            onlineUsers = new ArrayList<>();
            limit = 50;
            try {
                connection = DriverManager.getConnection(url, user, password);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        public synchronized void addOnlineUser(ClientHandler user) {
            onlineUsers.add(user);
        }

        public synchronized void removeOnlineUser(ClientHandler user) {
            onlineUsers.remove(user);
        }

        public String getTableInfoAsJson(String tableName) {
            JSONObject tableInfo = new JSONObject();
            JSONArray jsonArray = new JSONArray();
            try {
                Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);

                // Получаем количество строк в таблице
                ResultSet countResultSet = statement.executeQuery("SELECT COUNT(*) AS row_count FROM " + tableName);
                if (countResultSet.next()) {
                    int rowCount = countResultSet.getInt("row_count");
                    tableInfo.put("number_rows", rowCount);
                }

                // Получаем информацию о колонках
                ResultSet resultSet = statement.executeQuery("SELECT * FROM " + tableName + " LIMIT 1"); // Ограничиваем запрос одной строкой для получения метаданных
                ResultSetMetaData metaData = resultSet.getMetaData();
                int columnCount = metaData.getColumnCount();
                tableInfo.put("number_columns", columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("column_name", metaData.getColumnName(i));
                    jsonObject.put("data_type", metaData.getColumnTypeName(i));
                    jsonArray.put(jsonObject);
                }
                tableInfo.put("columns", jsonArray);
            } catch (SQLException | JSONException e) {
                return e.getMessage();
            }
            return tableInfo.toString();
        }

        public String getDataTable(String tableName, int offset, String query) throws SQLException {
            JSONObject tableData = new JSONObject();
            JSONArray columns = new JSONArray();
            JSONArray rows = new JSONArray();
            int rowCount = 0;

            String sqlQuery = "SELECT * FROM " + tableName;
            String countQuery = "SELECT COUNT(*) AS row_count FROM " + tableName;

            if (query != null && !query.trim().isEmpty()) {
                sqlQuery += " WHERE " + query;
                countQuery += " WHERE " + query;
            }

            sqlQuery += " LIMIT ? OFFSET ?";

            try (PreparedStatement countStmt = connection.prepareStatement(countQuery);
                    PreparedStatement dataStmt = connection.prepareStatement(sqlQuery)) {

                if (query != null && !query.trim().isEmpty()) {
                    try (ResultSet countResultSet = countStmt.executeQuery()) {
                        if (countResultSet.next()) {
                            rowCount = countResultSet.getInt("row_count");
                        }
                    }
                }

                dataStmt.setInt(1, limit);
                dataStmt.setInt(2, offset);

                try (ResultSet rs = dataStmt.executeQuery()) {
                    ResultSetMetaData rsmd = rs.getMetaData();
                    int columnCount = rsmd.getColumnCount();

                    for (int i = 1; i <= columnCount; i++) {
                        columns.put(rsmd.getColumnName(i));
                    }
                    tableData.put("columns", columns);

                    while (rs.next()) {
                        JSONObject row = new JSONObject();
                        for (int i = 1; i <= columnCount; i++) {
                            row.put(rsmd.getColumnName(i), rs.getObject(i));
                        }
                        rows.put(row);
                    }
                    tableData.put("rows", rows);
                }
            } catch (SQLException e) {
                return e.getMessage();
            }

            if (query != null && !query.trim().isEmpty()) {
                tableData.put("row_count", rowCount);
            }

            return tableData.toString();
        }

        public String addRowToTable(String tableName, String rowJson) throws SQLException {
            try {
                JSONObject rowObject = new JSONObject(rowJson);
                JSONArray columns = rowObject.getJSONArray("columns");
                JSONArray rows = rowObject.getJSONArray("rows");

                if (rows.length() == 0) {
                    throw new IllegalArgumentException("No rows provided");
                }

                JSONObject row = rows.getJSONObject(0);

                StringBuilder columnNames = new StringBuilder();
                StringBuilder columnValues = new StringBuilder();

                for (int i = 0; i < columns.length(); i++) {
                    columnNames.append(columns.getString(i));
                    columnValues.append("?");
                    if (i < columns.length() - 1) {
                        columnNames.append(", ");
                        columnValues.append(", ");
                    }
                }

                String query = "INSERT INTO " + tableName + " (" + columnNames.toString() + ") VALUES (" + columnValues.toString() + ")";
                PreparedStatement preparedStatement = connection.prepareStatement(query);

                // Получение информации о типах столбцов
                Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT " + columnNames.toString() + " FROM " + tableName + " LIMIT 1");
                ResultSetMetaData metaData = rs.getMetaData();

                for (int i = 0; i < columns.length(); i++) {
                    String columnName = columns.getString(i);
                    Object value = row.get(columnName);
                    int columnType = metaData.getColumnType(i + 1);

                    switch (columnType) {
                        case Types.BIGINT:
                            preparedStatement.setLong(i + 1, row.getLong(columnName));
                            break;
                        case Types.INTEGER:
                            preparedStatement.setInt(i + 1, row.getInt(columnName));
                            break;
                        case Types.BOOLEAN:
                            preparedStatement.setBoolean(i + 1, row.getBoolean(columnName));
                            break;
                        case Types.FLOAT:
                            preparedStatement.setFloat(i + 1, (float) row.getDouble(columnName));
                            break;
                        case Types.DOUBLE:
                            preparedStatement.setDouble(i + 1, row.getDouble(columnName));
                            break;
                        case Types.VARCHAR:
                        case Types.CHAR:
                        case Types.LONGVARCHAR:
                        case Types.LONGNVARCHAR:
                            preparedStatement.setString(i + 1, row.getString(columnName));
                            break;
                        case Types.DATE:
                            preparedStatement.setDate(i + 1, java.sql.Date.valueOf(row.getString(columnName)));
                            break;
                        case Types.TIMESTAMP:
                            preparedStatement.setTimestamp(i + 1, Timestamp.valueOf(row.getString(columnName)));
                            break;
                        default:
                            preparedStatement.setObject(i + 1, value);
                            break;
                    }
                }

                preparedStatement.executeUpdate();
                preparedStatement.close();
                rs.close();
                stmt.close();

                return "true";

            } catch (SQLException e) {
                return e.getLocalizedMessage();
            }
        }

        public String deleteRowsFromTable(String tableName, String rowNumbersStr) {
            try {
                String[] rowNumbersArr = rowNumbersStr.replaceAll("[\\[\\]]", "").split(",\\s*");
                int[] rowNumbers = new int[rowNumbersArr.length];
                for (int i = 0; i < rowNumbersArr.length; i++) {
                    rowNumbers[i] = Integer.parseInt(rowNumbersArr[i]);
                }

                Statement statement = connection.createStatement();
                for (int i = rowNumbers.length - 1; i >= 0; i--) {
                    int rowNum = rowNumbers[i];
                    String sql = "DELETE FROM " + tableName + " WHERE ctid = (SELECT ctid FROM " + tableName + " ORDER BY ctid OFFSET " + rowNum + " LIMIT 1)";
                    statement.executeUpdate(sql);
                }

                statement.close();
                return "true";
            } catch (SQLException e) {
                return e.getMessage();
            }
        }

        public String updateRow(String tableName, String newRowJson, int EditRow) {
            try {
                JSONArray jsonArray = new JSONArray(newRowJson);
                JSONObject jsonObject = jsonArray.getJSONObject(0);
                Statement statement = connection.createStatement();

                // Формирование динамического SQL-запроса для обновления
                StringBuilder sql = new StringBuilder("UPDATE ").append(tableName).append(" SET ");
                boolean first = true;
                for (Object key : jsonObject.keySet()) {
                    if (!first) {
                        sql.append(", ");
                    }
                    sql.append(key).append(" = '").append(jsonObject.getString(key.toString())).append("'");
                    first = false;
                }
                sql.append(" WHERE ctid = (SELECT ctid FROM ").append(tableName)
                        .append(" ORDER BY ctid OFFSET ").append(EditRow).append(" LIMIT 1)");

                String sqlString = sql.toString();
                statement.executeUpdate(sqlString);
                return "true";
            } catch (SQLException e) {
                return e.getMessage();
            }
        }

        public String getAllTables() {
            JSONArray jsonArray = new JSONArray();
            try {
                DatabaseMetaData metaData = connection.getMetaData();
                ResultSet tables = metaData.getTables(null, null, "%", new String[]{"TABLE"});
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    jsonArray.put(tableName);
                }
                tables.close();
            } catch (SQLException e) {
                return e.getMessage();
            }
            return jsonArray.toString();
        }
    }

    public static class ClientHandler extends Thread {

        private final Socket clientSocket;
        private final DataBase database;
        private static final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
        private PrintWriter out;

        public ClientHandler(Socket socket, DataBase database) {
            this.clientSocket = socket;
            this.database = database;
            this.database.addOnlineUser(this);
            clients.add(this);
        }

        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
                out = new PrintWriter(clientSocket.getOutputStream(), true);

                String request;
                while ((request = in.readLine()) != null) {
                    JSONObject jsonRequest = new JSONObject(request);
                    String action = jsonRequest.getString("action");

                    switch (action) {
                        case "GetTableNames":
                            out.println(database.getAllTables());
                            break;
                        case "GetTypeTable":
                            String tableName = jsonRequest.getString("tableName");
                            out.println(database.getTableInfoAsJson(tableName));
                            break;
                        case "GetDataTable":
                            tableName = jsonRequest.getString("tableName");
                            int offset = jsonRequest.getInt("offset");
                            String query = jsonRequest.optString("query", "");
                            out.println(database.getDataTable(tableName, offset, query));
                            break;
                        case "AddNewRow":
                            tableName = jsonRequest.getString("tableName");
                            String row = jsonRequest.getString("row");
                            String addResult = database.addRowToTable(tableName, row);
                            if ("true".equals(addResult)) {
                                out.println("true");
                                notifyClients("UpdateTable", tableName);
                            } else {
                                out.println("Error: " + addResult.replace("\n", ""));
                            }
                            break;
                        case "DeleteRow":
                            tableName = jsonRequest.getString("tableName");
                            String rowsToDelete = jsonRequest.getString("rows");
                            String deleteResult = database.deleteRowsFromTable(tableName, rowsToDelete);
                            if ("true".equals(deleteResult)) {
                                out.println("true");
                                notifyClients("UpdateTable", tableName);
                            } else {
                                out.println("Error: " + deleteResult.replace("\n", ""));
                            }
                            break;
                        case "UpdateRow":
                            tableName = jsonRequest.getString("tableName");
                            String NewRows = jsonRequest.getString("NewRows");
                            int EditRow = jsonRequest.getInt("EditRow");
                            String UpfateResult = database.updateRow(tableName, NewRows, EditRow);
                            if ("true".equals(UpfateResult)) {
                                out.println("true");
                                notifyClients("UpdateTable", tableName);
                            } else {
                                out.println("Error: " + UpfateResult.replace("\n", ""));
                            }
                            break;
                        case "disconnect":
                            disconnect();
                            return;
                        default:
                            out.println("Unknown action: " + action);
                    }
                }
            } catch (IOException | SQLException e) {
                e.printStackTrace();
            } finally {
                database.removeOnlineUser(this);
                clients.remove(this);
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void disconnect() {
            try {
                in.close();
                out.close();
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void notifyClients(String action, String tableName) {
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    if (client != this) {
                        client.sendMessage(action, tableName);
                    }
                }
            }
        }

        private void sendMessage(String action, String tableName) {
            if (out != null) {
                JSONObject jsonMessage = new JSONObject();
                jsonMessage.put("action", action);
                jsonMessage.put("tableName", tableName);
                out.println(jsonMessage.toString());
            }
        }
    }

    public static void main(String[] args) {
        int port = 12345; // Укажите нужный порт
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            InetAddress inetAddress = InetAddress.getLocalHost();
            System.out.println("Server started...");
            System.out.println("IP Address: " + inetAddress.getHostAddress());
            System.out.println("Зort " + port);
            System.out.println("Host Name: " + inetAddress.getHostName());
            DataBase database = new DataBase();

            while (true) {
                Socket clientSocket = serverSocket.accept();

                ClientHandler clientHandler = new ClientHandler(clientSocket, database);
                clientHandler.start();
            }
        } catch (IOException | SQLException e) {
            e.printStackTrace();
        }
    }
}
