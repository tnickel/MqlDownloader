package rest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonWriterTest {

    @Test
    void writesSimpleObject() {
        JsonWriter w = new JsonWriter();
        w.beginObject()
            .name("name").value("World PEACE")
            .name("subscribers").value(42)
            .name("active").value(true)
            .name("note").nullValue()
        .endObject();
        assertEquals("{\"name\":\"World PEACE\",\"subscribers\":42,\"active\":true,\"note\":null}",
                w.toString());
    }

    @Test
    void escapesQuotesAndControlChars() {
        JsonWriter w = new JsonWriter();
        w.value("Preis \"ok\"\n\\C:\\temp");
        assertEquals("\"Preis \\\"ok\\\"\\n\\\\C:\\\\temp\"", w.toString());
    }

    @Test
    void escapesUnicodeControlCharacters() {
        JsonWriter w = new JsonWriter();
        w.value("a\u0001b");
        assertEquals("\"a\\u0001b\"", w.toString());
    }

    @Test
    void nestsArraysAndObjects() {
        JsonWriter w = new JsonWriter();
        w.beginObject()
            .name("items").beginArray();
        w.beginObject().name("id").value(1).endObject();
        w.beginObject().name("id").value(2).endObject();
        w.endArray().endObject();
        assertEquals("{\"items\":[{\"id\":1},{\"id\":2}]}", w.toString());
    }

    @Test
    void numberOrStringParsesNumbersKeepsText() {
        JsonWriter w = new JsonWriter();
        w.beginObject()
            .name("a").numberOrString("123")
            .name("b").numberOrString("1.5")
            .name("c").numberOrString("EURUSD")
            .name("d").numberOrString(null)
        .endObject();
        assertEquals("{\"a\":123,\"b\":1.5,\"c\":\"EURUSD\",\"d\":null}", w.toString());
    }

    @Test
    void encodesNonAsciiAsUtf8() {
        JsonWriter w = new JsonWriter();
        w.value("Gr\u00fc\u00dfe");
        String json = w.toString();
        assertTrue(json.contains("Gr\u00fc\u00dfe"));
        assertEquals("\"Gr\u00fc\u00dfe\"", json);
    }
}
