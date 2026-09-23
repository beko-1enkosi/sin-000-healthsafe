package co.wethinkcode.healthsafe;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WardDataLoader {

    private static final String CSV_FILE = "/wards-outdated.csv";

    public List<Ward> loadWards() {
        Map<String, Ward> wardsById = new LinkedHashMap<>();

        try (InputStream inputStream = WardDataLoader.class.getResourceAsStream(CSV_FILE)) {

            if (inputStream == null) {
                throw new IllegalStateException("Could not find " + CSV_FILE);
            }

            try (CSVReader reader = new CSVReader(new InputStreamReader(inputStream))) {
                List<String[]> rows = reader.readAll();

                // Skip the CSV header.
                for (int i = 1; i < rows.size(); i++) {
                    String[] row = rows.get(i);

                    if (row.length < 4) {
                        System.out.println("Skipping malformed row " + (i + 1));
                        continue;
                    }

                    Ward ward = cleanRow(row);

                    if (ward == null) {
                        continue;
                    }

                    if (wardsById.containsKey(ward.wardId())) {
                        System.out.println("Duplicate ward ignored: " + ward.wardId());
                        continue;
                    }

                    wardsById.put(ward.wardId(), ward);
                }

            } catch (CsvException e) {
                throw new RuntimeException("Could not parse ward CSV", e);
            }

        } catch (IOException e) {
            throw new RuntimeException("Could not read ward CSV", e);
        }

        return new ArrayList<>(wardsById.values());
    }

    private Ward cleanRow(String[] row) {
        String wardId = normalizeWardId(row[0]);
        String wing = normalizeText(row[1]);
        String department = normalizeDepartment(row[2]);
        Integer bedsAvailable = normalizeBeds(row[3]);

        if (wardId == null) {
            return null;
        }

        return new Ward(
                wardId,
                wing,
                department,
                bedsAvailable
        );
    }

    private String normalizeWardId(String value) {
        String cleaned = cleanMissingValue(value);

        if (cleaned == null) {
            return null;
        }

        return cleaned.toUpperCase();
    }

    private String normalizeText(String value) {
        String cleaned = cleanMissingValue(value);

        if (cleaned == null) {
            return null;
        }

        cleaned = cleaned.replaceAll("\\s+", " ");

        String[] words = cleaned.toLowerCase().split(" ");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }

            if (!result.isEmpty()) {
                result.append(" ");
            }

            result.append(Character.toUpperCase(word.charAt(0)));

            if (word.length() > 1) {
                result.append(word.substring(1));
            }
        }

        return result.toString();
    }

    private String normalizeDepartment(String value) {
        String department = normalizeText(value);

        if (department == null) {
            return null;
        }

        if (department.equalsIgnoreCase("Pediatrics")) {
            return "Paediatrics";
        }

        if (department.equalsIgnoreCase("Icu")) {
            return "ICU";
        }

        return department;
    }

    private Integer normalizeBeds(String value) {
        String cleaned = cleanMissingValue(value);

        if (cleaned == null) {
            return null;
        }

        try {
            int beds = Integer.parseInt(cleaned);

            if (beds < 0 || beds > 100) {
                System.out.println("Invalid bed count ignored: " + cleaned);
                return null;
            }

            return beds;

        } catch (NumberFormatException e) {
            System.out.println("Non-numeric bed count ignored: " + cleaned);
            return null;
        }
    }

    private String cleanMissingValue(String value) {
        if (value == null) {
            return null;
        }

        String cleaned = value.trim();

        if (cleaned.isBlank()) {
            return null;
        }

        String lower = cleaned.toLowerCase();

        if (
                lower.equals("n/a")
                        || lower.equals("tbd")
                        || lower.equals("unknown")
                        || lower.equals("-")
                        || lower.equals("nan")
        ) {
            return null;
        }

        return cleaned;
    }
}