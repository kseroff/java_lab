
import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.json.JSONObject;

public class Request {

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private BlockingQueue<String> responseQueue;

    public Request(String address, int port) {
        try {
            socket = new Socket(address, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            responseQueue = new LinkedBlockingQueue<>();
            System.out.println("Connected to server");

            // прослушка с сервера
            new Thread(this::listenForMessages).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void listenForMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                if (message.startsWith("{")) {
                    JSONObject jsonMessage = new JSONObject(message);
                    if (jsonMessage.has("action")) {
                        String action = jsonMessage.getString("action");
                        if (action.equals("UpdateTable")) {
                            String tableName = jsonMessage.getString("tableName");                            
                            if(tableName.equals(main_form.getActiveTabTitle())){
                                main_form.labelInfo.setText("Таблица не актуальна");
                                main_form.jPanelInfo.setVisible(true);
                            }
                        } 
                        // else if() тут могут быть другие оповещения с сервера
                        else {
                            responseQueue.put(message);
                        }
                    } else {
                        responseQueue.put(message);
                    }
                } else {
                    responseQueue.put(message);
                }
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public String sendRequest(JSONObject request) {
        try {
            out.println(request.toString());
            return responseQueue.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getTableNames() {
        JSONObject request = new JSONObject();
        request.put("action", "GetTableNames");
        return sendRequest(request);
    }

    public String getTypeTable(String tableName) {
        JSONObject request = new JSONObject();
        request.put("action", "GetTypeTable");
        request.put("tableName", tableName);
        return sendRequest(request);
    }

    public String getDataTable(String tableName, int offset, String query) {
        JSONObject request = new JSONObject();
        request.put("action", "GetDataTable");
        request.put("tableName", tableName);
        request.put("offset", offset);
        request.put("query", query);
        return sendRequest(request);
    }

    public String addNewRow(String tableName, String row) {
        JSONObject request = new JSONObject();
        request.put("action", "AddNewRow");
        request.put("tableName", tableName);
        request.put("row", row);
        return sendRequest(request);
    }

    public String deleteRow(String tableName, String rows) {
        JSONObject request = new JSONObject();
        request.put("action", "DeleteRow");
        request.put("tableName", tableName);
        request.put("rows", rows);
        return sendRequest(request);
    }

    public String updateRow(String tableName, String newRowJson, int row) {
        JSONObject request = new JSONObject();
        request.put("action", "UpdateRow");
        request.put("tableName", tableName);
        request.put("NewRows", newRowJson);
        request.put("EditRow", row);
        return sendRequest(request);
    }

    public void close() {
        try {
            JSONObject request = new JSONObject();
            request.put("action", "disconnect");
            out.println(request.toString());

            in.close();
            out.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
