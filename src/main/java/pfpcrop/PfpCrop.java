package pfpcrop;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javafx.util.StringConverter;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeType;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class PfpCrop extends Application {
    private static final ExtensionFilter IMAGE_ALL = new ExtensionFilter("All Images", "*.png", "*.jpg", "*.jpeg", "*.gif");
    private static final ExtensionFilter IMAGE_PNG = new ExtensionFilter("PNG Images", "*.png");
    private static final ExtensionFilter IMAGE_GIF = new ExtensionFilter("GIF Images", "*.gif");
    private static final ExtensionFilter IMAGE_JPEG = new ExtensionFilter("JPEG Images", "*.jpg", "*.jpeg");

    private ImagePane pane;
    private URL initialImage = getClass().getResource("/pfpcrop/naruto.jpg");

    private File initialDirectory = new File(System.getProperty("user.dir"));
    
    @Override
    public void start(Stage stage) {
        pane = new ImagePane(initialImage.toString(), 420);

        var pickerBtn = new Button("Open");
        pickerBtn.setOnAction(e -> {
                File f = openChooser(stage, "Open Image").showOpenDialog(stage);

                if (f == null)
                    return;
                initialDirectory = new File(f.getParent());
                try {
                    pane.setImage(new Image(new FileInputStream(f)));
                    initialImage = f.toURL();
                } catch (FileNotFoundException err) {
                    return;
                } catch (MalformedURLException err) {
                    throw new RuntimeException(err);
                }
            });

        var saveBtn = new Button("Save");
        saveBtn.setOnAction(e -> {
                FileChooser chooser = openChooser(stage, "Save Image As");
                File out = chooser.showSaveDialog(stage);

                if (out == null)
                    return;
                initialDirectory = new File(out.getParent());

                String imageFormat = "png";
                ExtensionFilter type = chooser.getSelectedExtensionFilter();
                if (out.toString().endsWith("png"))
                    ;
                else if (out.toString().endsWith("gif"))
                    imageFormat = "gif";
                else if (out.toString().matches(".*\\.jpe?g$"))
                    imageFormat = "jpeg";
                else if (type == IMAGE_GIF)
                    imageFormat = "gif";
                else if (type == IMAGE_JPEG)
                    imageFormat = "jpeg";

                writeImage(out, imageFormat);
            });

        var resetBtn = new Button("Reset");
        resetBtn.setOnAction(e -> pane.resetView());

        var zoomInBtn = new Button("Zoom+");
        zoomInBtn.setOnAction(e -> pane.zoomTo(-1.0, pane.getWidth()/2, pane.getHeight()/2));

        var zoomOutBtn = new Button("Zoom-");
        zoomOutBtn.setOnAction(e -> pane.zoomTo(1.0, pane.getWidth()/2, pane.getHeight()/2));

        var guides = new ChoiceBox<ImagePane.GuideLineType>();
        guides.getItems().addAll(ImagePane.GuideLineType.NONE,
                                 ImagePane.GuideLineType.CENTER,
                                 ImagePane.GuideLineType.THIRDS,
                                 ImagePane.GuideLineType.FIFTHS,
                                 ImagePane.GuideLineType.PHI,
                                 ImagePane.GuideLineType.DIAGONAL);
        guides.setConverter(new StringConverter<ImagePane.GuideLineType>() {
                @Override
                public String toString(ImagePane.GuideLineType g) {
                    if (g == null)
                        return "None";

                    switch (g) {
                    case CENTER: return "Center Lines";
                    case THIRDS: return "Rule of Thirds";
                    case FIFTHS: return "Rule of Fifths";
                    case PHI: return "Golden Sections";
                    case DIAGONAL: return "Diagonal";
                    case NONE:
                    default:
                        return "None";
                    }
                }
                @Override
                public ImagePane.GuideLineType fromString(String s) {
                    return ImagePane.GuideLineType.NONE;
                }
            });
        guides.setOnAction(e -> pane.setGuideLineType(guides.getValue()));

        var buttons = new HBox(3, pickerBtn, saveBtn, resetBtn, zoomInBtn, zoomOutBtn, guides);
        buttons.setAlignment(Pos.CENTER);


        var root = new VBox(5, pane, buttons);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(15));
        VBox.setVgrow(pane, Priority.ALWAYS);

        var scene = new Scene(root);

        stage.setTitle("(Profile) Picture Cropper");
        stage.setScene(scene);
        stage.show();
    }

    private FileChooser openChooser(Stage stage, String title) {
        var fc = new FileChooser();
        fc.setTitle(title);
        fc.setInitialDirectory(initialDirectory);

        fc.getExtensionFilters().addAll(IMAGE_ALL, IMAGE_PNG, IMAGE_GIF, IMAGE_JPEG);
        fc.setSelectedExtensionFilter(IMAGE_ALL);

        return fc;
    }

    private void writeImage(File out, String type) {
        Rectangle2D v = pane.imageView.getViewport();

        try {
            if (type.equals("gif")) {
                Process p = Runtime.getRuntime().exec("convert -version");
                if (p.waitFor() == 0) {
                    ProcessBuilder b = new ProcessBuilder();
                    // convert INPUT -coalesce -repage 0x0 -crop WxH+X+Y +repage -layers optimize OUTPUT
                    b.command("convert", pane.imageView.getImage().getUrl().substring(5), // remove 'file:'
                              "-coalesce", "-repage", "0x0", "-crop",
                              String.format("%dx%d+%d+%d",
                                            (int)v.getWidth(), (int)v.getHeight(),
                                            (int)v.getMinX(), (int)v.getMinY()),
                              "+repage", "-layers", "optimize",
                              out.toString());
                    b.start();
                    return;
                }
            }
        } // ignore that imagemagick is missing
        catch (InterruptedException err) {}
        catch (IOException err) {}

        try {
            BufferedImage bi = ImageIO.read(initialImage);
            BufferedImage img = bi.getSubimage((int)v.getMinX(), (int)v.getMinY(), (int)v.getWidth(), (int)v.getHeight());
            ImageIO.write(img, type, out);
        } catch (IOException err) {
            err.printStackTrace(System.err);
            new Alert(Alert.AlertType.ERROR, err.toString(), ButtonType.OK).showAndWait();
        }
    }

    @Override
    public void init() {
        if (!getParameters().getUnnamed().isEmpty()) {
            File f = new File(getParameters().getUnnamed().get(0));
            try {
                initialImage = f.toURL();
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
            if (!f.exists())
                throw new AssertionError(String.format("Image ‘%s’ does not exist.", f.toString()));
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
