package org.example;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.sikuli.script.*;
import java.awt.datatransfer.*;
import java.awt.image.BufferedImage;
import java.awt.Toolkit;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;

public class Main {
    private static Screen screen = new Screen();

    private static final String IMAGE_FOLDER = "D:\\Disk D 13-09-25\\CS\\OCRTrainingAutomation\\src\\main\\resources\\FineReaderAutomation\\Screen2\\";
    private static final String FINEREADER_ICON = IMAGE_FOLDER + "FR16_icon.png";
    private static final String TEXT = IMAGE_FOLDER + "text.png";
    private static final String INPUT = IMAGE_FOLDER + "input.png"; // Imaginea casetei de input
    private static final String TRAIN = IMAGE_FOLDER + "train.png";
    private static final URI OCR_SERVICE_URI = URI.create("http://localhost:8000/predict");
    private static final int CONTEXT_PADDING = 30;
    private static final float PATTERN_SCALE = 0.5f; // 2880x1800 @200% -> 1440x900 logic
    private static final double ICON_SIMILARITY = 0.8;
    private static final double TEXT_SIMILARITY = 0.3;
    private static final double INPUT_SIMILARITY = 0.7;
    private static final double TRAIN_BUTTON_SIMILARITY = 0.7;
    private static final double HIGHLIGHT_DURATION = 0.8;
    private static final int PATTERN_RETRY_ATTEMPTS = 5;
    private static final int PATTERN_RETRY_TIMEOUT = 3;
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    // Contor pentru numele fișierelor de caractere
    private static int characterCounter = 1;
    private static File diagnosticsDirectory;

