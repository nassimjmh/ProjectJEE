package jee.philosophe;


public class SupervisorEvent {
    private int philosopherId;
    private String state;
    private int logicalClock;

    public SupervisorEvent(int philosopherId) {
        this.philosopherId = philosopherId;

    }

    public int getPhilosopherId() { return philosopherId; }
    public void setPhilosopherId(int philosopherId) { this.philosopherId = philosopherId; }

}
