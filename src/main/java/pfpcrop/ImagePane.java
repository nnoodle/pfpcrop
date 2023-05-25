package pfpcrop;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.paint.Color;
import javafx.scene.Node;
import javafx.scene.shape.Shape;
import javafx.scene.shape.Line;
import javafx.scene.shape.StrokeType;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Circle;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.input.ScrollEvent;


public class ImagePane extends Region {
    private static final double PHI = (1+Math.sqrt(5))/2;
    private static final int MIN_PIXELS = 50;

    public ImageView imageView;
    private String imageUrl;

    public ImagePane(String imageUrl, double initialSize) {
        super();
        this.imageUrl = imageUrl;
        this.setWidth(initialSize);
        this.setHeight(initialSize);
        initialize();
    }

    private void initialize() {
        imageView = new ImageView(imageUrl);
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(square());

        double maximgsize = Math.min(imageView.getImage().getWidth(), imageView.getImage().getHeight());
        imageView.setViewport(new Rectangle2D(0, 0, maximgsize, maximgsize));

        ObjectProperty<Point2D> mouseDown = new SimpleObjectProperty<>();
        imageView.setOnMousePressed(e -> {
                Point2D mousePress = imageViewToImage(imageView, new Point2D(e.getX(), e.getY()));
                mouseDown.set(mousePress);
            });
        imageView.setOnMouseDragged(e -> {
                Point2D dragPoint = imageViewToImage(imageView, new Point2D(e.getX(), e.getY()));
                shift(imageView, dragPoint.subtract(mouseDown.get()));
                mouseDown.set(imageViewToImage(imageView, new Point2D(e.getX(), e.getY())));
            });

        imageView.setOnScroll(e -> {
                if (e.getDeltaY() == -0.0) // getDeltaY can throw a negative 0
                    return;
                zoomTo(e.getDeltaY() < 0 ? 5 : -5, e.getX(), e.getY());
            });

        imageView.setOnMouseClicked(e -> {
                double size = getImageSquare();
                if (e.getClickCount() == 2)
                    resetView();
            });

        this.getChildren().addAll(imageView, getGuideLines(square(), square()), hollow(square()));
    }

    private double getImageWidth() { return imageView.getImage().getWidth(); }

    private double getImageHeight() { return imageView.getImage().getHeight(); }

    private double getImageSquare() { return Math.min(getImageWidth(), getImageHeight()); }

    public void setImage(Image image) {
        imageView.setImage(image);
        resetView();
        System.gc(); // images can get pretty big.
    }

    private Shape hollow(double size) {
        Shape o =Shape.subtract(new Rectangle(size, size), new Circle(size/2, size/2, size/2));
        o.setFill(Color.gray(0.0, 0.5));
        o.setStroke(Color.web("#f4f4f4", 1.0));
        o.setStrokeType(StrokeType.OUTSIDE);
        return o;
    }

    private double square() {
        return Math.min(this.getWidth(), this.getHeight());
    }

    private double lastResize = 0.0;
    @Override
    public void resize(double width, double height) {
        super.resize(width, height);
        double size = Math.min(width, height);
        setWidth(size);
        setHeight(size);

        if (lastResize == square())
            return;

        lastResize = square();
        imageView.setFitWidth(square());

        this.getChildren().set(1, getGuideLines(square(), square()));
        this.getChildren().set(2, hollow(square()));
    }

    // guide lines
    public enum GuideLineType {
        NONE,
        CENTER,
        THIRDS,
        FIFTHS,
        PHI,
        DIAGONAL
    }
    private GuideLineType guideLineType = GuideLineType.NONE;
    public void setGuideLineType(GuideLineType t) {
        guideLineType = t;
        this.getChildren().set(1, getGuideLines(square(), square()));
    }

