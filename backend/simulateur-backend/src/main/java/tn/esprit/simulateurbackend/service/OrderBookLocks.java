package tn.esprit.simulateurbackend.service;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/*
 * Un seul traitement à la fois par carnet.
 *
 * Les ordres des utilisateurs et le teneur de marché
 * verrouillent les mêmes lignes dans des ordres différents :
 * sérialiser par actif, avant d'ouvrir la transaction,
 * évite les interblocages en base.
 */
@Component
public class OrderBookLocks {

    private final Map<String, ReentrantLock> locks =
            new ConcurrentHashMap<>();

    public <T> T withLock(
            String symbol,
            Supplier<T> action
    ) {

        String key =
                symbol == null
                        ? ""
                        : symbol
                        .trim()
                        .toUpperCase(Locale.ROOT);

        ReentrantLock lock =
                locks.computeIfAbsent(
                        key,
                        ignored -> new ReentrantLock(true)
                );

        lock.lock();

        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
