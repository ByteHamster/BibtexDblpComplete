package com.bytehamster.dblpcomplete;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Locale;
import java.util.Scanner;

public abstract class Utils {
    public static String httpRequest(String url) throws IOException {
        URLConnection conn = new URL(url).openConnection();
        conn.setRequestProperty("User-Agent", "BibtexDblpComplete (https://github.com/ByteHamster/BibtexDblpComplete/)");
        return new Scanner(conn.getInputStream()).useDelimiter("\\A").next();
    }

    public static String read() {
        Scanner scanner = new Scanner(System.in);
        return scanner.nextLine().toLowerCase(Locale.ROOT);
    }
}
