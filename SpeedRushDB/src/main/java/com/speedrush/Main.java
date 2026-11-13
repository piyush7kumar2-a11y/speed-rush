package com.speedrush;

import com.speedrush.config.AppConfig;
import com.speedrush.db.Database;
import com.speedrush.ui.ConsoleMenu;
import com.speedrush.web.WebServer;

import java.io.IOException;
import java.net.BindException;
import java.sql.SQLException;

public class Main {
    public static void main(String[] args) {
        if (args.length > 0 && (args[0].equals("--help") || args[0].equals("-h"))) {
            System.out.println(AppConfig.usage());
            return;
        }

        AppConfig config;
        try {
            config = AppConfig.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println(AppConfig.usage());
            System.exit(2);
            return;
        }

        try {
            Database.configure(config.dbPath());
            Database.init();
        } catch (SQLException e) {
            System.err.println("Could not initialise database '" + config.dbPath() + "': " + e.getMessage());
            System.exit(1);
            return;
        }

        if (config.console()) {
            new ConsoleMenu().run();
            return;
        }

        WebServer server = new WebServer();
        try {
            int port = server.start(config.port());
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
            System.out.println("Speed Rush is running -> open http://localhost:" + port
                    + " in your browser (Ctrl+C to stop)");
            System.out.println("Database file: " + config.dbPath());
        } catch (BindException e) {
            System.err.println("Port " + config.port() + " is already in use. Try: --port=" + (config.port() + 1));
            System.exit(1);
        } catch (IOException | SQLException e) {
            System.err.println("Could not start web server: " + e.getMessage());
            System.exit(1);
        }
    }
}
