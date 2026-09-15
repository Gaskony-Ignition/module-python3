package com.gaskony.python3.designer;

/**
 * Represents Python process pool statistics from the REST API.
 * <p>
 * Converted to Java 17 record in v2.10.0 (eliminated 27 lines of boilerplate).
 *
 * <p>Example JSON response from /api/v1/pool-stats:</p>
 * <pre>{
 *   "totalSize": 3,
 *   "healthy": 3,
 *   "available": 2,
 *   "inUse": 1
 * }</pre>
 *
 * @param totalSize Total number of processes in the pool
 * @param healthy Number of healthy processes
 * @param available Number of available (not borrowed) processes
 * @param inUse Number of processes currently in use
 *
 * @since v1.0.0 (as class)
 * @since v2.10.0 (as record)
 */
public record PoolStats(
    int totalSize,
    int healthy,
    int available,
    int inUse
) {
    /**
     * Compact constructor with validation.
     */
    public PoolStats {
        // All fields are primitives, no null checks needed
    }

    // Legacy getter methods for backward compatibility with existing code
    public int getTotalSize() { return totalSize; }
    public int getHealthy() { return healthy; }
    public int getAvailable() { return available; }
    public int getInUse() { return inUse; }

    /**
     * Check if all processes in the pool are healthy.
     * @return true if healthy count equals total size
     */
    public boolean isHealthy() {
        return healthy == totalSize;
    }

    @Override
    public String toString() {
        return String.format("PoolStats{total=%d, healthy=%d, available=%d, inUse=%d}",
                totalSize, healthy, available, inUse);
    }
}
