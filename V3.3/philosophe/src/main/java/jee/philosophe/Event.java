package jee.philosophe;

/** Évènement envoyé au moniteur (pure observation, le moniteur ne décide de rien). */
public class Event {
    private int philosopherId;
    private String state;
    private long logicalClock;
    private String detail;

    public Event() {}

    public Event(int philosopherId, String state, long logicalClock, String detail) {
        this.philosopherId = philosopherId;
        this.state = state;
        this.logicalClock = logicalClock;
        this.detail = detail;
    }

    public int getPhilosopherId() { return philosopherId; }
    public void setPhilosopherId(int philosopherId) { this.philosopherId = philosopherId; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public long getLogicalClock() { return logicalClock; }
    public void setLogicalClock(long logicalClock) { this.logicalClock = logicalClock; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
