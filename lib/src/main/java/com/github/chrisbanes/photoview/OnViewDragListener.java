package com.github.chrisbanes.photoview;

/**
 * Interface definition for a callback to be invoked when the photo is experiencing a drag event
 */
public interface OnViewDragListener {

    /**
     * Callback for when the photo is experiencing a drag event. This cannot be invoked when the
     * user is scaling.
     *
     * @param startX X coordinates of finger down
     * @param startY Y coordinates of finger down
     * @param endX   X coordinates of finger up
     * @param endY   Y coordinates of finger up
     */
    void onDrags(float startX, float startY, float endX, float endY);
}
