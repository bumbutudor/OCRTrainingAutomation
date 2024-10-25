package org.example;

import org.sikuli.script.*;
import java.awt.datatransfer.*;
import java.awt.image.BufferedImage;
import java.awt.Toolkit;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import javax.imageio.ImageIO;

public class Main {
    private static Screen screen = new Screen();

    private static final String IMAGE_FOLDER = "C:\\Users\\mariu\\Desktop\\OCRTrainingAutomation\\src\\main\\resources\\FineReaderAutomation\\Screen2\\";
    private static final String FINEREADER_ICON = IMAGE_FOLDER + "FR15_icon.png";
    private static final String PATTERN_TRAINING_WINDOW = IMAGE_FOLDER + "pattern_training_window.png";
    private static final String NEXT_BUTTON = IMAGE_FOLDER + "skip.png"; // Imaginea butonului "Skip" sau "Train"
    private static final String INPUT = IMAGE_FOLDER + "input.png"; // Imaginea casetei de input

    // Contor pentru numele fișierelor de caractere
    private static int characterCounter = 1;

    public static void main(String[] args) {
        try {
            // Creează un folder de output unic pentru sesiune
            String uniqueOutputFolder = IMAGE_FOLDER + "output_" + UUID.randomUUID().toString() + "\\";
            File outputDir = new File(uniqueOutputFolder);
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            // Pasul 1: Deschide ABBYY FineReader
            Pattern fineReaderIconPattern = new Pattern(FINEREADER_ICON);
            screen.wait(fineReaderIconPattern, 10);
            screen.doubleClick(fineReaderIconPattern); // Deschide programul

            // Așteaptă ca FineReader să se deschidă complet
            Pattern patternTrainingWindowPattern = new Pattern(PATTERN_TRAINING_WINDOW);
            screen.wait(patternTrainingWindowPattern, 15);
            Region trainingWindowRegion = screen.find(patternTrainingWindowPattern);

            // Verifică dacă regiunea a fost găsită
            if (trainingWindowRegion == null) {
                System.out.println("Nu a fost găsită fereastra de antrenare!");
                return;
            }

            // Bucla principală pentru procesarea caracterelor
            while (true) {
                Thread.sleep(1000); // Pauză pentru stabilizarea interfeței

                // Capturează imaginea ferestrei de antrenare
                ScreenImage trainingWindowImage = screen.capture(trainingWindowRegion);
                BufferedImage trainingBufferedImage = trainingWindowImage.getImage();

                // Salvează imaginea ferestrei de antrenare pentru verificare (opțional)
                File trainingWindowFile = new File(uniqueOutputFolder + "last_training_window.png");
                ImageIO.write(trainingBufferedImage, "png", trainingWindowFile);
                System.out.println("Screenshot al ferestrei de antrenare salvat la: " + trainingWindowFile.getAbsolutePath());

                // Detectează caseta verde în imagine
                Region greenBoxRegion = detectGreenBox(trainingWindowRegion, trainingBufferedImage);

                if (greenBoxRegion == null) {
                    System.out.println("Nu a fost găsită caseta verde în fereastra de antrenare!");
                    break; // Ieși din buclă dacă nu mai există caseta verde
                }

                // Evidențiază regiunea casetei verzi timp de 1 secundă
                greenBoxRegion.highlight(1); // Evidențiază timp de 1 secundă

                // Capturează imaginea caracterului din caseta verde
                ScreenImage characterImage = screen.capture(greenBoxRegion);
                BufferedImage characterBufferedImage = characterImage.getImage();

                // Salvează imaginea originală a caracterului
                String originalCharacterImagePath = uniqueOutputFolder + "character_" + characterCounter + "_original.png";
                File originalCharacterImageFile = new File(originalCharacterImagePath);
                ImageIO.write(characterBufferedImage, "png", originalCharacterImageFile);
                System.out.println("Imaginea originală a caracterului a fost salvată la: " + originalCharacterImageFile.getAbsolutePath());

                // Procesează imaginea pentru a elimina pixeli diferiți de negru
                BufferedImage processedCharacterImage = processCharacterImage(characterBufferedImage);

                // Salvează imaginea procesată a caracterului
                String processedCharacterImagePath = uniqueOutputFolder + "character_" + characterCounter + "_processed.png";
                File processedCharacterImageFile = new File(processedCharacterImagePath);
                ImageIO.write(processedCharacterImage, "png", processedCharacterImageFile);
                System.out.println("Imaginea procesată a caracterului a fost salvată la: " + processedCharacterImageFile.getAbsolutePath());

                // Copiază imaginea procesată în clipboard
                copyImageToClipboard(processedCharacterImage);
                System.out.println("Caracterul a fost procesat și copiat în clipboard!");

                // Interacționează cu caseta de input pentru a copia caracterul recunoscut
                Pattern inputBoxPattern = new Pattern(INPUT);
                screen.wait(inputBoxPattern, 15);
                Region inputBoxRegion = screen.find(inputBoxPattern);

                // Click pe caseta de input
                screen.click(inputBoxRegion);

                // Selectează tot textul și copiază în clipboard
                screen.type("a", KeyModifier.CTRL); // Ctrl+A (Select All)
                screen.type("c", KeyModifier.CTRL); // Ctrl+C (Copy)

                // Așteaptă puțin pentru a asigura copierea
                Thread.sleep(500);

                // Obține textul din clipboard
                String copiedText = getClipboardText();

                if (isValidCharacter(copiedText)) {
                    String character = copiedText.trim();

                    // Creează un folder cu numele caracterului dacă nu există
                    String characterFolderPath = uniqueOutputFolder + character + "\\";
                    File characterFolder = new File(characterFolderPath);
                    if (!characterFolder.exists()) {
                        characterFolder.mkdirs();
                        System.out.println("Folder creat pentru caracterul: " + character);
                    }

                    // Mută imaginea procesată în folderul caracterului
                    File sourceFile = processedCharacterImageFile;
                    File destFile = new File(characterFolderPath + sourceFile.getName());

                    boolean success = sourceFile.renameTo(destFile);
                    if (success) {
                        System.out.println("Imaginea procesată a caracterului a fost mutată în folderul: " + destFile.getAbsolutePath());
                    } else {
                        System.out.println("Eroare la mutarea imaginii procesate a caracterului.");
                    }
                } else {
                    System.out.println("Caracter invalid sau gol. Imaginea procesată nu va fi mutată.");
                }

                // Crește contorul pentru următorul caracter
                characterCounter++;

                // Apasă butonul "Next" sau "Train" pentru a trece la următorul caracter
                Pattern skipButtonPattern = new Pattern(NEXT_BUTTON);
                screen.wait(skipButtonPattern, 10);
                screen.click(skipButtonPattern);
            }

            System.out.println("Procesarea caracterelor s-a încheiat.");

        } catch (FindFailed e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (UnsupportedFlavorException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Metoda pentru a detecta caseta verde în imagine
    public static Region detectGreenBox(Region trainingWindowRegion, BufferedImage trainingBufferedImage) {
        // Definim culoarea verde specifică casetei
        Color targetColor = new Color(128, 220, 128, 255); // Ajustează valorile RGB dacă este necesar
        int colorTolerance = 30; // Toleranța culorii (poți ajusta această valoare)

        // Variabile pentru coordonate
        int minX = trainingBufferedImage.getWidth();
        int minY = trainingBufferedImage.getHeight();
        int maxX = 0;
        int maxY = 0;

        // Parcurgem pixelii imaginii
        for (int y = 0; y < trainingBufferedImage.getHeight(); y++) {
            for (int x = 0; x < trainingBufferedImage.getWidth(); x++) {
                int pixelColor = trainingBufferedImage.getRGB(x, y);
                Color color = new Color(pixelColor, true); // Al doilea parametru 'true' pentru a include alfa

                // Verificăm dacă pixelul este verde în limitele toleranței
                if (isColorMatch(color, targetColor, colorTolerance)) {
                    // Actualizăm coordonatele extreme
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }

        // Verificăm dacă am găsit coordonatele casetei verzi
        if (minX >= maxX || minY >= maxY) {
            return null; // Caseta verde nu a fost găsită
        }

        // Determinăm coordonatele casetei verzi în raport cu ecranul
        int greenBoxX = trainingWindowRegion.getX() + minX;
        int greenBoxY = trainingWindowRegion.getY() + minY;
        int greenBoxW = maxX - minX;
        int greenBoxH = maxY - minY;

        // Creăm regiunea casetei verzi
        Region greenBoxRegion = new Region(greenBoxX, greenBoxY, greenBoxW, greenBoxH);

        return greenBoxRegion;
    }

    // Metodă pentru a verifica dacă două culori se potrivesc în limitele unei toleranțe
    public static boolean isColorMatch(Color c1, Color c2, int tolerance) {
        int diffRed = Math.abs(c1.getRed() - c2.getRed());
        int diffGreen = Math.abs(c1.getGreen() - c2.getGreen());
        int diffBlue = Math.abs(c1.getBlue() - c2.getBlue());

        return (diffRed <= tolerance) && (diffGreen <= tolerance) && (diffBlue <= tolerance);
    }

// Metoda pentru a procesa imaginea caracterului, păstrând toate nuanțele de gri
    public static BufferedImage processCharacterImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        BufferedImage processedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixelColor = image.getRGB(x, y);
                Color color = new Color(pixelColor, true);

                // Verifică dacă pixelul este în nuanțe de gri (R, G și B sunt egale)
                if (color.getRed() == color.getGreen() && color.getGreen() == color.getBlue()) {
                    // Păstrează pixelul în nuanțe de gri
                    processedImage.setRGB(x, y, pixelColor);
                } else {
                    // Setează alți pixeli ca fiind albi
                    processedImage.setRGB(x, y, Color.WHITE.getRGB());
                }
            }
        }

        return processedImage;
    }

    // Metoda pentru a copia o imagine în clipboard
    public static void copyImageToClipboard(BufferedImage image) {
        TransferableImage trans = new TransferableImage(image);
        Clipboard c = Toolkit.getDefaultToolkit().getSystemClipboard();
        c.setContents(trans, null);
    }

    // Clasă internă pentru a face imaginea transferabilă în clipboard
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

    // Metoda pentru a obține textul din clipboard
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

    // Metoda pentru a verifica dacă textul copiat este un caracter valid
    public static boolean isValidCharacter(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        text = text.trim();
        // Utilizează regex pentru a verifica dacă textul începe cu o literă sau cifră,
        // urmată de zero sau mai multe semne diacritice
        return text.matches("^[\\p{L}\\p{N}][\\p{M}]*$");
    }

}
