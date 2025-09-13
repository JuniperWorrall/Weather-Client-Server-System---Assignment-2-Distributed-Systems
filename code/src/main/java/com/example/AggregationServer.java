package com.example;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
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

    public static void main( String[] args ) throws IOException { // Defines port if that is entered when run and starts the Aggregation Server
        int port = (args.length) > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        new AggregationServer().Start(port);
    }

    public void Start(int port) throws IOException {
        LoadFromFile(); //Checks if a file already exsists and loads it

        ServerSocket serverSocket = new ServerSocket(port); //Opens a socket at the given port

        //Creates a scheduler that runs a task every 1 second checking if each entry is older than 30 seconds
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            DataStore.entrySet().removeIf(e -> now - e.getValue().lastUpdated > 30000);
        }, 1, 1, TimeUnit.SECONDS);

        //Accepts connections to the server and starts a new ClientHandler for each
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
            //Creates an in and out for all clients to take in and send out information
            try(BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream())); 
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                String requestLine = in.readLine(); //Takes in the initial request
                if(requestLine == null) return;
                System.out.println("Request:" + requestLine);

                String[] parts = requestLine.split(" "); //Checks for GET/PUT request sent
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
                    default: //If GET or PUT are not sent as first part
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
        int contentLength = 1; //This starts as one so if Content-Length header is not concluded, a 500 error will be send instead of 204
        String ID = "NULL";
        int Checksum = 0;

        while(!(line = in.readLine()).equals("")) { //Reads all lines until end of input
            if(line.startsWith("Content-Length:")) { //Gets the header information and confirms they are there
                contentLength = Integer.parseInt(line.split(":")[1].trim());
                Checksum++;
            }
            if(line.startsWith("Lamport-Clock:")) { //Gets the header information and confirms they are there
                Clock.Assert(Integer.parseInt(line.split(":")[1].trim()));
                Checksum++;
            }
            if(line.startsWith("Station-ID:")) { //Gets the header information and confirms they are there
                ID = line.split(":")[1].trim();
                Checksum++;
            }
        }

        if(contentLength == 0){ //If there is not accompanying JSON, 204 error
            SendResponse(out, 204, "Empty body, no content");
            return;
        }
        
        char[] buffer = new char[contentLength];
        in.read(buffer); //Reads JSON that is input
        String body = new String(buffer);
        char first = body.charAt(0); //Gets first and last character to check if they are "{" "}"
        char last = body.charAt(body.length() - 1);

        if(Checksum != 3 || first != '{' || last != '}'){ //All three headers arent included and JSON doesnt start and end with curly brackets there is a 500 error
            SendResponse(out, 500, "Invalid JSON");
            return;
        }

        try{
            boolean isNew = !DataStore.containsKey(ID);

            WeatherEntry entry = new WeatherEntry();
            entry.body = body;
            entry.lastUpdated = System.currentTimeMillis();
            entry.LamportNumber = Clock.Output();

            DataStore.compute(ID,(key, existing) -> {
                if(existing == null || entry.LamportNumber > existing.LamportNumber){
                    return entry;
                }
                return existing;
            });

            Clock.Tick();
            
            SaveToFile();

            SendResponse(out, isNew ? 201 : 200, "OK"); //If new send 201 otherwise 200
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void HandleGET(BufferedReader in, PrintWriter out) throws IOException {
        String TempLine;
        while(!(TempLine = in.readLine()).equals("")) { //Reads in input and gets LamportClock, runs Assert then break
            if(TempLine.startsWith("Lamport-Clock:")) {
                Clock.Assert(Integer.parseInt(TempLine.split(":")[1].trim())); 
            }
            break;
        } 

        Clock.Tick();

        //Gets content of saved file and sends it to client
        StringBuilder JSON = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader("WeatherData.json"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                JSON.append(line).append(System.lineSeparator());
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        }
        String FinalJSON = JSON.toString();

        SendResponse(out, 200, FinalJSON);
    }

    public void SendResponse(PrintWriter out, int status, String message) throws IOException {
        Clock.Tick(); //Ticks just before sending
        out.println("HTTP/1.1 " + status); //Adds appropriate headers
        out.println("Lamport-Clock: " + Clock.Output());
        out.println("Content-Type: application/json");
        out.println("Content-Length: " + message.length());
        out.println();
        out.println(message); //Sends message (JSON)
    }

    private synchronized void SaveToFile() throws IOException {
        Path TempPath = Paths.get("WeatherData.json.tmp");
        Path RealPath = Paths.get("WeatherData.json");

        String JSONLine = "{\n  \"stations\":[\n"; //Begins JSON parsing
        String EntryTemp = "";
        String LineTemp = "";
            
        for(ConcurrentHashMap.Entry<String, WeatherEntry> entry : DataStore.entrySet()){ //Parses all weather data given into a local file
            JSONLine += LineTemp.replaceAll("\t", "");
            EntryTemp = entry.getValue().body.replaceAll("\t", "");
            LineTemp = "\t\t\t " + entry.getValue().body.replaceAll("\t", "") + ",\n";
        }

        JSONLine += "\t\t\t " + EntryTemp + "\n  ]\n}";
        
        try (FileOutputStream fos = new FileOutputStream(TempPath.toFile()); 
            FileChannel Channel = fos.getChannel(); 
            PrintWriter out = new PrintWriter(fos)) {
            out.print(JSONLine); //Prints to temp file

            Channel.force(true); //Flushes buffer, ensures durabilty during crash
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Files.move(TempPath, RealPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); //Atomically moves to correct file
    }

    private synchronized void LoadFromFile() throws IOException {
        Path RealPath = Paths.get("WeatherData.json");

        if (!Files.exists(RealPath)) return; //Checks if file exists

        try (BufferedReader br = Files.newBufferedReader(RealPath)) { 
            //Parses what is in saved files and turns it into WeatherEntry and uploads to DataStore with correct ID
            String line;
            StringBuilder jsonEntry = new StringBuilder();
            String currentID = null;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                
                if (line.startsWith("{")) {
                    jsonEntry = new StringBuilder();
                }

                jsonEntry.append(line);

                if (line.contains("\"id\"")) {
                    currentID = line.split(":")[1].trim().replace("\"", "").replace(",", "");
                }

                if (line.endsWith("}")) {
                    // Finished reading a single station
                    if (currentID != null) {
                        WeatherEntry entry = new WeatherEntry();
                        entry.body = jsonEntry.toString();
                        entry.lastUpdated = System.currentTimeMillis();
                        entry.LamportNumber = 0;
                        DataStore.put(currentID, entry);
                        currentID = null;
                    }
                }
            }
        }
    }

    private class WeatherEntry {
        String body;
        long lastUpdated;
        int LamportNumber;
    }
}