    private Shape getGuideLines(double width, double height) {
        Shape s;

        switch (guideLineType) {
        case CENTER: {
            s = union(new Line(0, height/2, width, height/2),
                      new Line(width/2, 0, width/2, height));
            break;
        }
        case THIRDS: {
            s = union(new Line(width/3, 0, width/3, height),
                      new Line(width/3*2, 0, width/3*2, height),
                      new Line(0, height/3, width, height/3),
                      new Line(0, height/3*2, width, height/3*2));
            break;
        }
        case FIFTHS: {
            s = union(new Line(width/5, 0, width/5, height),
                      new Line(width/5*2, 0, width/5*2, height),
                      new Line(width/5*3, 0, width/5*3, height),
                      new Line(width/5*4, 0, width/5*4, height),
                      new Line(0, height/5, width, height/5),
                      new Line(0, height/5*2, width, height/5*2),
                      new Line(0, height/5*3, width, height/5*3),
                      new Line(0, height/5*4, width, height/5*4));
            break;
        }
        case PHI: {
            s = union(new Line(width/PHI, 0, width/PHI, height),
                      new Line(width-width/PHI, 0, width-width/PHI, height),
                      new Line(0, height/PHI, width, height/PHI),
                      new Line(0, height-height/PHI, width, height-height/PHI)
                      );
            break;
        }
        case DIAGONAL: {
            s = union(new Line(0, 0, width, height),
                      new Line(0, height, width, 0));
            break;
        }
        case NONE:
        default: {
            s = new Line(0,0,0,0);
            s.setFill(Color.TRANSPARENT);
            s.setStroke(Color.TRANSPARENT);
            return s;
        }
        }

        s.setFill(Color.gray(1, 0.5));
        s.setStroke(Color.gray(1, 0.1));
        s.setStrokeWidth(1);

        return s;
    }

    private static Shape union(Shape... shapes) {
        if (shapes.length < 2)
            return shapes[0];

        Shape u = shapes[0];
        for (Shape s : java.util.Arrays.copyOfRange(shapes, 1, shapes.length))
            u = Shape.union(u, s);
        return u;
    }

    /* taken and modified from:
     * James D. (2020, May 1). Zoomable and Pannable JavaFX ImageView Example. https://gist.github.com/james-d/ce5ec1fd44ce6c64e81a
     */
    public int zoomTo(double delta, double atX, double atY) {
        double width = getImageWidth();
        double height = getImageHeight();

        // you can't set delta with a ?: because getDeltaY can throw a negative 0
        Rectangle2D viewport = imageView.getViewport();

        double min = Math.min(MIN_PIXELS / viewport.getWidth(), MIN_PIXELS / viewport.getHeight());
        double max = Math.min(width / viewport.getWidth(), height / viewport.getHeight());
        double scale = clamp(Math.pow(1.01, delta), min, max);

        Point2D mouse = imageViewToImage(imageView, new Point2D(atX, atY));

        double newWidth = viewport.getWidth() * scale;
        double newHeight = viewport.getHeight() * scale;

        /* To keep the visual point under the mouse from moving, we need
         * (x - newViewportMinX) / (x - currentViewportMinX) = scale
         * where x is the mouse X coordinate in the image
         *
         * solving this for newViewportMinX gives
         *
         * newViewportMinX = x - (x - currentViewportMinX) * scale
         *
         * we then clamp this value so the image never scrolls out
         * of the imageview:
         */

        double newMinX = clamp(mouse.getX() - (mouse.getX() - viewport.getMinX()) * scale,
                               0, width - newWidth);
        double newMinY = clamp(mouse.getY() - (mouse.getY() - viewport.getMinY()) * scale,
                               0, height - newHeight);

        imageView.setViewport(new Rectangle2D(newMinX, newMinY, newWidth, newHeight));
        if (scale == min)
            return -1;
        else if (scale == max)
            return 1;
        else
            return 0;
    }

    // reset to the top left:
    public void resetView() {
        double size = getImageSquare();
        imageView.setViewport(new Rectangle2D(0, 0, size, size));
    }

    // shift the viewport of the imageView by the specified delta, clamping so
    // the viewport does not move off the actual image:
    private static void shift(ImageView imageView, Point2D delta) {
        Rectangle2D viewport = imageView.getViewport();

        double width = imageView.getImage().getWidth() ;
        double height = imageView.getImage().getHeight() ;

        double maxX = width - viewport.getWidth();
        double maxY = height - viewport.getHeight();

        double minX = clamp(viewport.getMinX() - delta.getX(), 0, maxX);
        double minY = clamp(viewport.getMinY() - delta.getY(), 0, maxY);

        imageView.setViewport(new Rectangle2D(minX, minY, viewport.getWidth(), viewport.getHeight()));
    }

    private static double clamp(double value, double min, double max) {
        if (value < min)
            return min;
        if (value > max)
            return max;
        return value;
    }

    // convert mouse coordinates in the imageView to coordinates in the actual image:
    private static Point2D imageViewToImage(ImageView imageView, Point2D imageViewCoordinates) {
        double xProportion = imageViewCoordinates.getX() / imageView.getBoundsInLocal().getWidth();
        double yProportion = imageViewCoordinates.getY() / imageView.getBoundsInLocal().getHeight();

        Rectangle2D viewport = imageView.getViewport();
        return new Point2D(viewport.getMinX() + xProportion * viewport.getWidth(),
                           viewport.getMinY() + yProportion * viewport.getHeight());
    }
}
