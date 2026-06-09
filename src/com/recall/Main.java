package com.recall;

/**
 * Main - Application entry point
 *
 * Purpose: Simple entry point that creates and starts the game.
 * All game logic is managed by the Game class.
 *
 * Networking (Phase 2.5 — basic 1v1 over UDP). Optional command-line args:
 *
 *   (no args)            → singleplayer (networking disabled)
 *   host [port]          → host a 1v1 session (default port {@link Config#NET_DEFAULT_PORT})
 *   join &lt;ip&gt; [port]     → join a session at the given host IP
 *
 * Examples (Gradle passes args through --args):
 *
 *   ./gradlew.bat run --args="host"
 *   ./gradlew.bat run --args="join 192.168.1.42"
 *   ./gradlew.bat run --args="join 192.168.1.42 7777"
 */
public class Main {
    public static void main(String[] args) {
        // Create game instance
        Game game = new Game(Config.WINDOW_TITLE, Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);

        // Optional networking setup from command-line args
        configureNetworking(game, args);

        // Start game loop
        game.run();
    }

    /**
     * Parse optional networking args and configure the game accordingly.
     * Unknown/invalid args fall back to singleplayer with a printed hint.
     */
    private static void configureNetworking(Game game, String[] args) {
        if (args.length == 0) return;  // singleplayer

        String mode = args[0].toLowerCase();
        int port = Config.NET_DEFAULT_PORT;

        switch (mode) {
            case "host":
                if (args.length >= 2) port = parsePort(args[1], port);
                game.configureNetwork("host", null, port);
                break;

            case "join":
                if (args.length < 2) {
                    System.err.println("Usage: join <host-ip> [port]");
                    return;
                }
                String ip = args[1];
                if (args.length >= 3) port = parsePort(args[2], port);
                game.configureNetwork("join", ip, port);
                break;

            default:
                System.err.println("Unknown mode '" + args[0]
                        + "'. Use: host [port] | join <ip> [port]  (no args = singleplayer)");
                break;
        }
    }

    /** Parse a port number, returning {@code fallback} if it is not a valid integer. */
    private static int parsePort(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            System.err.println("Invalid port '" + s + "', using " + fallback);
            return fallback;
        }
    }
}
