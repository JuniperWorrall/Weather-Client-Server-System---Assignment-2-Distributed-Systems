package com.example;

import java.io.*;
import java.nio.Buffer;

public class ContentServer {
    private int LamportClock;
    private String LocalWeatherData;

    public void Run(String[] args){
        String ServerAndPort = args[0];
        LocalWeatherData =  args[1];
    }

    public void TextToJSON(){
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
            String JSONLine = "";
            String temp;
            while ((line = br.readLine()) != null){
                int indexOfColon = line.indexOf(":");

                if(indexOfColon != -1){
                    fw.write(JSONLine);
                    temp = line.substring(0, indexOfColon);
                    temp = temp.replaceAll("\\s", "");
                    JSONLine = "\"" + temp + "\" : \"";
                    temp = line.substring(indexOfColon+1);
                    temp = temp.replaceAll("\\s", "");
                    JSONLine = JSONLine + temp + "\",";
                } else{
                    System.err.println("Error colon not found in line");
                }
            }
            JSONLine = JSONLine.replaceAll(",", "");
            fw.write(JSONLine);
        } catch(IOException e){
            System.err.println("Error while reading local weather data");
            e.printStackTrace();
        }
    }

    public void SendPUT(){

    }
}
