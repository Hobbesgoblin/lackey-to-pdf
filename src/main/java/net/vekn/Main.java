package net.vekn;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class Main {
    public static void main(String[] args) {
        String filename = args[0];
        String imagefolder = args[1];

        log.info("Starting application with filename: {} and image folder: {}", filename, imagefolder);

        Path filePath = Paths.get(filename);

        Map<String, Integer> library = new HashMap<>();
        Map<String, Integer> crypt = new HashMap<>();

        try {
            List<String> lines = Files.readAllLines(filePath);
            parseLibrarySection(lines, library);
            parseCryptSection(lines, crypt);
            processCryptEntries(crypt, imagefolder);

            List<File> imageFiles = new ArrayList<>();
            imageFiles.addAll(getImageFiles(crypt, imagefolder).stream().sorted().toList());
            imageFiles.addAll(getImageFiles(library, imagefolder).stream().sorted().toList());

            addImagesToPdf(imageFiles, new File("output.pdf"));

            log.info("PDF generation completed successfully.");

        } catch (IOException e) {
            log.error("Error during file processing: {}", e.getMessage(), e);
        }
    }

    private static void parseLibrarySection(List<String> lines, Map<String, Integer> library) {
        int numberOfLines = lines.size();
        log.debug("Parsing library section with {} lines", numberOfLines);

        for (int i = 0; i < numberOfLines; i++) {
            String line = lines.removeFirst();
            String[] card = line.split("\\s+", 2);

            if (!card[0].toLowerCase().startsWith("crypt")) {
                log.debug("Adding card to library: Card= {}, Qty= {}", card[1], card[0]);
                library.put(buildImageName(card[1]), Integer.parseInt(card[0]));
            } else {
                log.info("Crypt section detected. Stopping library parsing.");
                break;
            }
        }
    }

    private static void parseCryptSection(List<String> lines, Map<String, Integer> crypt) {
        int numberOfLines = lines.size();
        log.debug("Parsing crypt section with {} lines", numberOfLines);

        for (int i = 0; i < numberOfLines; i++) {
            String line = lines.removeFirst();
            String[] card = line.split("\\s+", 2);

            log.debug("Adding card to crypt: Vampire= {}, Qty= {}", card[1], card[0]);
            crypt.put(buildImageName(card[1]), Integer.parseInt(card[0]));
        }
    }

    private static void processCryptEntries(Map<String, Integer> crypt, String imagefolder) {
        Map<String, Integer> updatedCrypt = new HashMap<>(crypt);
        log.debug("Processing {} crypt entries", crypt.size());

        for (Map.Entry<String, Integer> entry : crypt.entrySet()) {
            String key = entry.getKey();
            boolean multipleGroupsFound = false;
            boolean groupFound = false;
            int foundGroup = -1;

            if (key.matches(".*g[1-7]$")) {
                int group = Character.getNumericValue(key.charAt(key.length() - 1));
                log.debug("Key already ends with group indicator: {}. Checking only group {}", key, group);
                groupFound = checkGroup(imagefolder, key, group, true);
            } else {
                for (int group = 1; group <= 7; group++) {
                    if (checkGroup(imagefolder, key, group, false)) {
                        if (groupFound) {
                            multipleGroupsFound = true;
                            break;
                        }
                        groupFound = true;
                        foundGroup = group;
                    }
                }
            }

            handleGroupResults(updatedCrypt, key, multipleGroupsFound, groupFound, foundGroup, entry);
        }

        crypt.clear();
        crypt.putAll(updatedCrypt);
    }

    private static boolean checkGroup(String imagefolder, String key, int group, boolean isExactKey) {
        String pathString = imagefolder + File.separator + (isExactKey ? key : key + "g" + group) + ".jpg";
        Path path = Paths.get(pathString);
        log.debug("Checking path: {}", path);

        if (Files.exists(path)) {
            log.debug("Crypt found for card {} in group {}", key, group);
            return true;
        }
        return false;
    }

    private static void handleGroupResults(Map<String, Integer> updatedCrypt, String key, boolean multipleGroupsFound, boolean groupFound, int foundGroup, Map.Entry<String, Integer> entry) {
        if (multipleGroupsFound) {
            log.error("Multiple groups found for card {}", key);
            throw new RuntimeException("Multiple groups found for card " + key);
        } else if (groupFound) {
            String newKey = key + "g" + foundGroup;
            log.info("Only one group found. Updating key from {} to {}", key, newKey);
            updatedCrypt.put(newKey, entry.getValue());
            updatedCrypt.remove(key);
        } else {
            log.warn("No group found for card {}", key);
        }
    }

    private static String buildImageName(String cardName) {
        if (cardName == null) {
            return null;
        }
        return cardName.replaceAll("[()\\s]", "").toLowerCase();
    }

    private static List<File> getImageFiles(Map<String, Integer> crypt, String imagefolder) {
        List<File> imageFiles = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : crypt.entrySet()) {
            String imageName = entry.getKey();
            int quantity = entry.getValue();
            File imageFile = new File(imagefolder + File.separator + imageName + ".jpg");

            if (imageFile.exists()) {
                for (int i = 0; i < quantity; i++) {
                    imageFiles.add(imageFile);
                }
            }
        }

        return imageFiles;
    }


    private static void addImagesToPdf(List<File> imageFiles, File outputPdfFile) throws IOException {
        final float imageWidthCm = 6.35f; // Image width in cm
        final float imageHeightCm = 8.89f; // Image height in cm
        final float imageWidthPt = imageWidthCm * 28.35f; // Convert cm to points
        final float imageHeightPt = imageHeightCm * 28.35f; // Convert cm to points

        final int imagesPerRow = 3;
        final int rowsPerPage = 3;
        final float margin = 1f; // Margin between images
        final float xOffset = 25f; // X offset from left margin
        final float yOffset = 25f; // Y offset from top margin

        try (PDDocument document = new PDDocument()) {
            int totalImages = imageFiles.size();
            int pageCount = (int) Math.ceil((double) totalImages / (imagesPerRow * rowsPerPage));
            log.debug("Generating PDF with {} pages for {} images", pageCount, totalImages);

            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);

                try (PDPageContentStream contentStream = new PDPageContentStream(document, page, AppendMode.APPEND, true, true)) {
                    float yStart = page.getMediaBox().getHeight() - yOffset - imageHeightPt; // Start position from top
                    int startIndex = pageIndex * (imagesPerRow * rowsPerPage);
                    int endIndex = Math.min(startIndex + (imagesPerRow * rowsPerPage), totalImages);

                    int imageIndex = startIndex;
                    for (int row = 0; row < rowsPerPage; row++) {
                        float xPosition = xOffset; // Reset X position for each row
                        for (int col = 0; col < imagesPerRow; col++) {
                            if (imageIndex < endIndex) {
                                File imageFile = imageFiles.get(imageIndex++);
                                PDImageXObject pdImage = PDImageXObject.createFromFile(imageFile.getPath(), document);

                                contentStream.drawImage(pdImage,
                                    xPosition,
                                    yStart - row * (imageHeightPt + margin),
                                    imageWidthPt,
                                    imageHeightPt);
                                xPosition += imageWidthPt + margin; // Move X position to the right
                            }
                        }
                    }
                }
            }

            document.save(outputPdfFile);
            log.info("PDF saved successfully: {}", outputPdfFile.getAbsolutePath());
        }
    }

}
