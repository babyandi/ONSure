package io.onsure.sdk.v1;

import java.util.List;

/** Cursor page returned by a Local API workflow that follows the items/next_cursor convention. */
public record Page<T>(List<T> items, String nextCursor, int requestedLimit) {
    public Page {
        items = List.copyOf(items == null ? List.of() : items);
        if (requestedLimit < 1 || requestedLimit > 1000) throw new IllegalArgumentException("requestedLimit");
        if (nextCursor != null && (nextCursor.isBlank() || nextCursor.length() > 1024)) {
            throw new IllegalArgumentException("nextCursor");
        }
    }

    public boolean hasNext() { return nextCursor != null; }
}
