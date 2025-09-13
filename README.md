# Weather-Client-Server-System---Assignment-2-Distributed-Systems

## Functional Analysis

### Content Server

The Content Server will read weather data from a local file and convert it into JSON, then send a PUT request to the aggregation Server with its Lamport clock timestamp. It also expects an acknowledgement from the server, 201 for the first time and 200 for all successful updates. The Lamport clock must be maintained, incrementing on messages sent. 

### Aggregation Server

It will accept PUT requests from content servers and take in and validate their JSON. It will order updates using Lamport clock timestamps and store the data persistently; if it hasn't updated in 30 seconds, it will delete the data. The aggregation server will also handle GET requests from clients and send them JSON data; both PUT and GET requests should be multi-threaded with proper handling. 

### Client

Sends GET requests to the aggregation server and will, in turn, receive JSON data from it. It will print this data to the client in a readable format. Also, maintain its Lamport clock by including it in GET requests and updating it when receiving JSON data. 

## Number of Replicas

There will be only one aggregation server that handles all clients and content servers, acting as the central hub. There can be any number of content servers, as these simulate separate weather stations sending their own data. There will also be any number of clients, as the number of people requesting data could be substantial.

## UML Diagram

<img width="802" height="181" alt="UML" src="https://github.com/user-attachments/assets/b6a8c24b-f872-4642-b449-914af86aa4f6" />

## How to Compile and Run

All of this should happen in a terminal while in the file path /code/src/main/java/. Follow these steps, and you will get it working.

1. Compile the Aggregation Server, enter "javac com/example/AggregationServer.java com/example/LamportClock.java".
2. Then, run the server, enter "java com.example.AggregationServer", if you want a different port number, enter "java com.example.AggregationServer (*Choosen_Port_Number*)".
3. Compile the Content Server, enter "javac com/example/ContentServer.java com/example/LamportClock.java".
4. Then, run the Content Server, enter "java com.example.ContentServer http://localhost:(*Choosen_Port_Number*) (*Name of file you want to send, including file type*)". This data will last 30 seconds.
5. Compile the GETClient, enter "javac com/example/GETClient.java com/example/LamportClock.java".
6. Then, run the GETClient, enter "java com.example.GETClient http://localhost:(*Choosen_Port_Number*)".
