package rest;

import java.util.Map;

/**
 * Fern-Auslöser der Stufe-0-Update-Kaskade (SignalKiScanner doc/23 §4.1).
 * Der RestApiServer delegiert POST /api/v1/update an diese Schnittstelle;
 * implementiert wird sie von der GUI (busy-Kopplung + Swing-Einstieg).
 */
public interface UpdateStarter {

    UpdateAntwort starte(Map<String, Object> body);

    /** Letzter Job (auch abgeschlossener) für GET /api/v1/update/status;
     *  null = seit Start kein Update gelaufen (→ state "idle"). */
    UpdateJob letzterJob();

    /** Ergebnis des Startversuchs — der Server rendert daraus HTTP.
     *  (Klassische Klasse statt record: Projekt kompiliert mit Java 8.) */
    final class UpdateAntwort {
        private final int status;
        private final String error;
        private final boolean bereitsLaufend;
        private final UpdateJob job;

        private UpdateAntwort(int status, String error, boolean bereitsLaufend, UpdateJob job) {
            this.status = status;
            this.error = error;
            this.bereitsLaufend = bereitsLaufend;
            this.job = job;
        }

        public static UpdateAntwort gestartet(UpdateJob job) {
            return new UpdateAntwort(202, null, false, job);
        }

        public static UpdateAntwort laufend(UpdateJob job) {
            return new UpdateAntwort(200, null, true, job);
        }

        public static UpdateAntwort beschaeftigt(String grund) {
            return new UpdateAntwort(409, grund, false, null);
        }

        public int status() {
            return status;
        }

        public String error() {
            return error;
        }

        public boolean bereitsLaufend() {
            return bereitsLaufend;
        }

        public UpdateJob job() {
            return job;
        }
    }
}
