package com.gameroute;

/**
 * Non-{@code Application} entry point. Launching a class that extends
 * {@code javafx.application.Application} directly from a fat jar's
 * manifest (java -jar ...) fails with "JavaFX runtime components are
 * missing" because the JVM checks the main class itself before the
 * classpath is fully set up. Delegating through a plain class avoids that
 * check while {@code mvn javafx:run} continues to launch {@link Main} directly.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Main.main(args);
    }
}
