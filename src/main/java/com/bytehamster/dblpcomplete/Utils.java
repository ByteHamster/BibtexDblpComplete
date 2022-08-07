package com.bytehamster.dblpcomplete;

import java.io.IOException;
import java.net.URL;
import java.util.Locale;
import java.util.Scanner;

public abstract class Utils {
    public static String httpRequest(String url) throws IOException {
        return new Scanner(new URL(url).openStream()).useDelimiter("\\A").next();
    }

    public static String read() {
        Scanner scanner = new Scanner(System.in);
        return scanner.nextLine().toLowerCase(Locale.ROOT);
    }
}
