package dictionary.cli.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CommandLineTable {
    private final String horizontalSeperate = "-";
    private String verticalSeperate;
    private String joinSeperate;
    private boolean rightAlign;
    private String[] headers;
    private final List<String[]> rows;

    public CommandLineTable() {
        setShowVerticalLines(true);
        rows = new ArrayList<>();
    }

    public void setRightAlign(final boolean rightAlign) {
        this.rightAlign = rightAlign;
    }

    public void setShowVerticalLines(final boolean showVerticalLines) {
        verticalSeperate = showVerticalLines ? "|" : "";
        joinSeperate = showVerticalLines ? "+" : " ";
    }

    public void setHeaders(final String... headers) {
        this.headers = headers;
    }

    public void addRow(final String... cells) {
        rows.add(cells);
    }

    public void show() {
        int[] maxWidths = (headers != null) ? Arrays.stream(headers).mapToInt(String::length).toArray() : null;

        for (final String[] cells : rows) {
            if (maxWidths == null) {
                maxWidths = new int[cells.length];
            }

            if (cells.length != maxWidths.length) {
                throw new IllegalArgumentException("Number of row-cells and headers should be consistent");
            }

            for (int i = 0; i < cells.length; i++) {
                maxWidths[i] = Math.max(maxWidths[i], cells[i].length());
            }
        }

        if (headers != null) {
            printLine(maxWidths);
            printRow(headers, maxWidths);
            printLine(maxWidths);
        }

        for (final String[] cells : rows) {
            printRow(cells, maxWidths);
        }

        if (headers != null) {
            printLine(maxWidths);
        }
    }

    private void printLine(final int[] columnWidths) {
        for (int i = 0; i < columnWidths.length; i++) {
            final String line = String.join("", Collections.nCopies(columnWidths[i] +
                    verticalSeperate.length() + 1, horizontalSeperate));
            System.out.print(joinSeperate + line + (i == columnWidths.length - 1 ? joinSeperate : ""));
        }

        System.out.println();
    }

    private void printRow(final String[] cells, final int[] maxWidths) {
        for (int i = 0; i < cells.length; i++) {
            final String s = cells[i];
            final String verStrTemp = (i == cells.length - 1) ? verticalSeperate : "";

            if (rightAlign) {
                System.out.printf("%s %" + maxWidths[i] + "s %s", verticalSeperate, s, verStrTemp);
            } else {
                System.out.printf("%s %-" + maxWidths[i] + "s %s", verticalSeperate, s, verStrTemp);
            }
        }

        System.out.println();
    }
}