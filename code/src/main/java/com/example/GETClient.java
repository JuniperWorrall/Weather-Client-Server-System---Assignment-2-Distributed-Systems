package com.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class GETClient {
    private final LamportClock Clock = new LamportClock();
    public URL url;

    public static void main(String[] args) throws MalformedURLException, IOException {
        args = new String[]{"http://localhost:4567/"};
        new GETClient().SendGET(args[0]);
    }

    public void OutputJSON(BufferedReader br) throws IOException {
        String Output = "";
        String Input;

        while ((Input = br.readLine()) != null) {
            if(Input.trim().startsWith("{") && !Input.trim().equals("{")){
                String[] VariablesList = Input.split(",");

                for(String Item : VariablesList ){
                    String Variable = Item.trim().split(":")[0].replaceAll("\"", "");
                    String Value = Item.trim().split(":")[1].replaceAll("\"", "");
                    if(Variable.equals("{id")){
                        Output = Output.concat("\n---\nStation: " + Value + "\n\n");
                    }else if(Value.endsWith("}")){
                        Value = Value.substring(0, Value.length() - 1);
                        Output = Output.concat(Variable + ": " + Value + "\n\n");
                    }else{
                        Output = Output.concat(Variable + ": " + Value + "\n");
                    }
                }
            }
        }
        System.out.print(Output);
    }

    public void SendGET(String urlString) throws IOException {
        url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "ATOMClient/1/0");
        connection.setRequestProperty("Lamport-Clock",  Integer.toString(Clock.Output()));
        
        Clock.Tick();

        int responseCode = connection.getResponseCode();
        System.out.println("GET request sent. Response Code: " + responseCode);

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
                return;
            case HttpURLConnection.HTTP_NO_CONTENT:
                System.out.println("HTTP NO_CONTENT");
                return;
            case HttpURLConnection.HTTP_BAD_REQUEST:
                System.out.println("HTTP NOT GET/PUT");
                return;
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));

        OutputJSON(br);
        br.close();
    }
}
