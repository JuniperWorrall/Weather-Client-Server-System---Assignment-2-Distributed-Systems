package com.example;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


public class AggregationServerTest {
    private AggregationServer server;
    private StringWriter responseWriter;
    private PrintWriter out;
    private BufferedReader in;

    @BeforeEach
    void setUp() {
        server = new AggregationServer();
        responseWriter = new StringWriter();
        out = new PrintWriter(responseWriter, true);
    }

    @Test
    void testHandlePUTCreatesNewEntry() throws Exception {
        String body = "{\"temp\":25}";
        String requestHeaders =
                "Content-Length: " + body.length() + "\r\n" +
                "Lamport-Clock: 1\r\n" +
                "Station-ID: STN1\r\n" +
                "\r\n" +
                body;

        in = new BufferedReader(new StringReader(requestHeaders));
        server.HandlePUT(in, out);

        String response = responseWriter.toString();
        assertTrue(response.contains("HTTP/1.1 201"), response); // created
    }

    @Test
    void testHandlePUTWithInvalidJsonReturns500() throws Exception {
        String body = "{\"temp\":15}";
        String headers =
            "Content-Lengt: " + body.length() + "\r\n" +
            "Lamport-Clock: 1\r\n" +
            "Station-ID: STN6\r\n\r\n" + body;
        in = new BufferedReader(new StringReader(headers));
        server.HandlePUT(in, out);
        
        String response = responseWriter.toString();
        assertTrue(response.contains("HTTP/1.1 500"));
    }

    @Test
    void testHandlePUTUpdatesExistingEntry() throws Exception {
        // First insert
        String body1 = "{\"temp\":25}";
        String headers1 =
                "Content-Length: " + body1.length() + "\r\n" +
                "Lamport-Clock: 1\r\n" +
                "Station-ID: STN2\r\n" +
                "\r\n" +
                body1;
        in = new BufferedReader(new StringReader(headers1));
        server.HandlePUT(in, out);

        // Update with new Lamport
        String body2 = "{\"temp\":30}";
        String headers2 =
                "Content-Length: " + body2.length() + "\r\n" +
                "Lamport-Clock: 5\r\n" +
                "Station-ID: STN2\r\n" +
                "\r\n" +
                body2;
        in = new BufferedReader(new StringReader(headers2));
        server.HandlePUT(in, out);

        String response = responseWriter.toString();
        assertTrue(response.contains("HTTP/1.1 200")); // update
    }

    @Test
    void testHandleGETReturnsJson() throws Exception {
        // Insert first
        String body = "{\"temp\":22}";
        String request =
                "Content-Length: " + body.length() + "\r\n" +
                "Lamport-Clock: 1\r\n" +
                "Station-ID: STN3\r\n" +
                "\r\n" +
                body;
        in = new BufferedReader(new StringReader(request));
        server.HandlePUT(in, out);

        // Now GET
        String getHeaders = "Lamport-Clock: 2\r\n\r\n";
        in = new BufferedReader(new StringReader(getHeaders));
        server.HandleGET(in, out);

        String response = responseWriter.toString();
        assertTrue(response.contains("\"stations\""));
        assertTrue(response.contains("{\"temp\":22}"));
    }

    @Test
    void testSendResponseFormat() throws Exception {
        server.SendResponse(out, 200, "{\"ok\":true}");
        String response = responseWriter.toString();

        assertTrue(response.contains("HTTP/1.1 200"));
        assertTrue(response.contains("Content-Length:"));
        assertTrue(response.contains("{\"ok\":true}"));
    }

    @Test
    void testSaveAndLoadFile() throws Exception {
        // Create entry
        String body = "{\"humidity\":50}";
        String headers =
                "Content-Length: " + body.length() + "\r\n" +
                "Lamport-Clock: 1\r\n" +
                "Station-ID: STN4\r\n" +
                "\r\n" +
                body;
        in = new BufferedReader(new StringReader(headers));
        server.HandlePUT(in, out);

        Path realFile = Paths.get("weatherdata.json");
        assertTrue(Files.exists(realFile));

        // Cleanup after test
        Files.deleteIfExists(realFile);
        Files.deleteIfExists(Paths.get("weatherdata.json.tmp"));
    }

    @Test
    void testExpiryRemovesOldData() throws Exception {
        String body = "{\"temp\":28}";
        String headers =
            "Content-Length: " + body.length() + "\r\n" +
            "Lamport-Clock: 1\r\n" +
            "Station-ID: STN7\r\n\r\n" + body;
        in = new BufferedReader(new StringReader(headers));
        server.HandlePUT(in, out);

        // Simulate waiting beyond expiry
        Thread.sleep(31000);

        String getHeaders = "Lamport-Clock: 2\r\n\r\n";
        in = new BufferedReader(new StringReader(getHeaders));
        server.HandleGET(in, out);

        String response = responseWriter.toString();
        assertTrue(!response.contains("STN7"));
    }

    @Test
    void testAggregationServerReturnsAllStatusCodes() throws Exception {
        String body = "{\"temp\":25}";
        
        // 201 - First PUT
        in = new BufferedReader(new StringReader(
            "Content-Length: " + body.length() + "\r\nStation-ID: STN_NEW\r\nLamport-Clock:1\r\n\r\n" + body
        ));
        server.HandlePUT(in, out);
        assertTrue(responseWriter.toString().contains("HTTP/1.1 201"));

        responseWriter.getBuffer().setLength(0); // reset buffer

        // 200 - Update PUT
        in = new BufferedReader(new StringReader(
            "Content-Length: " + body.length() + "\r\nStation-ID: STN_NEW\r\nLamport-Clock:2\r\n\r\n" + body
        ));
        server.HandlePUT(in, out);
        assertTrue(responseWriter.toString().contains("HTTP/1.1 200"));

        responseWriter.getBuffer().setLength(0);

        // 204 - No content
        in = new BufferedReader(new StringReader(
            "Content-Length: 0\r\nStation-ID: STN_NEW\r\nLamport-Clock:3\r\n\r\n"
        ));
        server.HandlePUT(in, out);
        assertTrue(responseWriter.toString().contains("HTTP/1.1 204"));

        responseWriter.getBuffer().setLength(0);

        // 400 - Not GET or PUT, dont have code to send a different request so I am simulating it
        server.SendResponse(out, 400, "Bad Request");
        assertTrue(responseWriter.toString().contains("HTTP/1.1 400"));

        responseWriter.getBuffer().setLength(0);

        // 500 - Invalid JSON
        in = new BufferedReader(new StringReader(
            "Content-Length: 5\r\nStation-ID: STN_ERR\r\nLamport-Clock:4\r\n\r\nabcde"
        ));
        server.HandlePUT(in, out);
        assertTrue(responseWriter.toString().contains("HTTP/1.1 500"));
    }
}
