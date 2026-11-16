package jee.monitor;


public class EventSupervisor {
    private int philosopherId;
    private String state;
    private int logicalClock;

    public EventSupervisor(int philosopherId) {
        this.philosopherId = philosopherId;

    }

    public int getPhilosopherId() { return philosopherId; }
    public void setPhilosopherId(int philosopherId) { this.philosopherId = philosopherId; }

}
