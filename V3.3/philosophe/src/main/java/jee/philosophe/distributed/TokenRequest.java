package jee.philosophe.distributed;

import java.util.List;

/** Demande diffusée à tous les philosophes. (ts, from) est la priorité globale (ordre total de Lamport). */
public record TokenRequest(int from, long ts, List<Integer> forks) {}
