import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Based on: https://stackoverflow.com/a/26674200/4193263
public class Columns {
    private final List<List<String>> lines = new ArrayList<>();
    private final List<Integer> maxLengths = new ArrayList<>();
    private final int numColumns;
    String columnSeparator = " ";
    int maxColumnSize = Integer.MAX_VALUE;

    public Columns(int numColumns) {
        this.numColumns = numColumns;
        for (int column = 0; column < numColumns; column++) {
            maxLengths.add(0);
        }
    }

    public void addLine(String... line) {
        if (numColumns != line.length) {
            throw new IllegalArgumentException();
        }
        for (int column = 0; column < numColumns; column++) {
            maxLengths.set(column, Math.max(maxLengths.get(column), line[column].length()));
        }
        lines.add(Arrays.asList(line));
    }

    public void writeColumn(String l, int row, int column) {
        if (column > numColumns) {
            throw new IllegalArgumentException();
        }
        String[] addLines = l.split("\n");
        for (String s : addLines) {
            maxLengths.set(column, Math.max(maxLengths.get(column), s.length()));
        }
        while (lines.size() < row + addLines.length) {
            String[] newR = new String[numColumns];
            Arrays.fill(newR, "");
            lines.add(Arrays.asList(newR));
        }
        for (String s : addLines) {
            lines.get(row).set(column, s);
            row++;
        }
    }

    public String toString() {
        StringBuilder result = new StringBuilder();
        for (List<String> line : lines) {
            for (int i = 0; i < numColumns; i++) {
                result.append(pad(line.get(i), Math.min(maxColumnSize, maxLengths.get(i))));
                if (i != numColumns - 1) {
                    result.append(columnSeparator);
                }
            }
            result.append(System.lineSeparator());
        }
        return result.toString();
    }

    private String pad(String word, int newLength){
        if (word.length() > newLength) {
            return word.substring(0, newLength-1) + "…";
        }
        return word + new String(new char[newLength - word.length()]).replace("\0", " ");
    }
}