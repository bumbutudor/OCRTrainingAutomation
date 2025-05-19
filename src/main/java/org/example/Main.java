package org.example;

import org.sikuli.script.*;
import javax.swing.*;
import java.awt.datatransfer.*;
import java.awt.image.BufferedImage;
import java.awt.Toolkit;
import java.awt.Color;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import javax.imageio.ImageIO;

public class Main {
    private static Screen screen = new Screen();

    private static final String IMAGE_FOLDER;
    private static final String FINEREADER_ICON;
    private static final String PATTERN_TRAINING_WINDOW;
    private static final String NEXT_BUTTON; // Image of the "Skip" or "Train" button
    private static final String INPUT; // Image of the input box

    static {
        try {
            String basePath = Paths.get(Main.class.getResource("/FineReaderAutomation/Screen2").toURI()).toString() + File.separator;
            IMAGE_FOLDER = basePath;
            FINEREADER_ICON = basePath + "FR15_icon.png";
            PATTERN_TRAINING_WINDOW = basePath + "pattern_training_window.png";
            NEXT_BUTTON = basePath + "skip.png";
            INPUT = basePath + "input.png";
        } catch (URISyntaxException e) {
            throw new RuntimeException("Unable to resolve resource path", e);
        }
    }

    // Counter for character file names
    private static int characterCounter = 1;

