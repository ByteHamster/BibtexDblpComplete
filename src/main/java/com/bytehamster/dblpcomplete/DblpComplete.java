package com.bytehamster.dblpcomplete;

import org.jbibtex.BibTeXDatabase;
import org.jbibtex.BibTeXEntry;
import org.jbibtex.BibTeXFormatter;
import org.jbibtex.BibTeXObject;
import org.jbibtex.BibTeXParser;
import org.jbibtex.Key;
import org.jbibtex.ParseException;
import org.jbibtex.StringValue;
import org.jbibtex.Value;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class DblpComplete {
    private final ArrayList<String> updated = new ArrayList<>();
    private final ArrayList<String> alreadyGood = new ArrayList<>();
    private final ArrayList<String> skippedManually = new ArrayList<>();
    private final ArrayList<String> notFound = new ArrayList<>();
    private final Format format;
    private final BibTeXDatabase newDatabase = new BibTeXDatabase();
    private BibTeXDatabase database;

    public DblpComplete(Format format) {
        this.format = format;
    }

    public void read(String filename) {
        try {
            BibTeXParser parser = new BibTeXParser();
            FileReader reader = new FileReader(filename);
            database = parser.parse(reader);
        } catch (ParseException | FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void complete() {
        List<BibTeXObject> objects = database.getObjects();
        int i = 0;
        for (; i < objects.size(); i++) {
            BibTeXObject object = objects.get(i);
            if (!(object instanceof BibTeXEntry)) {
                newDatabase.addObject(object);
                continue;
            }
            System.out.println("\n");
            System.out.println("BibTeXObject " + i + " of " + objects.size());
            BibTeXEntry entry = (BibTeXEntry) object;
            String title = get(entry, BibTeXEntry.KEY_TITLE);
            try {
                System.out.println("Loading from dblp: " + title);
                Thread.sleep(3000);
                BibTeXEntry betterEntry = tryImprove(entry);
                if (betterEntry == null) {
                    notFound.add(title);
                    newDatabase.addObject(entry);
                    continue;
                }
                if (format(entry).equals(format(betterEntry))) {
                    System.out.println("Is already optimized.");
                    alreadyGood.add(title);
                    newDatabase.addObject(betterEntry);
                    continue;
                }
                preview(entry, betterEntry);
                warnIfDifferent(entry, betterEntry, BibTeXEntry.KEY_YEAR);
                warnIfDifferent(entry, betterEntry, BibTeXEntry.KEY_PAGES);
                System.out.print("Apply this change? [Y]es, [n]o, [w]rite&close ");
                String read = Utils.read();
                if (read.equals("y") || read.equals("")) {
                    newDatabase.addObject(betterEntry);
                    updated.add(title);
                } else if (read.equals("w")) {
                    newDatabase.addObject(entry);
                    skippedManually.add(title);
                    break;
                } else {
                    newDatabase.addObject(entry);
                    skippedManually.add(title);
                }
            } catch (JSONException | InterruptedException | IOException | ParseException e) {
                e.printStackTrace();
                newDatabase.addObject(object);
                notFound.add(title);
                if (e.getMessage().contains("429")) {
                    System.out.print("Try next one? [Y]es, [w]rite&close ");
                    String read = Utils.read();
                    if (read.equals("w")) {
                        break;
                    }
                }
            }
        }
        // Re-add remaining objects in case of a break
        for (; i < objects.size(); i++) {
            newDatabase.addObject(objects.get(i));
        }
    }

    public void write(String filename) {
        try {
            StringWriter writer = new StringWriter();
            BibTeXFormatter formatter = new BibTeXFormatter();
            formatter.format(newDatabase, writer);

            String formatted = writer.toString();
            formatted = formatted.replace("\t", "  ");
            FileWriter out = new FileWriter(filename);
            out.write(formatted);
            out.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void printStats() {
        System.out.println("\n\n");
        System.out.println("Updated: ");
        for (String s : updated) {
            System.out.println("  " + s.replace("\n", ""));
        }
        System.out.println("Skipped because they already were good: ");
        for (String s : alreadyGood) {
            System.out.println("  " + s.replace("\n", ""));
        }
        System.out.println("Skipped manually: ");
        for (String s : skippedManually) {
            System.out.println("  " + s.replace("\n", ""));
        }
        System.out.println("Not found: ");
        for (String s : notFound) {
            System.out.println("  " + s.replace("\n", ""));
        }
    }

    private String get(BibTeXEntry entry, Key key) {
        Value title = entry.getField(key);
        if (title == null) {
            return null;
        }
        return title.toUserString();
    }

    private BibTeXEntry tryImprove(BibTeXEntry entry) throws JSONException, IOException, ParseException {
        String apiResult = Utils.httpRequest("https://dblp.org/search/publ/api?format=json&q="
                + URLEncoder.encode(get(entry, BibTeXEntry.KEY_TITLE), "UTF-8"));
        JSONObject hitParent = new JSONObject(apiResult)
                .getJSONObject("result")
                .getJSONObject("hits");
        if (!hitParent.has("hit")) {
            System.err.println("No hits");
            return null;
        }
        JSONArray hits = hitParent.getJSONArray("hit");
        JSONObject result = hits.getJSONObject(0).getJSONObject("info"); // Default
        for (int i = 0; i < hits.length(); i++) {
            // If there is multiple search results, use the first that has the same year
            if (get(entry, BibTeXEntry.KEY_YEAR).equals(
                    hits.getJSONObject(i).getJSONObject("info").getString("year"))) {
                result = hits.getJSONObject(i).getJSONObject("info");
                break;
            }
        }
        String key = result.getString("key");
        int param;
        switch (format) {
            case CROSSREF:
                param = 2;
            break;
            case STANDARD:
                param = 1;
            break;
            case CONDENSED: // Fall-through
            case CONDENSED_WITH_DOI:
                param = 0;
                break;
            default:
                throw new RuntimeException("Unknown format");
        }
        String condensed = Utils.httpRequest("https://dblp.org/rec/" + key + ".bib?param=" + param);
        BibTeXEntry condensedEntry = parseSingle(condensed);
        BibTeXEntry newEntry = new BibTeXEntry(condensedEntry.getType(), entry.getKey());
        newEntry.addAllFields(condensedEntry.getFields());
        if (format == Format.CONDENSED_WITH_DOI) {
            if (result.has("doi")) {
                String doi = result.getString("doi");
                newEntry.addField(BibTeXEntry.KEY_DOI, new StringValue(doi, StringValue.Style.BRACED));
            } else {
                System.out.println("Warning: No doi found. Formatting without doi.");
            }
        }
        return newEntry;
    }

    private void warnIfDifferent(BibTeXEntry entry, BibTeXEntry newEntry, Key key) {
        if (get(entry, key) != null && !get(entry, key).equalsIgnoreCase(get(newEntry, key))) {
            System.err.println("\u001B[31m" + "Warning: Different " + key.getValue() + "\u001B[0m");
        }
    }

    private BibTeXEntry parseSingle(String entry) throws ParseException {
        BibTeXParser parser = new BibTeXParser();
        StringReader reader = new StringReader(entry);
        BibTeXDatabase database = parser.parse(reader);
        return database.getEntries().entrySet().iterator().next().getValue();
    }

    private void preview(BibTeXEntry entry1, BibTeXEntry entry2) throws IOException {
        Columns columns = new Columns(2);
        columns.columnSeparator = " | ";
        columns.maxColumnSize = 100;
        columns.addLine("Original entry", "Suggested change");
        columns.addLine("-----", "-----");
        columns.writeColumn(format(entry1), 2, 0);
        columns.writeColumn(format(entry2), 2, 1);
        System.out.println(columns);
    }

    private String format(BibTeXEntry entry) throws IOException {
        BibTeXDatabase databaseTemp = new BibTeXDatabase();
        databaseTemp.addObject(entry);
        BibTeXFormatter formatter = new BibTeXFormatter();
        StringWriter writer = new StringWriter();
        formatter.format(databaseTemp, writer);
        return writer.toString().replace("\t", "    ");
    }
}
