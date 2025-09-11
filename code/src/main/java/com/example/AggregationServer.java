package com.example;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AggregationServer {
    private final static int DEFAULT_PORT = 4567;
    private final LamportClock Clock = new LamportClock();
    private final ConcurrentHashMap<String, WeatherEntry> DataStore = new ConcurrentHashMap<>();

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

    public class ClientHandler implements Runnable {
        private final Socket socket;

        public ClientHandler(Socket socket) { this.socket = socket; }

        @Override
        public void run() {
            Clock.Tick();
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
                switch (method) {
                    case "PUT":
                        HandlePUT(in, out);
                        break;
                    case "GET":
                        HandleGET(in, out);
                        break;
                    default:
                        SendResponse(out, 400, "Bad Request");
                        break;
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void HandlePUT(BufferedReader in, PrintWriter out) throws IOException {
        String line;
        int contentLength = 1;
        String ID = "NULL";
        int Checksum = 0;
        while(!(line = in.readLine()).equals("")) {
            if(line.startsWith("Content-Length:")) {
                contentLength = Integer.parseInt(line.split(":")[1].trim());
                Checksum++;
            }
            if(line.startsWith("Lamport-Clock:")) {
                Clock.Assert(Integer.parseInt(line.split(":")[1].trim()));
                Checksum++;
            }
            if(line.startsWith("Station-ID:")) {
                ID = line.split(":")[1].trim();
                Checksum++;
            }
        }

        if(contentLength == 0){
            SendResponse(out, 204, "Empty body, no content");
            return;
        }
        
        char[] buffer = new char[contentLength];
        in.read(buffer);
        String body = new String(buffer);
        char first = body.charAt(0);
        char last = body.charAt(body.length() - 1);

        if(Checksum != 3 || first != '{' || last != '}'){
            SendResponse(out, 500, "Invalid JSON");
            return;
        }

        try{
            WeatherEntry entry = new WeatherEntry();
            entry.body = body;
            entry.lastUpdated = System.currentTimeMillis();
            entry.LamportNumber = Clock.Output();
            boolean isNew = !DataStore.containsKey(ID);

            if(!isNew){
                WeatherEntry Existing = DataStore.get(ID);
                if(Existing == null || entry.LamportNumber > Existing.LamportNumber){
                    DataStore.put(ID, entry);
                }
            } else{
                DataStore.put(ID, entry);
            }

            Clock.Tick();
            
            SaveToFile();

            SendResponse(out, isNew ? 201 : 200, "OK");
        } catch (Exception e) {
            
        }
    }

    public void HandleGET(BufferedReader in, PrintWriter out) throws IOException {
        String line;
        while(!(line = in.readLine()).equals("")) {
            if(line.startsWith("Lamport-Clock:")) {
                Clock.Assert(Integer.parseInt(line.split(":")[1].trim()));
            }
            break;
        } 

        Clock.Tick();
        
        String JSONLine = "{\n  \"stations\": [\n";
        String Temp = "";

        for(WeatherEntry entry : DataStore.values()){
            JSONLine += Temp;
            Temp = "\t" + entry.body + ",\n";
        }
        StringBuilder sb = new StringBuilder(Temp);
        sb.deleteCharAt(sb.lastIndexOf(","));
        Temp = sb.toString();
        JSONLine += Temp;

        SendResponse(out, 200, JSONLine);
    }

    public void SendResponse(PrintWriter out, int status, String message) throws IOException {
        Clock.Tick();
        out.println("HTTP/1.1 " + status);
        out.println("Lamport-Clock: " + Clock.Output());
        out.println("Content-Type: application/json");
        out.println("Content-Length: " + message.length());
        out.println();
        out.println(message);
    }

    private void SaveToFile() throws IOException {
        Path TempPath = Paths.get("weatherdata.json.tmp");
        Path RealPath = Paths.get("weatherdata.json");

        String JSONLine = "{\n  \"stations\": [\n";
        String Temp = "";
            
        for(ConcurrentHashMap.Entry<String, WeatherEntry> entry : DataStore.entrySet()){
            JSONLine += Temp;
            Temp = "\t" + entry.getValue().body + ",\n";
        }
        StringBuilder sb = new StringBuilder(Temp);
        sb.deleteCharAt(sb.lastIndexOf(","));
        Temp = sb.toString();
        JSONLine += Temp;
        
        try (FileOutputStream fos = new FileOutputStream(TempPath.toFile(), true); FileChannel Channel = fos.getChannel(); PrintWriter out = new PrintWriter(fos)) {
            out.print(JSONLine);

            Channel.force(true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Files.move(TempPath, RealPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private void LoadFromFile() throws IOException{
        Path TempPath = Paths.get("weatherdata.json.tmp");
        Path RealPath = Paths.get("weatherdata.json");

        if(Files.exists(TempPath) && !Files.exists(RealPath)){
            Files.move(TempPath, RealPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }

    }

    private class WeatherEntry {
        String body;
        long lastUpdated;
        int LamportNumber;
    }
}
