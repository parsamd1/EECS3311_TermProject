package avsa.infrastructure.market;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reads listing records from a CSV file with a header row; columns are found by name. */
final class ListingCsvReader {

    record Contents(List<RawListing> listings, int skippedRecords) {
    }

    private ListingCsvReader() {
    }

    static Contents read(Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MarketDataUnavailableException("Listings file could not be read: " + file, e);
        }
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        List<List<String>> rows = parse(text);
        if (rows.isEmpty()) {
            return new Contents(List.of(), 0);
        }
        List<String> header = rows.get(0).stream().map(h -> h.strip().toLowerCase(Locale.ROOT)).toList();
        List<RawListing> listings = new ArrayList<>();
        int skipped = 0;
        for (List<String> row : rows.subList(1, rows.size())) {
            if (row.size() != header.size()) {
                skipped++;
            } else {
                listings.add(toListing(header, row));
            }
        }
        return new Contents(listings, skipped);
    }

    private static RawListing toListing(List<String> header, List<String> row) {
        return new RawListing(column(header, row, "source"), column(header, row, "url"),
                column(header, row, "listing_date"), column(header, row, "make"), column(header, row, "model"),
                column(header, row, "year"), column(header, row, "trim"), column(header, row, "odometer_km"),
                column(header, row, "asking_price"), column(header, row, "city"), column(header, row, "province"),
                column(header, row, "title_status"), column(header, row, "accidents"),
                column(header, row, "description"));
    }

    private static String column(List<String> header, List<String> row, String name) {
        int i = header.indexOf(name);
        return i < 0 ? "" : row.get(i).strip();
    }

    /** Splits CSV text into rows, honouring double-quoted fields. Blank lines are ignored. */
    static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n') {
                endRow(rows, row, field);
                row = new ArrayList<>();
            } else if (c != '\r') {
                field.append(c);
            }
        }
        endRow(rows, row, field);
        return rows;
    }

    private static void endRow(List<List<String>> rows, List<String> row, StringBuilder field) {
        row.add(field.toString());
        field.setLength(0);
        if (row.size() > 1 || !row.get(0).isBlank()) {
            rows.add(row);
        }
    }
}
