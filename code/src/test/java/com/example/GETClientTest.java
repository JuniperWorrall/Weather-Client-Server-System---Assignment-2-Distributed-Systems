package com.example;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

class GETClientTest {
    private static HttpServer mockServer;
    public ByteArrayOutputStream outContent;

    @BeforeAll
    static void startServer() throws IOException {
        mockServer = HttpServer.create(new InetSocketAddress(4567), 0);
        mockServer.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                // Send back JSON with headers
                String responseJson = "{" +
                        "\"id\":\"station-123\"," +
                        "\"temp\":\"25\"," +
                        "\"humidity\":\"80\"" +
                        "}";
                exchange.getResponseHeaders().add("LamportClock", "5");
                exchange.sendResponseHeaders(200, responseJson.getBytes().length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseJson.getBytes());
                }
            }
        });
        mockServer.start();
    }

    @AfterAll
    static void stopServer() {
        mockServer.stop(0);
    }

    @BeforeEach
    void setUpStream() {
        outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    void restoreStream() {
        System.setOut(System.out);
    }

    @Test
    void testSendGET_receivesJsonAndParses() throws Exception {
        GETClient client = new GETClient();

        // Run the GET call
        client.SendGET("http://localhost:4567/");

        // Capture printed output
        String printed = outContent.toString();

        // Check if the formatted output contains the expected values
        assertTrue(printed.contains("Station: station-123"), "Output should include Station ID");
        assertTrue(printed.contains("temp: 25"), "Output should include temperature");
        assertTrue(printed.contains("humidity: 80"), "Output should include humidity");

        // Verify Lamport clock ticked
        Field clockField = GETClient.class.getDeclaredField("Clock");
        clockField.setAccessible(true);
        LamportClock clock = (LamportClock) clockField.get(client);
        assertTrue(clock.Output() >= 1, "Lamport clock should have ticked after GET");
    }

    @Test
    void testGETClientHandlesServerDown() throws MalformedURLException {
        GETClient client = new GETClient();

        assertThrows(ConnectException.class, () -> {
            client.SendGET("http://localhost:9999/");
        });
    }
}