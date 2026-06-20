package dev.echo.engine.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SimpleJson {
    private SimpleJson() { }

    public static Object parse(String json) {
        Parser parser = new Parser(json == null ? "" : json);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.end()) throw parser.error("Trailing data");
        return value;
    }

    public static Map<String, Object> parseObject(String json) {
        Object value = parse(json);
        if (!(value instanceof Map<?, ?> raw)) throw new IllegalArgumentException("JSON root is not an object");
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        raw.forEach((key, item) -> out.put(String.valueOf(key), item));
        return out;
    }

    public static Map<String, Object> readObject(Path path) throws IOException {
        return parseObject(Files.readString(path, StandardCharsets.UTF_8));
    }

    public static void write(Path path, Object value) throws IOException {
        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent != null) Files.createDirectories(parent);
        Files.writeString(path, stringify(value) + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    public static String stringify(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(out, value, 0);
        return out.toString();
    }

    private static void writeValue(StringBuilder out, Object value, int depth) {
        if (value == null) { out.append("null"); return; }
        if (value instanceof String text) { writeString(out, text); return; }
        if (value instanceof Number || value instanceof Boolean) { out.append(value); return; }
        if (value instanceof Map<?, ?> map) {
            out.append('{');
            if (!map.isEmpty()) {
                boolean first = true;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!first) out.append(',');
                    out.append('\n'); indent(out, depth + 1);
                    writeString(out, String.valueOf(entry.getKey())); out.append(": ");
                    writeValue(out, entry.getValue(), depth + 1);
                    first = false;
                }
                out.append('\n'); indent(out, depth);
            }
            out.append('}');
            return;
        }
        if (value instanceof Collection<?> collection) {
            out.append('[');
            if (!collection.isEmpty()) {
                boolean first = true;
                for (Object item : collection) {
                    if (!first) out.append(',');
                    out.append('\n'); indent(out, depth + 1);
                    writeValue(out, item, depth + 1);
                    first = false;
                }
                out.append('\n'); indent(out, depth);
            }
            out.append(']');
            return;
        }
        writeString(out, String.valueOf(value));
    }

    private static void indent(StringBuilder out, int depth) { out.append("  ".repeat(Math.max(0, depth))); }
    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) out.append(String.format("\\u%04x", (int) ch)); else out.append(ch);
                }
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String text; private int index;
        Parser(String text) { this.text = text; }
        boolean end() { return index >= text.length(); }
        void skipWhitespace() { while (!end() && Character.isWhitespace(text.charAt(index))) index++; }
        RuntimeException error(String message) { return new IllegalArgumentException(message + " at character " + index); }
        Object readValue() {
            skipWhitespace(); if (end()) throw error("Expected value");
            return switch (text.charAt(index)) {
                case '{' -> readObject(); case '[' -> readArray(); case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE); case 'f' -> readLiteral("false", Boolean.FALSE);
                case 'n' -> readLiteral("null", null); default -> readNumber();
            };
        }
        Map<String,Object> readObject() {
            index++; LinkedHashMap<String,Object> out=new LinkedHashMap<>(); skipWhitespace();
            if (consume('}')) return out;
            while (true) {
                skipWhitespace(); if (end() || text.charAt(index)!='"') throw error("Expected object key");
                String key=readString(); skipWhitespace(); expect(':'); out.put(key, readValue()); skipWhitespace();
                if (consume('}')) return out; expect(',');
            }
        }
        List<Object> readArray() {
            index++; ArrayList<Object> out=new ArrayList<>(); skipWhitespace();
            if (consume(']')) return out;
            while (true) { out.add(readValue()); skipWhitespace(); if (consume(']')) return out; expect(','); }
        }
        String readString() {
            expect('"'); StringBuilder out=new StringBuilder();
            while (!end()) {
                char ch=text.charAt(index++); if (ch=='"') return out.toString();
                if (ch!='\\') { out.append(ch); continue; }
                if (end()) throw error("Unterminated escape"); char esc=text.charAt(index++);
                switch (esc) {
                    case '"','\\','/' -> out.append(esc); case 'b' -> out.append('\b'); case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n'); case 'r' -> out.append('\r'); case 't' -> out.append('\t');
                    case 'u' -> { if (index+4>text.length()) throw error("Bad unicode escape"); out.append((char)Integer.parseInt(text.substring(index,index+4),16)); index+=4; }
                    default -> throw error("Invalid escape: " + esc);
                }
            }
            throw error("Unterminated string");
        }
        Object readLiteral(String literal,Object value) { if (!text.startsWith(literal,index)) throw error("Expected "+literal); index+=literal.length(); return value; }
        Number readNumber() {
            int start=index; if (!end() && text.charAt(index)=='-') index++;
            while (!end() && Character.isDigit(text.charAt(index))) index++;
            boolean decimal=false;
            if (!end() && text.charAt(index)=='.') { decimal=true; index++; while (!end()&&Character.isDigit(text.charAt(index))) index++; }
            if (!end() && (text.charAt(index)=='e'||text.charAt(index)=='E')) { decimal=true; index++; if (!end()&&(text.charAt(index)=='+'||text.charAt(index)=='-')) index++; while(!end()&&Character.isDigit(text.charAt(index))) index++; }
            if (start==index) throw error("Expected number"); String number=text.substring(start,index);
            try { return decimal ? Double.parseDouble(number) : Long.parseLong(number); } catch (NumberFormatException e) { throw error("Invalid number"); }
        }
        boolean consume(char expected) { skipWhitespace(); if (!end()&&text.charAt(index)==expected){index++;return true;} return false; }
        void expect(char expected) { skipWhitespace(); if (end()||text.charAt(index)!=expected) throw error("Expected '"+expected+"'"); index++; }
    }
}
