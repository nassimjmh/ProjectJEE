package jee.monitor;


public class Event {
    private int philosopherId;
    private String state; // HUNGRY, EATING, DONE, THINKING
    private int logicalClock;

    public Event(int philosopherId, String state, int logicalClock) {
        this.philosopherId = philosopherId;
        this.state = state;
        this.logicalClock = logicalClock;
    }

    // Getters et Setters
    public int getPhilosopherId() { return philosopherId; }
    public void setPhilosopherId(int philosopherId) { this.philosopherId = philosopherId; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public int getLogicalClock() { return logicalClock; }
    public void setLogicalClock(int logicalClock) { this.logicalClock = logicalClock; }
}
