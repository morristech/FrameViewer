package org.samcrow.frameviewer.ui;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.samcrow.frameviewer.io3.Marker;
import org.samcrow.frameviewer.PaintableCanvas;
import org.samcrow.frameviewer.io3.AntActivity;
import org.samcrow.frameviewer.io3.AntLocation;
import org.samcrow.frameviewer.io3.InteractionMarker;

/**
 * Displays a video frame and allows it to be clicked on
 * <p>
 * @author Sam Crow
 */
public class FrameCanvas extends PaintableCanvas {

    /**
     * The image to be displayed
     */
    private final ObjectProperty<Image> image = new SimpleObjectProperty<>();

    /**
     * The markers to display on this frame. This must not be null. For no
     * markers
     * to be displayed, this should be set to an empty list.
     */
    private List<Marker> markers = new LinkedList<>();

    /**
     * Local coordinate X position of the frame's top left corner
     */
    private double imageTopLeftX;

    /**
     * Local coordinate Y position of the frame's top left corner
     */
    private double imageTopLeftY;

    /**
     * Local coordinate displayed width of the frame
     */
    private double imageWidth;

    /**
     * Local coordinate displayed width of the frame
     */
    private double imageHeight;

    private MouseEvent lastMouseMove;

    public FrameCanvas() {

        setFocusTraversable(true);
        requestFocus();

        //Add a marker when clicked on
        setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                try {
                    Point2D markerPoint = getFrameLocation(event);

                    // Part 1: See if an existing marker should be edited
                    for (ListIterator<Marker> iterator = getMarkers().listIterator(); iterator.hasNext();) {
                        Marker marker = iterator.next();
                        if (markerClicked(marker, markerPoint)) {
                            // Edit this marker
                            MarkerEditDialog dialog = new MarkerEditDialog(getScene().getWindow(), marker);
                            //Move the dialog to the position of the cursor
                            dialog.setX(event.getScreenX());
                            dialog.setY(event.getScreenY());

                            dialog.showAndWait();

                            if (dialog.success()) {

                                if (dialog.deleted()) {
                                    // Delete this marker
                                    iterator.remove();

                                }
                                else {
                                    // Put the newly created (changed) marker in the list,
                                    // and replace the original
                                    Marker newMarker = dialog.createMarker();
                                    // Move the new marker to the same place as the old one
                                    newMarker.setX(marker.getX());
                                    newMarker.setY(marker.getY());
                                    // Modify the list to replace the current marker in this position
                                    // with the new one.
                                    iterator.set(newMarker);
                                }

                                event.consume();
                                repaint();
                            }

                            return;
                        }
                    }
                    // Existing marker not found
                    // Create a new marker

                    //Ask the user for a marker type
                    MarkerDialog dialog = new MarkerDialog(getScene().getWindow());

                    // If the user right-clicked, set up for an interaction
                    dialog.setIsInteraction(event.getButton() == MouseButton.SECONDARY);

                    //Move the dialog to the position of the cursor
                    dialog.setX(event.getScreenX());
                    dialog.setY(event.getScreenY());
                    dialog.showAndWait();

                    if (!dialog.success()) {
                        // Do nothing
                        return;
                    }

                    Marker marker = dialog.createMarker();
                    marker.setPosition(markerPoint);

                    getMarkers().add(marker);
                    repaint();

                    event.consume();
                }
                catch (NotInFrameException | IllegalArgumentException ex) {
                    //Ignore this click
                }

            }
        });

        //Update cursor position when the mouse moves
        setOnMouseMoved(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                lastMouseMove = event;
                requestFocus();
            }
        });

        setOnKeyTyped(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent event) {

                // Get the in-frame position based on the last mouse move
                if (lastMouseMove == null) {
                    return;
                }
                try {
                    getMarkers().add(createMarkerFromKey(lastMouseMove, event));
                    repaint();
                }
                catch (NotInFrameException | IllegalArgumentException ex) {

                }

            }
        });

        //Repaint when the frame or the markers changes
        image.addListener(new InvalidationListener() {
            @Override
            public void invalidated(Observable o) {
                requestFocus();
                repaint();
            }
        });
    }

    @Override
    protected void paint() {

        GraphicsContext gc = getGraphicsContext2D();
        gc.clearRect(0, 0, getWidth(), getHeight());

        //Draw image
        if (image.get() != null) {

            //Scale image to fit this canvas, but preserve its aspect ratio
            final double canvasWidth = getWidth();
            final double canvasHeight = getHeight();

            final double targetImageWidth = image.get().getWidth();
            final double targetImageHeight = image.get().getHeight();
            final double imageAspectRatio = targetImageWidth / targetImageHeight;

            final double widthRatio = targetImageWidth / canvasWidth;
            final double heightRatio = targetImageHeight / canvasHeight;

            if (heightRatio < widthRatio) {
                //Window is taller than image
                //If necessary, shrink image width to fit
                imageWidth = Math.min(targetImageWidth, canvasWidth);
                imageHeight = imageWidth / imageAspectRatio;
            }
            else {
                //Window is wider than image
                //If necessary, shrink image height to fit
                imageHeight = Math.min(targetImageHeight, canvasHeight);
                imageWidth = imageHeight * imageAspectRatio;
            }

            final double centerX = canvasWidth / 2;
            final double centerY = canvasHeight / 2;
            imageTopLeftX = centerX - imageWidth / 2;
            imageTopLeftY = centerY - imageHeight / 2;

            gc.drawImage(image.get(), imageTopLeftX, imageTopLeftY, imageWidth, imageHeight);

            gc.save();
            //Draw markers
            for (Marker marker : getMarkers()) {
                gc.setStroke(marker.getColor());

                final double imageXRatio = marker.getX() / image.get().getWidth();
                final double imageYRatio = marker.getY() / image.get().getHeight();

                final double canvasX = imageTopLeftX + imageWidth * imageXRatio;
                final double canvasY = imageTopLeftY + imageHeight * imageYRatio;

                marker.paint(gc, canvasX, canvasY);
            }

            gc.restore();
        }

    }

    private Marker createMarkerFromKey(MouseEvent location, KeyEvent keyEvent) throws NotInFrameException {
        Point2D screenPos = getFrameLocation(location);
        if (keyEvent.getCharacter().equals("i")) {
            // Interaction: entrance chamber, both ants walking and 2 way interaction
            InteractionMarker marker = new InteractionMarker(screenPos, AntActivity.Walking, AntLocation.EntranceChamber, AntActivity.Walking, AntLocation.EntranceChamber);
            marker.setType(InteractionMarker.InteractionType.TwoWay);
            marker.setAntId(MarkerDialog.getLastAntId());

            return marker;
        }
        else if (keyEvent.getCharacter().equals("x")) {
            // Not an interaction: Focal ant, walking, exit
            Marker marker = new Marker(screenPos, AntActivity.Walking, AntLocation.AtExit);
            marker.setAntId(MarkerDialog.getLastAntId());
            return marker;
        }
        else if (keyEvent.getCharacter().equals("e")) {
            // Not an interaction: Focal ant, walking, entrance chamber
            Marker marker = new Marker(screenPos, AntActivity.Walking, AntLocation.EntranceChamber);
            marker.setAntId(MarkerDialog.getLastAntId());
            return marker;
        }
        else if (keyEvent.getCharacter().equals("t")) {
            // Not an interaction: Focal ant, walking, tunnel
            Marker marker = new Marker(screenPos, AntActivity.Walking, AntLocation.AtTunnel);
            marker.setAntId(MarkerDialog.getLastAntId());
            return marker;
        }
        else if(keyEvent.getCharacter().equals("g")) {
            // Not an interaction: Focal ant walking, edge
            Marker marker = new Marker(screenPos, AntActivity.Walking, AntLocation.Edge);
            marker.setAntId(MarkerDialog.getLastAntId());
            return marker;
        }
        else if(keyEvent.getCharacter().equals("o")) {
            // Not an interaction: Focal ant walking, outside
            Marker marker = new Marker(screenPos, AntActivity.Walking, AntLocation.Outside);
            marker.setAntId(MarkerDialog.getLastAntId());
            return marker;
        }
        else {
            throw new IllegalArgumentException("No marker default corresponding to this key");
        }

    }

    /**
     * Returns the location, in frame image coordinates, of a mouse event
     * <p>
     * @param event
     * @return
     * @throws org.samcrow.frameviewer.ui.FrameCanvas.NotInFrameException If the
     * mouse
     * event was not on the displayed frame
     */
    private Point2D getFrameLocation(MouseEvent event) throws NotInFrameException {
        return getFrameLocation(event.getX(), event.getY());
    }

    /**
     * Returns the location, in frame image coordinates, of a location in local
     * canvas coordinates
     * <p>
     * @param x
     * @param y
     * @return
     * @throws org.samcrow.frameviewer.ui.FrameCanvas.NotInFrameException
     */
    private Point2D getFrameLocation(double x, double y) throws NotInFrameException {
        if ((x < imageTopLeftX || x > (imageTopLeftX + imageWidth))
                || (y < imageTopLeftY || y > (imageTopLeftY + imageHeight))) {
            throw new NotInFrameException("The provided click was outside the bounds of the frame");
        }

        final double xRatio = (x - imageTopLeftX) / imageWidth;
        final double yRatio = (y - imageTopLeftY) / imageHeight;

        assert xRatio <= 1;
        assert yRatio <= 1;

        return new Point2D(image.get().getWidth() * xRatio, image.get().getHeight() * yRatio);
    }

    private boolean markerClicked(Marker marker, Point2D frameLocation) {
        final int radius = 6;
        return radius >= frameLocation.distance(marker.getX(), marker.getY());

    }

    /**
     * Thrown when a mouse event is not inside the frame
     */
    private static class NotInFrameException extends Exception {

        public NotInFrameException(String message) {
            super(message);
        }

    }

    public final ObjectProperty<Image> imageProperty() {
        return image;
    }

    /**
     * Sets the markers.
     * The given list will be copied, so changes to it will not affect
     * the displayed markers.
     * <p>
     * @param newMarkers
     */
    public final void setMarkers(List<Marker> newMarkers) {
        if (newMarkers == null) {
            throw new IllegalArgumentException("The marker list must not be null");
        }

        markers = newMarkers;
    }

    public final List<Marker> getMarkers() {
        return markers;
    }

}
