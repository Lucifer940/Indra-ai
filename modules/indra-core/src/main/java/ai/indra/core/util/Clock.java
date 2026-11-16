package ai.indra.core.util;

/** Abstraction over wall-clock time so that scheduling logic is testable. */
public interface Clock {

    /** Current time in epoch milliseconds (UTC). */
    long nowMillis();

    /** A clock backed by the system time. */
    Clock SYSTEM = new Clock() {
        @Override
        public long nowMillis() {
            return System.currentTimeMillis();
        }
    };
}
