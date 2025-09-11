package com.example;
import java.util.concurrent.atomic.AtomicInteger;

public class LamportClock {
    private final AtomicInteger Clock = new AtomicInteger(0);

    public int Tick() {
        return Clock.incrementAndGet();
    }

    public int Output() {
        return Clock.get();
    }

    public int Assert(int Recieved_Lamport) {
        return Clock.updateAndGet(local -> Math.max(local, Recieved_Lamport) + 1);
    }
}