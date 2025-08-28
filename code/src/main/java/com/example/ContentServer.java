package com.example;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.nio.Buffer;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class ContentServer {
    private int LamportClock;
    private static String LocalWeatherData;
    private static URL url;

    public static void main(String[] args) throws IOException {
        args = new String[]{"https://www.website.com:19", "Weather.txt"};
        url = new URL(args[0]);
        LocalWeatherData =  args[1];
        TextToJSON();

        SendPUT();
    }

    public static void TextToJSON(){
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
                    temp = temp.replaceAll("\\s", "");
                    JSONLine = "    \"" + temp + "\" : \"";
                    temp = line.substring(indexOfColon+1);
                    temp = temp.replaceAll("\\s", "");
                    JSONLine = JSONLine + temp + "\",\n";
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

    public static void SendPUT() throws IOException{
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);
        connection.setRequestProperty("User-Agent", "ATOMClient/1/0");
        connection.setRequestProperty("Content-Type", "application/json");


        StringBuilder jsonContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader("Weather.json"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
        }

        try(FileOutputStream fos = new FileOutputStream("WeatherOutput.txt")){
            byte[] json = jsonContent.toString().getBytes(StandardCharsets.UTF_8);
            int contentLength = json.length;
            connection.setRequestProperty("Content-Length", String.valueOf(contentLength));
            fos.write(json);
        }

        int responseCode = connection.getResponseCode();
        System.out.println("PUT request sent. Response Code: " + responseCode);

        switch (responseCode){
            case HttpsURLConnection.HTTP_OK:
                System.out.println("HTTP OK");
                break;
            case HttpsURLConnection.HTTP_CREATED:
                System.out.println("HTTP CREATED");
                break;
            case HttpsURLConnection.HTTP_SERVER_ERROR:
                System.out.println("HTTP INTERNAL SERVER ERROR");
                break;
            case HttpsURLConnection.HTTP_NO_CONTENT:
                System.out.println("HTTP NO_CONTENT");
                break;
            case HttpsURLConnection.HTTP_BAD_REQUEST:
                System.out.println("HTTP NOT GET/PUT");
                break;
        }
    }
}