    public static void main(String[] args) {
        try {
            String outputFolder;

            // Prompt the user for the folder name
            String folderName = JOptionPane.showInputDialog(null,
                    "Enter the folder name where the program should work:",
                    "Folder Selection",
                    JOptionPane.PLAIN_MESSAGE);

            // Check if the user provided a folder name
            if (folderName == null || folderName.trim().isEmpty()) {
                JOptionPane.showMessageDialog(null,
                        "No folder name provided. The program will exit.",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Construct the output folder path
            outputFolder = IMAGE_FOLDER + folderName.trim() + File.separator;

            // Ensure the folder exists or create it
            File outputDir = new File(outputFolder);
            if (!outputDir.exists()) {
                boolean dirCreated = outputDir.mkdirs();
                if (dirCreated) {
                    System.out.println("Folder created at: " + outputFolder);
                } else {
                    JOptionPane.showMessageDialog(null,
                            "Failed to create the folder. The program will exit.",
                            "Error",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } else {
                System.out.println("Using existing folder at: " + outputFolder);
            }

            // Step 1: Open ABBYY FineReader
            Pattern fineReaderIconPattern = new Pattern(FINEREADER_ICON);
            screen.wait(fineReaderIconPattern, 10);
            screen.doubleClick(fineReaderIconPattern); // Open the program

            // Wait for FineReader to fully open
            Pattern patternTrainingWindowPattern = new Pattern(PATTERN_TRAINING_WINDOW).similar(0.5);
            screen.wait(patternTrainingWindowPattern, 15);
            Region trainingWindowRegion = screen.find(patternTrainingWindowPattern);

            // Check if the region was found
            if (trainingWindowRegion == null) {
                System.out.println("Training window not found!");
                return;
            }

            // Main loop for processing characters
            while (true) {
                Thread.sleep(1000); // Pause to stabilize the interface

                // Initialize variables for green box detection
                Region greenBoxRegion = null;
                long startTime = System.currentTimeMillis();
                long elapsedTime = 0;
                long maxWaitTime = 900000; // 10 seconds in milliseconds

                // Loop to wait for the green box for up to 5 seconds
                while (elapsedTime < maxWaitTime) {
                    // Capture the training window image
                    ScreenImage trainingWindowImage = screen.capture(trainingWindowRegion);
                    BufferedImage trainingBufferedImage = trainingWindowImage.getImage();

                    // Save the training window image for verification (optional)
                    File trainingWindowFile = new File(outputFolder + "last_training_window.png");
                    ImageIO.write(trainingBufferedImage, "png", trainingWindowFile);
                    // System.out.println("Training window screenshot saved at: " + trainingWindowFile.getAbsolutePath());

                    // Detect the green box in the image
                    greenBoxRegion = detectGreenBox(trainingWindowRegion, trainingBufferedImage);

                    if (greenBoxRegion != null) {
                        break; // Green box found, exit the waiting loop
                    }

                    // Wait before the next attempt
                    Thread.sleep(500); // Wait for 500 milliseconds
                    elapsedTime = System.currentTimeMillis() - startTime;
                }

                if (greenBoxRegion == null) {
                    System.out.println("Green box not found in the training window after 5 seconds!");
                    break; // Exit the main loop if the green box isn't found within 5 seconds
                }

                // Highlight the green box region for 1 second
                greenBoxRegion.highlight(1); // Highlight for 1 second

                // Capture the character image from the green box
                ScreenImage characterImage = screen.capture(greenBoxRegion);
                BufferedImage characterBufferedImage = characterImage.getImage();

                // Save the original character image
                String originalCharacterImagePath = outputFolder + "character_" + characterCounter + "_original.png";
                File originalCharacterImageFile = new File(originalCharacterImagePath);
                ImageIO.write(characterBufferedImage, "png", originalCharacterImageFile);
                System.out.println("Original character image saved at: " + originalCharacterImageFile.getAbsolutePath());

                // Process the image to remove non-gray pixels
                BufferedImage processedCharacterImage = processCharacterImage(characterBufferedImage);

                // Save the processed character image
                String processedCharacterImagePath = outputFolder + "character_" + characterCounter + "_processed.png";
                File processedCharacterImageFile = new File(processedCharacterImagePath);
                ImageIO.write(processedCharacterImage, "png", processedCharacterImageFile);
                System.out.println("Processed character image saved at: " + processedCharacterImageFile.getAbsolutePath());

                // Copy the processed image to clipboard
                copyImageToClipboard(processedCharacterImage);
                System.out.println("Character processed and copied to clipboard!");

                // Interact with the input box to copy the recognized character
                Pattern inputBoxPattern = new Pattern(INPUT);
                screen.wait(inputBoxPattern, 15);
                Region inputBoxRegion = screen.find(inputBoxPattern);

                // Click on the input box
                screen.click(inputBoxRegion);

                // Select all text and copy to clipboard
                screen.type("a", KeyModifier.CTRL); // Ctrl+A (Select All)
                screen.type("c", KeyModifier.CTRL); // Ctrl+C (Copy)

                // Wait a bit to ensure copying
                Thread.sleep(500);

                // Get text from clipboard
                String copiedText = getClipboardText();

                if (isValidCharacter(copiedText)) {
                    String character = copiedText.trim();

                    // Create a folder with the character's name if it doesn't exist
                    String characterFolderPath = outputFolder + character + File.separator;
                    File characterFolder = new File(characterFolderPath);
                    if (!characterFolder.exists()) {
                        characterFolder.mkdirs();
                        System.out.println("Folder created for character: " + character);
                    }

                    // Move the processed image to the character's folder
                    File sourceFile = processedCharacterImageFile;
                    File destFile = new File(characterFolderPath + sourceFile.getName());

                    boolean success = sourceFile.renameTo(destFile);
                    if (success) {
                        System.out.println("Processed character image moved to folder: " + destFile.getAbsolutePath());
                    } else {
                        System.out.println("Error moving processed character image.");
                    }
                } else {
                    System.out.println("Invalid or empty character. Processed image will not be moved.");
                }

                // Increment the counter for the next character
                characterCounter++;

                // Press the "Next" or "Train" button to proceed to the next character
                Pattern skipButtonPattern = new Pattern(NEXT_BUTTON);
                screen.wait(skipButtonPattern, 10);
                screen.click(skipButtonPattern);
            }

            System.out.println("Character processing completed.");

        } catch (FindFailed e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (UnsupportedFlavorException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // Method to detect the green box in the image
    public static Region detectGreenBox(Region trainingWindowRegion, BufferedImage trainingBufferedImage) {
        // Define the specific green color of the box
        Color targetColor = new Color(128, 220, 128, 255); // Adjust RGB values if necessary
        int colorTolerance = 30; // Color tolerance (adjust this value if needed)

        // Variables for coordinates
        int minX = trainingBufferedImage.getWidth();
        int minY = trainingBufferedImage.getHeight();
        int maxX = 0;
        int maxY = 0;

        // Iterate over the image pixels
        for (int y = 0; y < trainingBufferedImage.getHeight(); y++) {
            for (int x = 0; x < trainingBufferedImage.getWidth(); x++) {
                int pixelColor = trainingBufferedImage.getRGB(x, y);
                Color color = new Color(pixelColor, true); // Include alpha

                // Check if the pixel is green within the tolerance limits
                if (isColorMatch(color, targetColor, colorTolerance)) {
                    // Update extreme coordinates
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }

        // Check if we found the green box coordinates
        if (minX >= maxX || minY >= maxY) {
            return null; // Green box not found
        }

        // Determine the green box coordinates relative to the screen
        int greenBoxX = trainingWindowRegion.getX() + minX;
        int greenBoxY = trainingWindowRegion.getY() + minY;
        int greenBoxW = maxX - minX;
        int greenBoxH = maxY - minY;

        // Create the green box region
        Region greenBoxRegion = new Region(greenBoxX, greenBoxY, greenBoxW, greenBoxH);

        return greenBoxRegion;
    }

    // Method to check if two colors match within a tolerance
    public static boolean isColorMatch(Color c1, Color c2, int tolerance) {
        int diffRed = Math.abs(c1.getRed() - c2.getRed());
        int diffGreen = Math.abs(c1.getGreen() - c2.getGreen());
        int diffBlue = Math.abs(c1.getBlue() - c2.getBlue());

        return (diffRed <= tolerance) && (diffGreen <= tolerance) && (diffBlue <= tolerance);
    }

    // Method to process the character image, keeping all shades of gray
    public static BufferedImage processCharacterImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        BufferedImage processedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixelColor = image.getRGB(x, y);
                Color color = new Color(pixelColor, true);

                // Check if the pixel is in shades of gray (R, G, and B are equal)
                if (color.getRed() == color.getGreen() && color.getGreen() == color.getBlue()) {
                    // Keep the pixel in shades of gray
                    processedImage.setRGB(x, y, pixelColor);
                } else {
                    // Set other pixels to white
                    processedImage.setRGB(x, y, Color.WHITE.getRGB());
                }
            }
        }

        return processedImage;
    }

    // Method to copy an image to the clipboard
    public static void copyImageToClipboard(BufferedImage image) {
        TransferableImage trans = new TransferableImage(image);
        Clipboard c = Toolkit.getDefaultToolkit().getSystemClipboard();
        c.setContents(trans, null);
    }

    // Inner class to make the image transferable to the clipboard
    static class TransferableImage implements Transferable {
        private BufferedImage image;

        public TransferableImage(BufferedImage image) {
            this.image = image;
        }

        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (flavor.equals(DataFlavor.imageFlavor) && image != null) {
                return image;
            } else {
                throw new UnsupportedFlavorException(flavor);
            }
        }

        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return flavor.equals(DataFlavor.imageFlavor);
        }
    }

    // Method to get text from the clipboard
    public static String getClipboardText() throws UnsupportedFlavorException, IOException {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable contents = clipboard.getContents(null);
        boolean hasTransferableText = (contents != null) &&
                contents.isDataFlavorSupported(DataFlavor.stringFlavor);
        if (hasTransferableText) {
            return (String) contents.getTransferData(DataFlavor.stringFlavor);
        }
        return "";
    }

    // Method to check if the copied text is a valid character
    public static boolean isValidCharacter(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        text = text.trim();
        // Use regex to check if the text starts with a letter or number,
        // followed by zero or more diacritical marks
        return text.matches("^[\\p{L}\\p{N}][\\p{M}]*$");
    }
}