    public static void main(String[] args) {
        try {
            // Creează un folder de output unic pentru sesiune
            String uniqueOutputFolder = IMAGE_FOLDER + "output_" + UUID.randomUUID().toString() + "\\";
            ensureDirectory(uniqueOutputFolder);
            File diagnosticsDir = ensureDirectory(uniqueOutputFolder + "diagnostics\\");
            diagnosticsDirectory = diagnosticsDir;

            // Pasul 1: Deschide ABBYY FineReader
            log("Pregatim cautarea iconitei FineReader (%s)", FINEREADER_ICON);
            Pattern fineReaderIconPattern = loadPattern(FINEREADER_ICON, ICON_SIMILARITY);
            Match iconMatch = screen.exists(fineReaderIconPattern, 10);
            if (iconMatch != null) {
                log("Iconita gasita la %s, executam double-click", iconMatch.getRect());
                outlineRegion(iconMatch, "blue");
                screen.doubleClick(iconMatch); // Deschide programul
            } else {
                log("Iconita FineReader nu a fost gasita in 10s. Continuam sa cautam fereastra deschisa.");
            }

            // Așteaptă ca FineReader să se deschidă complet
            Thread.sleep(3000);
            log("Incepem ciclul de antrenare.");

            // Bucla principală pentru procesarea caracterelor
            while (true) {
                Thread.sleep(1000); // Pauză pentru stabilizarea interfeței

                // 1. Găsește zona cu text folosind pattern-ul text.png (cu retry)
                Pattern textPattern = loadPattern(TEXT, TEXT_SIMILARITY);
                Match textMatch = waitForPattern(textPattern, PATTERN_RETRY_ATTEMPTS, PATTERN_RETRY_TIMEOUT);
                
                if (textMatch == null) {
                    log("Pattern-ul text.png nu a fost gasit dupa %d incercari. Opresc automarea.", PATTERN_RETRY_ATTEMPTS);
                    break;
                }

                log("Pattern text.png gasit la %s", textMatch.getRect());
                outlineRegion(textMatch, "cyan");

                // 2. Capturează întreaga zonă de text
                ScreenImage textImage = screen.capture(textMatch);
                BufferedImage textBufferedImage = textImage.getImage();

                // Salvează imaginea zonei de text pentru verificare
                File textFile = new File(uniqueOutputFolder + "text_region_" + characterCounter + ".png");
                ImageIO.write(textBufferedImage, "png", textFile);
                log("Zona de text capturata la %s", textFile.getAbsolutePath());

                // 3. Trimite imaginea la API și obține caracterul recunoscut
                List<BufferedImage> contextImages = new ArrayList<>();
                contextImages.add(textBufferedImage);
                String recognizedCharacter = recognizeCharacter(textBufferedImage, contextImages);

                if (recognizedCharacter != null && !recognizedCharacter.isEmpty()) {
                    log("Caracter #%d recunoscut: '%s'", characterCounter, recognizedCharacter);

                    // 4. Mai întâi curăță caseta de input înainte de căutare
                    Pattern inputBoxPattern = loadPattern(INPUT, INPUT_SIMILARITY);
                    log("Cautam caseta de input pentru curatare folosind %s", INPUT);
                    Match inputMatchPreClean = waitForPattern(inputBoxPattern, PATTERN_RETRY_ATTEMPTS, PATTERN_RETRY_TIMEOUT);
                    if (inputMatchPreClean == null) {
                        log("Caseta de input nu a fost gasita pentru curatare. Opresc automarea.");
                        break;
                    }
                    
                    // Curăță caseta de input
                    screen.click(inputMatchPreClean);
                    screen.type("a", KeyModifier.CTRL);
                    screen.type(Key.BACKSPACE);
                    Thread.sleep(500); // Pauză pentru ca UI să se actualizeze
                    
                    // Acum caută din nou caseta de input curată pentru a introduce caracterul
                    log("Cautam din nou caseta de input curatata folosind %s", INPUT);
                    Match inputMatch = waitForPattern(inputBoxPattern, PATTERN_RETRY_ATTEMPTS, PATTERN_RETRY_TIMEOUT);
                    if (inputMatch == null) {
                        log("Caseta de input curatata nu a fost gasita dupa %d incercari. Opresc automarea.", PATTERN_RETRY_ATTEMPTS);
                        break;
                    }
                    outlineRegion(inputMatch, "yellow");

                    // Copiază caracterul recunoscut în clipboard și plasează în input
                    copyTextToClipboard(recognizedCharacter);
                    screen.click(inputMatch);
                    screen.type("v", KeyModifier.CTRL);

                    // 5. Apasă butonul "Train" (cu retry)
                    Pattern trainButtonPattern = loadPattern(TRAIN, TRAIN_BUTTON_SIMILARITY);
                    log("Cautam butonul Train (%s)", TRAIN);
                    Match trainMatch = waitForPattern(trainButtonPattern, PATTERN_RETRY_ATTEMPTS, PATTERN_RETRY_TIMEOUT);
                    if (trainMatch == null) {
                        log("Butonul Train nu a fost gasit dupa %d incercari. Opresc automarea.", PATTERN_RETRY_ATTEMPTS);
                        break;
                    }
                    outlineRegion(trainMatch, "magenta");
                    screen.click(trainMatch);
                    
                    // 6. Curăță câmpul de input după Train - pur și simplu apasă Backspace
                    Thread.sleep(500); // Pauză pentru ca UI să proceseze Train
                    log("Curatam campul de input cu Backspace");
                    screen.type(Key.BACKSPACE);
                    log("Backspace trimis pentru curatare");
                } else {
                    log("Recognizer-ul nu a intors un caracter valid pentru intrarea #%d", characterCounter);
                }

                // Crește contorul pentru următorul caracter
                characterCounter++;
            }

            log("Procesarea caracterelor s-a incheiat.");

        } catch (FindFailed e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // Metoda pentru a trimite imaginile la API-ul FastAPI și a prelua caracterul recunoscut
    public static String recognizeCharacter(BufferedImage primaryImage, List<BufferedImage> contextImages) {
        try {
            JsonObject payload = new JsonObject();
            payload.add("primary_image", buildImagePayload(primaryImage));

            JsonArray contextArray = new JsonArray();
            for (BufferedImage contextImage : contextImages) {
                if (contextImage != null) {
                    contextArray.add(buildImagePayload(contextImage));
                }
            }
            payload.add("context_images", contextArray);

            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);

            HttpURLConnection connection = (HttpURLConnection) OCR_SERVICE_URI.toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(body);
            }

            int status = connection.getResponseCode();
            InputStream responseStream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            if (responseStream == null) {
                log("Nu exista raspuns de la API-ul OCR.");
                return null;
            }

            String responseBody;
            try (InputStream stream = responseStream) {
                responseBody = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }

            if (status != HttpURLConnection.HTTP_OK) {
                log("Eroare API (%d): %s", status, responseBody);
                return null;
            }

            JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
            if (jsonResponse.has("prediction")) {
                return jsonResponse.get("prediction").getAsString();
            }

            log("Raspunsul API nu contine campul prediction: %s", responseBody);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
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

    private static BufferedImage captureContextImage(Region baseRegion) {
        if (baseRegion == null) {
            return null;
        }

        int x = Math.max(baseRegion.getX() - CONTEXT_PADDING, 0);
        int y = Math.max(baseRegion.getY() - CONTEXT_PADDING, 0);
        int width = baseRegion.getW() + CONTEXT_PADDING * 2;
        int height = baseRegion.getH() + CONTEXT_PADDING * 2;
        Region expandedRegion = new Region(x, y, width, height);
        ScreenImage contextCapture = screen.capture(expandedRegion);
        return contextCapture != null ? contextCapture.getImage() : null;
    }

    private static JsonObject buildImagePayload(BufferedImage image) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("data", encodeImageToBase64(image));
        payload.addProperty("mime_type", "image/png");
        return payload;
    }

    private static String encodeImageToBase64(BufferedImage image) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    public static void copyTextToClipboard(String text) {
        StringSelection stringSelection = new StringSelection(text);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
    }

    // Metoda pentru a copia o imagine în clipboard
    public static void copyImageToClipboard(BufferedImage image) {
        TransferableImage trans = new TransferableImage(image);
        Clipboard c = Toolkit.getDefaultToolkit().getSystemClipboard();
        c.setContents(trans, null);
    }

    private static Pattern loadPattern(String path, double similarity) {
        Pattern pattern = new Pattern(path);
        pattern = pattern.similar((float) similarity);
        if (PATTERN_SCALE != 1.0f) {
            log("Redimensionam pattern-ul %s cu factor %.2f", path, PATTERN_SCALE);
            pattern = pattern.resize(PATTERN_SCALE);
        }
        log("Pattern-ul %s foloseste similarity=%.2f", path, similarity);
        return pattern;
    }

    private static Match waitForPattern(Pattern pattern, int attempts, int timeoutSeconds) {
        Match match = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            log("(Retry %d/%d) asteptam pattern-ul %s cu timeout %ds", attempt, attempts, pattern.getFilename(), timeoutSeconds);
            match = screen.exists(pattern, timeoutSeconds);
            if (match != null) {
                log("Pattern-ul a fost gasit la %s in incercarea %d", match.getRect(), attempt);
                return match;
            }
            try {
                Thread.sleep(1000L * attempt);
            } catch (InterruptedException ignored) {
            }
        }
        return null;
    }

