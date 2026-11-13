package com.speedrush.config;

/**
 * Runtime settings. Precedence: command-line flag > environment variable > default.
 *
 *   --console        text menu instead of the web server
 *   --port=8080      (env SPEEDRUSH_PORT)
 *   --db=file.db     (env SPEEDRUSH_DB)
 */
public record AppConfig(int port, String dbPath, boolean console) {

    public static final int DEFAULT_PORT = 8080;
    public static final String DEFAULT_DB = "speedrush.db";

    public static String usage() {
        return """
                Usage: java -jar speedrush.jar [--console] [--port=8080] [--db=speedrush.db]
                  --console     use the text menu instead of the web server
                  --port=N      web server port (env SPEEDRUSH_PORT, default 8080)
                  --db=PATH     SQLite file (env SPEEDRUSH_DB, default speedrush.db)""";
    }

    /** @throws IllegalArgumentException with a user-friendly message for bad input */
    public static AppConfig parse(String[] args) {
        int port = parsePort(System.getenv("SPEEDRUSH_PORT"), DEFAULT_PORT);
        String db = System.getenv("SPEEDRUSH_DB");
        if (db == null || db.isBlank()) db = DEFAULT_DB;
        boolean console = false;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--console")) {
                console = true;
            } else if (a.startsWith("--port=")) {
                port = parsePort(a.substring(7), -1);
            } else if (a.equals("--port") && i + 1 < args.length) {
                port = parsePort(args[++i], -1);
            } else if (a.startsWith("--db=")) {
                db = a.substring(5);
            } else if (a.equals("--db") && i + 1 < args.length) {
                db = args[++i];
            } else {
                throw new IllegalArgumentException("Unknown option: " + a);
            }
        }
        if (db.isBlank()) throw new IllegalArgumentException("--db needs a file name.");
        return new AppConfig(port, db, console);
    }

    private static int parsePort(String text, int fallback) {
        if (text == null || text.isBlank()) {
            if (fallback < 0) throw new IllegalArgumentException("--port needs a number.");
            return fallback;
        }
        try {
            int p = Integer.parseInt(text.trim());
            if (p >= 1 && p <= 65535) return p;
        } catch (NumberFormatException ignored) {
            // fall through
        }
        throw new IllegalArgumentException("Port must be a number between 1 and 65535, got: " + text);
    }
}
