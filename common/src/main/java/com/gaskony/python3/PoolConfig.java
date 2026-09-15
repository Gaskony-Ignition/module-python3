package com.gaskony.python3;

/**
 * Centralised configuration constants for the Python 3 process pool and UI sizing.
 *
 * <p>Single source of truth for pool size limits, timeouts, and font size bounds.
 * Referenced by both gateway (pool management) and designer (settings UI) scopes.</p>
 */
public final class PoolConfig {

    private PoolConfig() {
        // Utility class - no instances
    }

    // =========================================================================
    // Process pool sizing
    // =========================================================================

    /** Default number of Python worker processes to maintain in the pool. */
    public static final int DEFAULT_POOL_SIZE = 3;

    /** Minimum pool size accepted via configuration or the REST API. */
    public static final int MIN_POOL_SIZE = 1;

    /** Maximum pool size accepted via configuration or the REST API. */
    public static final int MAX_POOL_SIZE = 20;

    // =========================================================================
    // Timeouts (all in seconds)
    // =========================================================================

    /** Seconds to wait when borrowing an executor from a fully-loaded pool. */
    public static final int BORROW_TIMEOUT_SECONDS = 30;

    /** Initial delay before the first pool health-check run (seconds). */
    public static final int HEALTH_CHECK_INITIAL_DELAY_SECONDS = 30;

    /** Interval between successive pool health-check runs (seconds). */
    public static final int HEALTH_CHECK_INTERVAL_SECONDS = 30;

    // =========================================================================
    // Code-editor font sizing (used by Settings UI and editor initialisation)
    // =========================================================================

    /** Default font size for the code editor. */
    public static final int DEFAULT_FONT_SIZE = 12;

    /** Minimum font size accepted in the Settings spinner. */
    public static final int MIN_FONT_SIZE = 8;

    /** Maximum font size accepted in the Settings spinner. */
    public static final int MAX_FONT_SIZE = 24;
}
