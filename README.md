# OCRTrainingAutomation

OCRTrainingAutomation automates the process of capturing and processing character images in **ABBYY FineReader** for OCR training. The application relies on **SikuliX** to control the graphical interface of FineReader and uses Java for image handling and automation logic.

## Requirements

- **Java 17**
- **SikuliX** libraries on the classpath
- **ABBYY FineReader** installed (tested with version 15/16)

## Running the automation

1. Ensure the required software above is installed.
2. Adjust the `IMAGE_FOLDER` path in `src/main/java/org/example/Main.java` to point to the `Screen2` resource directory in this project.
3. Build the project with Maven or your IDE.
4. Launch the `Main` class. The program will prompt for an output folder name and then begin interacting with ABBYY FineReader to capture training characters.

## Resource images

All images used by SikuliX reside under [`src/main/resources/FineReaderAutomation/Screen2`](src/main/resources/FineReaderAutomation/Screen2). They were originally captured from the FineReader user interface at a resolution of 1920×1080. If the UI changes or you need to retake screenshots, use SikuliX's capture feature (or any screenshot tool) to create replacements at the same resolution and overwrite the files in that directory.

## Troubleshooting

- **Image matching fails** – Make sure your screen resolution matches the resolution of the provided images (1920×1080). Capture new images if necessary.
- **FineReader not found** – Update the images for the FineReader icon or paths if the installation location is different.
- **Wrong output path** – Verify that the `IMAGE_FOLDER` constant points to the correct resource folder.

