package jee.philosophe.distributed;

/** Autres messages HTTP échangés entre philosophes. */
public final class Messages {
    private Messages() {}

    /** Fin de repas : la demande (from, ts) n'est plus active. */
    public record DoneMsg(int from, long ts, long clock) {}

    /** Transfert du jeton unique de la fourchette {@code fork}. */
    public record TokenMsg(int fork, int from, long clock) {}
}
