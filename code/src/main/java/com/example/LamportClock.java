package com.example;
import java.util.concurrent.atomic.AtomicInteger;

public class LamportClock {
    private final AtomicInteger Clock = new AtomicInteger(0);

    public int Tick() {
        return Clock.incrementAndGet(); //Increment LamportClock by 1
    }

    public int Output() {
        return Clock.get(); //Return Lamport when called
    }

    public int Assert(int Recieved_Lamport) { //Get local and given lamport, find the highest and make local equal that plus 1
        return Clock.updateAndGet(local -> Math.max(local, Recieved_Lamport) + 1);
    }
}