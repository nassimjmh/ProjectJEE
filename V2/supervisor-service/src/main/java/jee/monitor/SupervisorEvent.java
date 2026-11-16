package jee.monitor;


public class SupervisorEvent {
    private int philosopherId;

    public SupervisorEvent(int philosopherId) {
        this.philosopherId = philosopherId;

    }

    public int getPhilosopherId() { return philosopherId; }
    public void setPhilosopherId(int philosopherId) { this.philosopherId = philosopherId; }

}
