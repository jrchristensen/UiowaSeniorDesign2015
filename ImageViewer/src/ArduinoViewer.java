import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Rectangle;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

/**
 * This was created using the example from the Apress book JavaFX8 Introduction
 * by example and adding functionality. This application provides the user the
 * ability to view photos that are loaded from the Arduino and also any other
 * image file that is desired
 * <p>
 * Instructions:
 * <p>
 * 1. Drag and drop an image file onto the application window. 2. Repeat step 1
 * so more than 2 images are loaded. 3. Click the left and right arrow controls
 * to advance.
 * </p>
 * 
 * @author Jacob Christensen
 */
public class ArduinoViewer extends Application {
	/**
	 * private final list<Sting> imageFiles - The String representation of where
	 * the Images are stored in the local computer
	 */
	private final List<String> imageFiles = new ArrayList<String>();

	/**
	 * private int currentIndex - The current position in the ArrayList
	 * imageFiles (The image that is currently being viewed)
	 */
	private int currentIndex = -1;

	/**
	 * private enum ButtonMove - enumeration of the next and previous button
	 * directions.
	 */
	private enum ButtonMove {
		NEXT, PREV
	};

	/**
	 * private ImageView currentImageView - The current image that is being
	 * displayed
	 */
	private ImageView currentImageView;
	/**
	 * private ProgressIdicator - the progress indicator that indicates that the
	 * files are loading
	 */
	private ProgressIndicator progressIndicator;
	/**
	 * private AtomicBoolean loading - mutual exclusion used when handing out
	 * tasks of loading
	 */
	private AtomicBoolean loading = new AtomicBoolean();

	/**
	 * private String currentDirectory - The directory where the Arduino will
	 * save its files - The defaut will be "" meaning the project directory
	 */
	private String currentDirectory = "";

