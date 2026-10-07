package rest;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Status eines Stufe-0-Update-Jobs (SignalKiScanner doc/23 §4): Der Scanner
 * stößt per POST /api/v1/update die „Alles ausführen“-Kaskade an
 * (MQL4-Download → MQL5-Download → Konvertierung; Selenium-Login läuft
 * automatisch mit den gespeicherten Zugangsdaten) und pollt
 * GET /api/v1/update/status. Thread-sicherer Zustandscontainer.
 */
public final class UpdateJob {

    public enum State { IDLE, RUNNING, DONE, ERROR, LOGIN_REQUIRED }

    private static final DateTimeFormatter ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneId.systemDefault());

    private final String jobId;
    private final Instant startedAt = Instant.now();
    private volatile State state = State.IDLE;
    private volatile String phase = "";
    private volatile int done;
    private volatile int total;
    private volatile String message = "";
    private volatile String error;
    private volatile Instant finishedAt;
    private final Object ergebnisLock = new Object();
    private final Map<String, Object> ergebnis = new LinkedHashMap<>();

    public UpdateJob(String jobId) {
        this.jobId = jobId;
        ergebnis.put("signaleGeliefert", null);
        ergebnis.put("tradelistenNeu", null);
        ergebnis.put("tradelistenAktualisiert", null);
        ergebnis.put("katalogUebersprungen", false);
        ergebnis.put("datenstand", null);
        ergebnis.put("hinweise", new ArrayList<String>());
    }

    public String jobId() {
        return jobId;
    }

    public State state() {
        return state;
    }

    public boolean laeuft() {
        return state == State.RUNNING;
    }

    public void melde(String phase, int done, int total, String message) {
        if (state == State.DONE || state == State.ERROR
                || state == State.LOGIN_REQUIRED) return;
        this.state = State.RUNNING;
        this.phase = phase == null ? "" : phase;
        this.done = done;
        this.total = total;
        this.message = message == null ? "" : message;
    }

    public void ergebnis(String schluessel, Object wert) {
        synchronized (ergebnisLock) {
            ergebnis.put(schluessel, wert);
        }
    }

    public void hinweis(String text) {
        synchronized (ergebnisLock) {
            @SuppressWarnings("unchecked")
            List<String> liste = (List<String>) ergebnis.getOrDefault("hinweise", new ArrayList<String>());
            liste.add(text);
        }
    }

    public void fertig(String datenstand) {
        ergebnis("datenstand", datenstand);
        state = State.DONE;
        finishedAt = Instant.now();
    }

    public void fehler(String grund) {
        error = grund;
        state = State.ERROR;
        finishedAt = Instant.now();
    }

    private String stateAlsText() {
        // Java 8: kein Switch-Ausdruck — klassische Verzweigung
        switch (state) {
            case IDLE: return "idle";
            case RUNNING: return "running";
            case DONE: return "done";
            case ERROR: return "error";
            case LOGIN_REQUIRED: return "login_required";
            default: return "idle";
        }
    }

    public Map<String, Object> statusJson() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("jobId", jobId);
        out.put("state", stateAlsText());
        out.put("phase", phase);
        out.put("done", done);
        out.put("total", total);
        out.put("message", message);
        out.put("startedAt", ISO.format(startedAt));
        out.put("finishedAt", finishedAt == null ? null : ISO.format(finishedAt));
        synchronized (ergebnisLock) {
            out.put("ergebnis", new LinkedHashMap<>(ergebnis));
        }
        out.put("error", error);
        return out;
    }
}
