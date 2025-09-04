package com.example;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class GETClient {
    private int LamportClock;
    private URL url;

    public void Run() throws MalformedURLException {
        String[] args = new String[]{"http://www.website.com:19", "Weather.txt"};
        url = new URL(args[0]);
    }

    public void OutputJSON(StringBuffer json) {
        String Output = "";
        String Input = json.toString();
        int index = Input.indexOf("\n");
        while (index != -1) {
            String Temp = Input.substring(0,index);
            Output = Output.concat(Temp);
            Input = Input.replace(Temp, "");
            index = Input.indexOf("\n");
        }
    }

    public void SendGET() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "ATOMClient/1/0");

        int responseCode = connection.getResponseCode();
        System.out.println("GET request sent. Response Code: " + responseCode);

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

        BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = br.readLine()) != null) {
            response.append(inputLine);
        }
        br.close();

        OutputJSON(response);
    }
}
