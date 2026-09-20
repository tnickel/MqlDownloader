package rest;

/**
 * Minimaler JSON-Schreiber ohne externe Abhängigkeit (Java-8-kompatibel).
 * Erzeugt gültiges JSON mit UTF-8-Escapes für Steuerzeichen und Anführungszeichen.
 */
public final class JsonWriter {
    private final StringBuilder sb = new StringBuilder(256);
    private boolean needsComma = false;

    public JsonWriter beginObject() {
        prepareValue();
        sb.append('{');
        needsComma = false;
        return this;
    }

    public JsonWriter endObject() {
        sb.append('}');
        needsComma = true;
        return this;
    }

    public JsonWriter beginArray() {
        prepareValue();
        sb.append('[');
        needsComma = false;
        return this;
    }

    public JsonWriter endArray() {
        sb.append(']');
        needsComma = true;
        return this;
    }

    public JsonWriter name(String name) {
        prepareValue();
        writeString(name);
        sb.append(':');
        needsComma = false;
        return this;
    }

    public JsonWriter value(String value) {
        prepareValue();
        if (value == null) {
            sb.append("null");
        } else {
            writeString(value);
        }
        needsComma = true;
        return this;
    }

    public JsonWriter value(long value) {
        prepareValue();
        sb.append(value);
        needsComma = true;
        return this;
    }

    public JsonWriter value(double value) {
        prepareValue();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            sb.append("null");
        } else {
            sb.append(Double.toString(value));
        }
        needsComma = true;
        return this;
    }

    public JsonWriter value(boolean value) {
        prepareValue();
        sb.append(value);
        needsComma = true;
        return this;
    }

    public JsonWriter nullValue() {
        prepareValue();
        sb.append("null");
        needsComma = true;
        return this;
    }

    /** Zahl oder String, je nachdem ob der Text als Zahl interpretierbar ist. */
    public JsonWriter numberOrString(String raw) {
        if (raw == null) {
            return nullValue();
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return value(raw);
        }
        try {
            return value(Long.parseLong(trimmed));
        } catch (NumberFormatException notIntegral) {
            try {
                return value(Double.parseDouble(trimmed));
            } catch (NumberFormatException notNumeric) {
                return value(raw);
            }
        }
    }

    /** Objekt als vollständigen Wert schreiben (kann null sein). */
    public JsonWriter value(JsonWriter nested) {
        prepareValue();
        if (nested == null) {
            sb.append("null");
        } else {
            sb.append(nested.toString());
        }
        needsComma = true;
        return this;
    }

    private void prepareValue() {
        if (needsComma) {
            sb.append(',');
        }
    }

    private void writeString(String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
