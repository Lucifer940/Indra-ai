package ai.indra.android.ui;

import android.view.View;

import ai.indra.android.MainActivity;

/** Base class for the five main tabs. */
public abstract class Screen {

    protected final MainActivity host;

    protected Screen(MainActivity host) {
        this.host = host;
    }

    /** Builds (or rebuilds) the tab content. */
    public abstract View build();

    /** Called when the tab becomes visible. */
    public void onShown() {
        // optional
    }

    /** Short label for the bottom navigation. */
    public abstract String label();
}
