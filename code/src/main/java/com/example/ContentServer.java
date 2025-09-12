package com.example;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class ContentServer {
    private final LamportClock Clock = new LamportClock();

    public static void main(String[] args) throws IOException {
        new ContentServer().TextToJSON(args[1]);
        new ContentServer().SendPUT(args[0]);
    }

    public void TextToJSON(String LocalWeatherData){
        BufferedReader br = null;

        try{
            File myFile = new File("Weather.json");

            if(myFile.createNewFile()){
                System.out.println("File created");
            } else{
                System.out.println( "Error file already exists!" );
            }

            FileWriter fw = new FileWriter("Weather.json");
            br = new BufferedReader(new FileReader(LocalWeatherData));
            String line;
            String JSONLine = "{\n";
            String temp;

            while ((line = br.readLine()) != null){
                int indexOfColon = line.indexOf(":");

                if(indexOfColon != -1){
                    fw.write(JSONLine);
                    temp = line.substring(0, indexOfColon);
                    JSONLine = "\t\"" + temp + "\":\"";
                    temp = line.substring(indexOfColon+1);
                    JSONLine += temp + "\",\n";
                } else{
                    System.err.println("Error colon not found in line");
                }
            }
            JSONLine = JSONLine.replaceAll(",", "");
            JSONLine = JSONLine + "}";
            fw.write(JSONLine);
            fw.flush();
            fw.close();
        } catch(IOException e){
            System.err.println("Error while reading local weather data");
            e.printStackTrace();
        }
    }

    public void SendPUT(String urlString) throws IOException{
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);
        connection.setRequestProperty("User-Agent", "ATOMClient/1/0");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Lamport-Clock",  Integer.toString(Clock.Output()));


        StringBuilder jsonContent = new StringBuilder();
        String ID = "";
        try (BufferedReader reader = new BufferedReader(new FileReader("Weather.json"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if(line.contains("ID")){
                    ID = line.split(":")[1].replaceAll("\"", "");
                }
                jsonContent.append(line);
            }
        }

        connection.setRequestProperty("Station-ID", ID);

        byte[] json = jsonContent.toString().getBytes(StandardCharsets.UTF_8);

        connection.setFixedLengthStreamingMode(json.length);
        connection.setRequestProperty("Content-Length", String.valueOf(json.length));

        try(OutputStream os = connection.getOutputStream()){
            os.write(json);
            os.flush();
        }

        Clock.Tick();

        int responseCode = connection.getResponseCode();
        System.out.println("PUT request sent. Response Code: " + responseCode);

        String ResponseLamport = connection.getHeaderField("Lamport-Clock");
        if(ResponseLamport != null) Clock.Assert(Integer.parseInt(ResponseLamport));

        switch (responseCode){
            case HttpURLConnection.HTTP_OK:
                System.out.println("HTTP OK");
                break;
            case HttpURLConnection.HTTP_CREATED:
                System.out.println("HTTP CREATED");
                break;
            case HttpURLConnection.HTTP_SERVER_ERROR:
                System.out.println("HTTP INTERNAL SERVER ERROR");
                break;
            case HttpURLConnection.HTTP_NO_CONTENT:
                System.out.println("HTTP NO_CONTENT");
                break;
            case HttpURLConnection.HTTP_BAD_REQUEST:
                System.out.println("HTTP NOT GET/PUT");
                break;
        }
    }
}
