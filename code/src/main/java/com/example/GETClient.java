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
        new GETClient().SendGET(args[0]); //Sends get to URL given
    }

    public void OutputJSON(BufferedReader br) throws IOException {
        String Output = "";
        String Input;

        while ((Input = br.readLine()) != null) { //Reads all off what AggregationServer sends
            if(Input.trim().startsWith("{") && !Input.trim().equals("{")){ //Checks if starts with curly bracket
                String[] VariablesList = Input.split(","); //Splits all variables into array

                for(String Item : VariablesList ){
                    String Variable = Item.trim().split(":")[0].replaceAll("\"", "");
                    String Value = Item.trim().split(":")[1].replaceAll("\"", "");
                    if(Variable.equals("{id")){ //Gets ID and separates it from other data
                        Output = Output.concat("\n---\nStation: " + Value + "\n\n");
                    }else if(Value.endsWith("}")){ //If its the last Variable get rid of } first
                        Value = Value.substring(0, Value.length() - 1);
                        Output = Output.concat(Variable + ": " + Value + "\n\n");
                    }else{
                        Output = Output.concat(Variable + ": " + Value + "\n");
                    }
                }
            }
        }
        System.out.print(Output); //Prints parsed JSON
    }

    public void SendGET(String urlString) throws IOException {
        url = new URL(urlString); 
        //Connects to AggregationServer
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "ATOMClient/1/0");
        connection.setRequestProperty("Lamport-Clock",  Integer.toString(Clock.Output()));
        
        Clock.Tick();

        int responseCode = connection.getResponseCode(); //Sends and gets response code
        System.out.println("GET request sent. Response Code: " + responseCode);

        String ResponseLamport = connection.getHeaderField("Lamport-Clock");
        if(ResponseLamport != null) Clock.Assert(Integer.parseInt(ResponseLamport)); //Asserts Lamport

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

        BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream())); //Gets input from Aggregation Server

        OutputJSON(br); //Sends input to OutputJSON so it becomes readable
        br.close();
    }
}
