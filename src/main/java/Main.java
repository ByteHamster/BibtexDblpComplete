import org.jbibtex.BibTeXDatabase;
import org.jbibtex.BibTeXEntry;
import org.jbibtex.BibTeXFormatter;
import org.jbibtex.BibTeXObject;
import org.jbibtex.BibTeXParser;
import org.jbibtex.Key;
import org.jbibtex.ParseException;
import org.jbibtex.StringValue;
import org.jbibtex.Value;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class Main {
    private static final ArrayList<String> updated = new ArrayList<>();
    private static final ArrayList<String> alreadyGood = new ArrayList<>();
    private static final ArrayList<String> skippedManually = new ArrayList<>();
    private static final ArrayList<String> notFound = new ArrayList<>();

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("No argument given");
            return;
        }
        String filename = args[0];
        BibTeXDatabase database;
        try {
            BibTeXParser parser = new BibTeXParser();
            FileReader reader = new FileReader(filename);
            database = parser.parse(reader);
        } catch (ParseException | FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        BibTeXDatabase newDatabase = new BibTeXDatabase();
        List<BibTeXObject> objects = database.getObjects();
        int i = 0;
        for (; i < objects.size(); i++) {
            BibTeXObject object = objects.get(i);
            if (!(object instanceof BibTeXEntry)) {
                newDatabase.addObject(object); // Preserve comments etc
                continue;
            }
            System.out.println("\n\n");
            System.out.println("BibTeXObject " + i + " of " + objects.size());
            BibTeXEntry entry = (BibTeXEntry) object;
            String title = get(entry, BibTeXEntry.KEY_TITLE);
            try {
                System.out.println("Loading from dblp [condensed]: " + title);
                BibTeXEntry betterEntry = tryImprove(entry);
                if (format(entry).equals(format(betterEntry))) {
                    System.out.println("Is already optimized.");
                    newDatabase.addObject(betterEntry);
                    alreadyGood.add(title);
                    continue;
                }
                preview(entry, betterEntry);
                checkDifference(entry, betterEntry, BibTeXEntry.KEY_YEAR);
                checkDifference(entry, betterEntry, BibTeXEntry.KEY_PAGES);
                System.out.print("Apply this change? [Ynw] ");
                String read = read();
                if (read.equals("y") || read.equals("")) {
                    newDatabase.addObject(betterEntry);
                    updated.add(title);
                } else if (read.equals("w")) {
                    newDatabase.addObject(entry);
                    System.out.println("Writing changes early.");
                    skippedManually.add(title);
                    break;
                } else {
                    newDatabase.addObject(entry);
                    skippedManually.add(title);
                }
            } catch (JSONException | IOException | ParseException e) {
                e.printStackTrace();
                newDatabase.addObject(object);
                notFound.add(title);
            }
        }
        // Re-add remaining objects in case of a break
        for (; i < objects.size(); i++) {
            newDatabase.addObject(objects.get(i));
        }

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

    private static String get(BibTeXEntry entry, Key key) {
        Value title = entry.getField(key);
        if (title == null) {
            return null;
        }
        return title.toUserString();
    }

    private static BibTeXEntry tryImprove(BibTeXEntry entry) throws JSONException, IOException, ParseException {
        String apiResult = httpRequest("https://dblp.org/search/publ/api?format=json&q="
                        + URLEncoder.encode(get(entry, BibTeXEntry.KEY_TITLE), "UTF-8"));
        JSONObject result = new JSONObject(apiResult)
                .getJSONObject("result")
                .getJSONObject("hits")
                .getJSONArray("hit")
                .getJSONObject(0)
                .getJSONObject("info");
        String key = result.getString("key");
        String condensed = httpRequest("https://dblp.org/rec/" + key + ".bib?param=0"); // 0=condensed, 1=standard, 2=crossref
        BibTeXEntry condensedEntry = parseSingle(condensed);
        BibTeXEntry newEntry = new BibTeXEntry(condensedEntry.getType(), entry.getKey());
        newEntry.addAllFields(condensedEntry.getFields());
        if (result.has("doi")) {
            String doi = result.getString("doi");
            newEntry.addField(BibTeXEntry.KEY_DOI, new StringValue(doi, StringValue.Style.BRACED));
        } else {
            System.out.println("Warning: No doi found");
        }
        return newEntry;
    }

    private static void checkDifference(BibTeXEntry entry, BibTeXEntry newEntry, Key key) {
        if (get(entry, key) != null && !get(entry, key).equalsIgnoreCase(get(newEntry, key))) {
            System.err.println("\u001B[31m" + "Warning: Different " + key.getValue() + "\u001B[0m");
        }
    }

    private static BibTeXEntry parseSingle(String entry) throws ParseException {
        BibTeXParser parser = new BibTeXParser();
        StringReader reader = new StringReader(entry);
        BibTeXDatabase database = parser.parse(reader);
        return database.getEntries().entrySet().iterator().next().getValue();
    }

    private static String httpRequest(String url) throws IOException {
        return new Scanner(new URL(url).openStream()).useDelimiter("\\A").next();
    }

    private static void preview(BibTeXEntry entry1, BibTeXEntry entry2) throws IOException {
        Columns columns = new Columns(2);
        columns.columnSeparator = " | ";
        columns.maxColumnSize = 60;
        columns.addLine("Original entry", "Suggested change");
        columns.addLine("-----", "-----");
        columns.writeColumn(format(entry1), 2, 0);
        columns.writeColumn(format(entry2), 2, 1);
        System.out.println(columns);
    }

    private static String format(BibTeXEntry entry) throws IOException {
        BibTeXDatabase databaseTemp = new BibTeXDatabase();
        databaseTemp.addObject(entry);
        BibTeXFormatter formatter = new BibTeXFormatter();
        StringWriter writer = new StringWriter();
        formatter.format(databaseTemp, writer);
        return writer.toString().replace("\t", "    ");
    }

    private static String read() {
        Scanner scanner = new Scanner(System.in);
        return scanner.nextLine().toLowerCase(Locale.ROOT);
    }
}