	/**
	 * public method start -
	 */
	@Override
	public void start(Stage primaryStage) {
		currentDirectory = System.getProperty("user.dir").concat("\\");
		primaryStage.setTitle("ArduinoViewer");
		Group root = new Group();
		Scene scene = new Scene(root, 551, 400, Color.BLACK);
		scene.getStylesheets().add(
				getClass().getResource("photo-viewer.css").toExternalForm());
		primaryStage.setScene(scene);

		// set up the current image view area
		currentImageView = createImageView(scene.widthProperty());

		// set up drag and drop file abilities
		setupDragNDrop(scene);

		// create button panel controls (left & right arrows)
		Group buttonGroup = createButtonPanel(scene);

		// create a progress indicator
		progressIndicator = createProgressIndicator(scene);

		// create the menus that will allow the user to connect and download the
		// images stored on the Arduino
		MenuBar menuBar = new MenuBar();

		Menu file = new Menu("File");
		MenuItem ard = new MenuItem("Get Arduino Images");
		ard.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent event) {
				ArdSerial Arduino = new ArdSerial();
				Arduino.setSaveLocation(currentDirectory);

				if (Arduino.connect()) {
					Arduino.getFileNames();
					Arduino.printFileNames();
					Arduino.downloadFiles();
					Arduino.disconnect();

					ArrayList<String> names = Arduino.getImageFileNames();
					if (!names.isEmpty()) {
						for (String n : names) {
							addImage("file:/"
									+ currentDirectory.concat(n).replace("\\",
											"/"));
							System.out.println("file:/"
									+ currentDirectory.concat(n).replace("\\",
											"/"));
							System.out.println("Add" + n);
							if (currentIndex > -1) {
								loadImage(imageFiles.get(currentIndex));
							}
						}
					} else
						System.out.println("The Array is Empty");

				} else
					System.out.println("DownLoad Failed");

			}

		});
		MenuItem dir = new MenuItem("Set Working Directory");
		dir.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent arg0) {
				final DirectoryChooser directoryChooser = new DirectoryChooser();
				directoryChooser.setTitle("Choose a working Directory");
				directoryChooser.setInitialDirectory(new File(System
						.getProperty("user.home")));
				final File selectedDirectory = directoryChooser
						.showDialog(primaryStage);

				if (selectedDirectory != null) {
					currentDirectory = selectedDirectory.getAbsolutePath()
							.concat("\\");
					System.out.println(currentDirectory);
				}

			}

		});
		MenuItem exit = new MenuItem("Exit");
		exit.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent t) {
				System.exit(0);

			}

		});
		file.getItems().addAll(ard, dir, exit);
		Menu edit = new Menu("Edit");
		Menu view = new Menu("View");

		menuBar.getMenus().addAll(file, edit, view);

		root.getChildren().addAll(currentImageView, buttonGroup,
				progressIndicator, menuBar);

		primaryStage.show();
	}

	/**
	 * Private method createImageView - A factory function returning an
	 * ImageView instance to preserve the aspect ratio and bind the instance to
	 * the width of the scene for resizing the image.
	 * 
	 * @param widthProperty
	 *            is the Scene's read only width property.
	 * 
	 * @return ImageView newly created image view for current display.
	 */
	private ImageView createImageView(ReadOnlyDoubleProperty widthProperty) {
		// maintain aspect ratio
		ImageView imageView = new ImageView();
		// set aspect ratio
		imageView.setPreserveRatio(true);
		// resize based on the scene
		imageView.fitWidthProperty().bind(widthProperty);
		return imageView;
	}

	/**
	 * private method setupDragNDrop - Sets up the drag and drop capability for
	 * files and URLs to be dragged and dropped onto the scene. This will load
	 * the image into the current image view area.
	 * 
	 * @param scene
	 *            The primary application scene.
	 */
	private void setupDragNDrop(Scene scene) {

		// Dragging over surface
		scene.setOnDragOver((DragEvent event) -> {
			Dragboard db = event.getDragboard();
			if (db.hasFiles() || (db.hasUrl() && isValidImageFile(db.getUrl()))) {
				// System.out.println("url: " + db.getUrl());
				event.acceptTransferModes(TransferMode.LINK);
			} else {
				event.consume();
			}
		});

		// Dropping over surface
		scene.setOnDragDropped((DragEvent event) -> {
			Dragboard db = event.getDragboard();
			// image from the local file system.
			if (db.hasFiles() && !db.hasUrl()) {
				db.getFiles()
						.stream()
						.forEach(
								file -> {
									try {
										System.out.println("dropped file: "
												+ file.toURI().toURL()
														.toString());
										addImage(file.toURI().toURL()
												.toString());
									} catch (MalformedURLException ex) {
										ex.printStackTrace();
									}
								});
			} else {
				System.out.println("dropped url: " + db.getUrl());
				// image from some host
				addImage(db.getUrl());
			}
			if (currentIndex > -1) {
				loadImage(imageFiles.get(currentIndex));
			}

			event.setDropCompleted(true);
			event.consume();
		});
	}

	/**
	 * private method createButtonPannel - Returns a custom created button panel
	 * containing left and right buttons to see previous and next images.
	 * 
	 * @param scene
	 *            The main application scene
	 * 
	 * @return Group A custom button panel with previous and next buttons
	 */
	private Group createButtonPanel(Scene scene) {
		// create button panel
		Group buttonGroup = new Group();

		Rectangle buttonArea = new Rectangle(0, 0, 60, 30);
		buttonArea.getStyleClass().add("button-panel");
		buttonGroup.getChildren().add(buttonArea);

		// left arrow button
		Arc leftButton = new Arc(12, 16, 15, 15, -30, 60);
		leftButton.setType(ArcType.ROUND);
		leftButton.getStyleClass().add("left-arrow");

		// return to previous image
		leftButton.addEventHandler(MouseEvent.MOUSE_PRESSED, (mouseEvent) -> {
			System.out.println("busy loading? " + loading.get());
			// if no previous image or currently loading.
				if (currentIndex == 0 || loading.get())
					return;
				int indx = gotoImageIndex(ButtonMove.PREV);
				if (indx > -1) {
					loadImage(imageFiles.get(indx));
				}
			});

		// right arrow button
		Arc rightButton = new Arc(12, 16, 15, 15, 180 - 30, 60);
		rightButton.setType(ArcType.ROUND);
		rightButton.getStyleClass().add("right-arrow");

		// advance to next image
		rightButton.addEventHandler(MouseEvent.MOUSE_PRESSED, (mouseEvent) -> {
			System.out.println("busy loading? " + loading.get());
			// if no next image or currently loading.
				if (currentIndex == imageFiles.size() - 1 || loading.get())
					return;

				int indx = gotoImageIndex(ButtonMove.NEXT);
				if (indx > -1) {
					loadImage(imageFiles.get(indx));
				}
			});
		// add buttons to button group
		buttonGroup.getChildren().addAll(leftButton, rightButton);

		// move button group when scene is resized
		buttonGroup.translateXProperty().bind(
				scene.widthProperty().subtract(buttonArea.getWidth() + 6));
		buttonGroup.translateYProperty().bind(
				scene.heightProperty().subtract(buttonArea.getHeight() + 6));
		return buttonGroup;
	}

	/**
	 * private method ProgressIndicator - Create a progress indicator control to
	 * be centered.
	 * 
	 * @param scene
	 *            The primary application scene.
	 * 
	 * @return ProgressIndicator a new progress indicator centered.
	 */
	private ProgressIndicator createProgressIndicator(Scene scene) {
		ProgressIndicator progress = new ProgressIndicator(0);
		progress.setVisible(false);
		progress.layoutXProperty().bind(
				scene.widthProperty().subtract(progress.widthProperty())
						.divide(2));
		progress.layoutYProperty().bind(
				scene.heightProperty().subtract(progress.heightProperty())
						.divide(2));
		return progress;
	}

	/**
	 * private method isValidImageFile - Returns true if URL's file extensions
	 * match jpg, jpeg, png, gif and bmp.
	 * 
	 * @param url
	 *            standard URL path to image file.
	 * 
	 * @return boolean returns true if URL's extension matches jpg,jpeg, png and
	 *         gif.
	 */
	private boolean isValidImageFile(String url) {
		List<String> imgTypes = Arrays.asList(".jpg", ".jpeg", ".png", ".gif",
				".bmp");
		return imgTypes.stream().anyMatch(t -> url.endsWith(t));
	}

	/**
	 * private method addImage - Adds the URL string representation of the path
	 * to the image file. Based on a URL the method will check if it matches
	 * supported image format.
	 * 
	 * @param url
	 *            string representation of the path to the image file.
	 */
	private void addImage(String url) {
		if (isValidImageFile(url)) {
			currentIndex += 1;
			imageFiles.add(currentIndex, url);
		} else
			System.out.println("Not a valid image file");
	}

	/**
	 * private method gotoImageIndex - returns the next index in the list of
	 * files to go to the next image
	 * 
	 * @param direction
	 *            PREV and NEXT to move backward or forward in the list of
	 *            pictures.
	 * 
	 * @return int the index to the previous or next picture to be shown.
	 */
	private int gotoImageIndex(ButtonMove direction) {
		int size = imageFiles.size();
		if (size == 0) {
			currentIndex = -1;
		} else if (direction == ButtonMove.NEXT && size > 1
				&& currentIndex < size - 1) {
			currentIndex += 1;
		} else if (direction == ButtonMove.PREV && size > 1 && currentIndex > 0) {
			currentIndex -= 1;
		}

		return currentIndex;

	}

	/*
	 * Returns a worker task (Task) which will off-load the image on a separate
	 * thread when finished; the current image will be displayed on the JavaFX
	 * application thread.
	 * 
	 * @param url string representation of the path to the image file.
	 * 
	 * @return
	 */
	private Task createWorker(final String url) {
		return new Task() {
			@Override
			protected Object call() throws Exception {
				// on the worker thread...
				Image image = new Image(url, false);
				Platform.runLater(() -> {
					// on the JavaFX Application Thread....
					System.out.println("done loading image " + url);
					currentImageView.setImage(image);
					progressIndicator.setVisible(false);
					loading.set(false); // free lock
				});
				return true;
			}
		};
	}

	/**
	 * private method loadImage - This method does the following loads an image,
	 * updates a progress bar and spawns a new thread. If another process is
	 * already loading the method will return without loading.
	 * 
	 * @param url
	 *            string representation of the path to the image file.
	 */
	private void loadImage(String url) {
		// do not begin task until current
		// task is finished loading (atomic)
		if (!loading.getAndSet(true)) {
			System.out.println("loadImage spawned ");
			Task loadImage = createWorker(url);
			progressIndicator.setVisible(true);
			progressIndicator.progressProperty().unbind();
			progressIndicator.progressProperty().bind(
					loadImage.progressProperty());
			new Thread(loadImage).start();
		}
	}

	/**
	 * public method main - Entry point of ArduinoViewer
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		launch(args);
	}
}