package com.example;

import com.fasterxml.jackson.databind.util.JSONPObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AggregationServer {
    private static final int DEFAULT_PORT = 4567;
    private final LamportClock Clock = new LamportClock();
    private static final ConcurrentHashMap<String, WeatherEntry> DataStore = new ConcurrentHashMap<>();

    public static void main( String[] args ) throws IOException {
        int port = (args.length) > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        new AggregationServer().Start(port);
    }

    public void Start(int port) throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);

        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            DataStore.entrySet().removeIf(e -> now - e.getValue().lastUpdated > 30000);
        }, 5, 5, TimeUnit.SECONDS);

        while (true) {
            Socket socket = serverSocket.accept();
            new Thread(new ClientHandler(socket)).start();
        }
    }

    public static class ClientHandler implements Runnable {
        private final Socket socket;

        public ClientHandler(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            try(BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream())); PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                String requestLine = in.readLine();
                if(requestLine == null) return;
                System.out.println("Request:" + requestLine);

                String[] parts = requestLine.split(" ");
                if(parts.length < 3) {
                    SendResponse(out, 400, "Bad Request");
                    return;
                }

                String method = parts[0];
                if(method.equals("PUT")) {
                    HandlePUT(in, out);
                } else if(method.equals("GET")) {
                    HandleGET(out);
                } else {
                    SendResponse(out, 400, "Bad Request");
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void HandlePUT(BufferedReader in, PrintWriter out) throws IOException {
        String line;
        int contentLength = 0;
        while(!(line = in.readLine()).equals("")) {
            if(line.startsWith("Content-Length:")) {
                contentLength = Integer.parseInt(line.split(":")[1].trim());
            }
        }

        char[] buffer = new char[contentLength];
        in.read(buffer);
        String body = new String(buffer);

        try{
            int index = body.indexOf("Station-ID:");
            String ID = body.substring(index+11, index);
            WeatherEntry entry = new WeatherEntry();
            entry.body = body;
            entry.lastUpdated = System.currentTimeMillis();
            boolean isNew = !DataStore.containsKey(ID);

            DataStore.put(ID, entry);
            SaveToFile();

            SendResponse(out, isNew ? 201 : 200, "OK");
        } catch (Exception e) {
            SendResponse(out, 500, "Invalid JSON");
        }
    }

    public static void HandleGET(PrintWriter out) throws IOException {
        String DataEntries = "";
        String JSONLine = "{\n  \"stations\": [\n";
        String Temp;
    }

    public static void SendResponse(PrintWriter out, int status, String message) throws IOException {
        out.println("HTTP/1.1 " + status);
        out.println("Lamport-Clock: ");
        out.println("Content-Type: application/json");
        out.println("Content-Length: " + message.length());
        out.println();
        out.println(message);
    }

    public static void DeleteExpiredData() {

    }

    private static void SaveToFile() throws IOException {
        try (FileWriter fw = new FileWriter("weatherdata.json", true)) {

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class WeatherEntry {
        String body;
        long lastUpdated;
    }
}
