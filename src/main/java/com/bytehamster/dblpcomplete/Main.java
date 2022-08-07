package com.bytehamster.dblpcomplete;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }
        Format format = Format.CONDENSED;
        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--condensed":
                    format = Format.CONDENSED;
                    break;
                case "--condensed-with-doi":
                    format = Format.CONDENSED_WITH_DOI;
                    break;
                case "--standard":
                    format = Format.STANDARD;
                    break;
                case "--crossref":
                    format = Format.CROSSREF;
                    break;
                default:
                    printUsage();
                    return;

            }
        }
        String filename = args[args.length - 1];
        DblpComplete dblpComplete = new DblpComplete(format);
        dblpComplete.read(filename);
        dblpComplete.complete();
        dblpComplete.write(filename);
        dblpComplete.printStats();
    }

    private static void printUsage() {
        System.err.println("Usage: java -jar BibtexDblpComplete [FORMAT] FILENAME");
        System.err.println("FORMAT can be one of --condensed, --condensed-with-doi, --standard, --crossref");
    }
}
