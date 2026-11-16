package ai.indra.desktop;

/**
 * Launcher stub.
 *
 * <p>A JavaFX application cannot be started from a shaded jar when the main class
 * extends {@code Application} on some JDK versions ("JavaFX runtime components are
 * missing"). This tiny class is the jar's real main class and hands over to the
 * JavaFX application.</p>
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        DesktopApp.main(args);
    }
}
