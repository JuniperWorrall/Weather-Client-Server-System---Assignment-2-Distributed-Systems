package com.example;

import org.junit.jupiter.api.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.URL;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import static org.junit.jupiter.api.Assertions.*;

public class ContentServerTest {
    private ContentServer server;
    private File testInputFile;

    @BeforeEach
    void setUp() throws Exception {
        server = new ContentServer();

        // Create a test input weather file
        testInputFile = new File("Weather.txt");
        try (PrintWriter pw = new PrintWriter(new FileWriter(testInputFile))) {
            pw.println("Temperature: 25");
            pw.println("Humidity: 80");
        }
    }

    @AfterEach
    void tearDown() {
        testInputFile.delete();
        new File("Weather.json").delete();
    }

    @Test
    void testTextToJSON_createsJsonFile() throws Exception {
        server.LocalWeatherData = testInputFile.getAbsolutePath();
        server.TextToJSON();

        File jsonFile = new File("Weather.json");
        assertTrue(jsonFile.exists(), "Weather.json should exist after conversion");

        String content = new String(java.nio.file.Files.readAllBytes(jsonFile.toPath()));
        assertTrue(content.contains("\"Temperature\":\"25\""));
        assertTrue(content.contains("\"Humidity\":\"80\""));
    }

    @Test
    void testSendPUT_sendsJsonToMockServer() throws Exception {
        // Start a mock HTTP server
        HttpServer mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        int port = mockServer.getAddress().getPort();

        final StringBuilder receivedData = new StringBuilder();
        mockServer.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        receivedData.append(line);
                    }
                }
                exchange.getResponseHeaders().add("LamportClock", "5");
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, -1);
            }
        });
        mockServer.start();

        // Prepare input JSON
        server.LocalWeatherData = testInputFile.getAbsolutePath();
        server.TextToJSON();
        server.url = new URL("http://localhost:" + port + "/");

        // Run PUT
        server.SendPUT();

        // Shutdown server
        mockServer.stop(0);

        // Validate received JSON content
        assertTrue(receivedData.toString().contains("\"Temperature\":\"25\""));
        assertTrue(receivedData.toString().contains("\"Humidity\":\"80\""));
    }

    @Test
    void testMultipleContentServersPutOrdering() throws Exception, IOException {
        // Start a mock server
        HttpServer mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        int port = mockServer.getAddress().getPort();
        StringBuilder receivedLamports = new StringBuilder();

        mockServer.createContext("/", exchange -> {
            String lamport = exchange.getRequestHeaders().getFirst("Lamport-Clock");
            receivedLamports.append(lamport).append(",");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        mockServer.start();

        ContentServer cs1 = new ContentServer();
        cs1.LocalWeatherData = testInputFile.getAbsolutePath();
        cs1.url = new URL("http://localhost:" + port + "/");

        ContentServer cs2 = new ContentServer();
        cs2.LocalWeatherData = testInputFile.getAbsolutePath();
        cs2.url = new URL("http://localhost:" + port + "/");

        cs1.TextToJSON();
        cs2.TextToJSON();

        // Run PUTs concurrently
        Thread t1 = new Thread(() -> {
            try {
                cs1.SendPUT();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                cs2.SendPUT();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        t1.start(); t2.start();
        t1.join(); t2.join();

        mockServer.stop(0);

        // Lamport clock ordering should be monotonically increasing
        String[] clocks = receivedLamports.toString().split(",");
        int prev = 0;
        for(String clkStr : clocks) {
            int clk = Integer.parseInt(clkStr);
            assertTrue(clk >= prev, "Lamport clocks must not go backward");
            prev = clk;
        }
    }
}