package com.umlgen.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lector mínimo de multipart/form-data (RFC 7578) para los archivos subidos desde el navegador. */
final class MultipartParser {

    record Part(String name, String filename, byte[] content) {
    }

    private static final Pattern BOUNDARY = Pattern.compile("boundary=(?:\"([^\"]+)\"|([^;\\s]+))");
    private static final Pattern NAME = Pattern.compile("\\bname=\"([^\"]*)\"");
    private static final Pattern FILENAME = Pattern.compile("\\bfilename=\"([^\"]*)\"");

    private MultipartParser() {
    }

    static String boundary(String contentType) throws IOException {
        if (contentType == null) {
            throw new IOException("Falta Content-Type");
        }
        Matcher m = BOUNDARY.matcher(contentType);
        if (!m.find()) {
            throw new IOException("Content-Type multipart sin boundary");
        }
        return m.group(1) != null ? m.group(1) : m.group(2);
    }

    static List<Part> parse(byte[] body, String boundary) throws IOException {
        byte[] delimiter = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        List<Part> parts = new ArrayList<>();
        int pos = indexOf(body, delimiter, 0);
        if (pos < 0) {
            throw new IOException("Cuerpo multipart inválido");
        }
        while (true) {
            int start = pos + delimiter.length;
            if (start + 1 < body.length && body[start] == '-' && body[start + 1] == '-') {
                break; // fin
            }
            start = skipCrlf(body, start);
            int headerEnd = indexOf(body, "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), start);
            if (headerEnd < 0) {
                throw new IOException("Encabezado multipart incompleto");
            }
            String headers = new String(body, start, headerEnd - start, StandardCharsets.UTF_8);
            int contentStart = headerEnd + 4;
            int next = indexOf(body, delimiter, contentStart);
            if (next < 0) {
                throw new IOException("Multipart sin delimitador final");
            }
            int contentEnd = next - 2; // CRLF antes del delimitador
            if (contentEnd < contentStart) {
                contentEnd = contentStart;
            }
            byte[] content = java.util.Arrays.copyOfRange(body, contentStart, contentEnd);
            parts.add(new Part(group(NAME, headers), group(FILENAME, headers), content));
            pos = next;
        }
        return parts;
    }

    private static String group(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : null;
    }

    private static int skipCrlf(byte[] b, int i) {
        if (i + 1 < b.length && b[i] == '\r' && b[i + 1] == '\n') {
            return i + 2;
        }
        return i;
    }

    static int indexOf(byte[] data, byte[] pattern, int from) {
        outer:
        for (int i = Math.max(0, from); i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
