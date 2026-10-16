package rest;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.*;

/** Strict JSON object and known field types; empty body keeps protocol defaults. */
final class UpdateRequestParser {
    private static final Gson JSON=new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private UpdateRequestParser() { }
    static Map<String,Object> parse(String text) {
        Map<String,Object> values=new LinkedHashMap<>();
        if(text==null || text.trim().isEmpty()) return values;
        try {
            JsonReader reader=new JsonReader(new StringReader(text)); reader.setStrictness(Strictness.STRICT);
            reader.beginObject(); Set<String> seen=new HashSet<>();
            while(reader.hasNext()) {
                String key=reader.nextName(); if(!seen.add(key)) throw new IllegalArgumentException("Doppeltes Feld: "+key);
                JsonElement value=JSON.fromJson(reader,JsonElement.class);
                if("tradelisten".equals(key)) {
                    if(!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("tradelisten muss Boolean sein");
                    values.put(key,value.getAsBoolean());
                } else if("target".equals(key) || "katalogMaxAlterH".equals(key)) {
                    if(!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException(key+" muss ganzzahlig sein");
                    BigDecimal n=value.getAsBigDecimal(); int number=n.intValueExact();
                    if(number< ("target".equals(key)?1:0)) throw new IllegalArgumentException(key+" liegt ausserhalb des Bereichs");
                    values.put(key,number);
                }
            }
            reader.endObject();
            if(reader.peek()!=JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Zusatzinhalt nach JSON-Objekt");
            return values;
        } catch(Exception e) { throw new IllegalArgumentException("Ungueltiger Update-Body: "+e.getMessage(),e); }
    }
}