    private static void log(String message, Object... args) {
        String formatted = args.length == 0 ? message : String.format(message, args);
        System.out.println("[Automation] " + formatted);
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

    private static File ensureDirectory(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created) {
                log("Nu am putut crea directorul %s", path);
            }
        }
        return dir;
    }

    private static void saveScreenshot(Screen screen, File directory, String baseName, Region region) {
        if (screen == null || directory == null) {
            return;
        }
        ScreenImage capture = region != null ? screen.capture(region) : screen.capture();
        if (capture == null) {
            log("Nu s-a putut capta screenshot-ul pentru %s", baseName);
            return;
        }
        String filename = baseName.replace(".png", "") + "_" + TIMESTAMP.format(LocalDateTime.now()) + ".png";
        File target = new File(directory, filename);
        try {
            ImageIO.write(capture.getImage(), "png", target);
            log("Screenshot salvat la %s", target.getAbsolutePath());
        } catch (IOException e) {
            log("Nu am putut salva screenshot-ul la %s: %s", target.getAbsolutePath(), e.getMessage());
        }
    }

    private static void saveDiagnosticImage(BufferedImage image, String baseName) {
        if (diagnosticsDirectory == null || image == null) {
            return;
        }
        String filename = baseName.replace(".png", "") + "_" + TIMESTAMP.format(LocalDateTime.now()) + ".png";
        File target = new File(diagnosticsDirectory, filename);
        try {
            ImageIO.write(image, "png", target);
            log("Imagine diagnostica salvata la %s", target.getAbsolutePath());
        } catch (IOException e) {
            log("Nu am putut salva imaginea diagnostica la %s: %s", target.getAbsolutePath(), e.getMessage());
        }
    }

    private static void outlineRegion(Region region, String colorName) {
        if (region == null) {
            return;
        }
        try {
            region.highlight(HIGHLIGHT_DURATION, colorName);
        } catch (Exception e) {
            log("Nu am putut afisa chenarul pentru regiune: %s", e.getMessage());
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
