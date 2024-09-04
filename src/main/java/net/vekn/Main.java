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
        if (args.length != 2) {
            log.error("Error: Exactly two arguments are required.");
            log.error("Usage: java net.vekn.Main <filename> <imagefolder>");
            System.exit(1);
        }

        String filename = args[0];
        String imagefolder = args[1];

        // Validate that the filename is not empty
        if (filename.trim().isEmpty()) {
            log.error("Error: The filename argument cannot be empty.");
            log.error("Usage: java net.vekn.Main <filename> <imagefolder>");
            System.exit(1);
        }

        // Validate that the imagefolder is not empty
        if (imagefolder.trim().isEmpty()) {
            log.error("Error: The imagefolder argument cannot be empty.");
            log.error("Usage: java net.vekn.Main <filename> <imagefolder>");
            System.exit(1);
        }

        log.info("Starting application with filename: {} and image folder: {}", filename, imagefolder);

        Path filePath = Paths.get(filename);

        Map<String, Integer> library = new HashMap<>();
        Map<String, Integer> crypt = new HashMap<>();

        try {
            List<String> lines = Files.readAllLines(filePath);
            parseLibrarySection(lines, library);
            processLibraryEntries(library, imagefolder);
            parseCryptSection(lines, crypt);
            processCryptEntries(crypt, imagefolder);

            List<File> imageFiles = new ArrayList<>();
            imageFiles.addAll(getImageFiles(crypt, imagefolder).stream().sorted().toList());
            imageFiles.addAll(getImageFiles(library, imagefolder).stream().sorted().toList());

            addImagesToPdf(imageFiles, new File(filename.substring(0, filename.length() - 4) + ".pdf"));

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
                int libSize = library.values().stream().mapToInt(Integer::intValue).sum();
                log.info("found {} different cards, totalling to {} cards", library.size(), libSize);
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

    private static void processLibraryEntries(Map<String, Integer> library, String imagefolder) {
        for (Map.Entry<String, Integer> entry : library.entrySet()) {
            String pathString = imagefolder + File.separator + entry.getKey() + ".jpg";
            Path path = Paths.get(pathString);
            log.debug("Checking path: {}", path);

            if (Files.exists(path)) {
                log.debug("Crypt found for card {}", entry.getKey());

            } else {
                log.error("No File found for library card {}!", entry.getKey());
            }

        }
    }

    private static void processCryptEntries(Map<String, Integer> crypt, String imagefolder) {
        Map<String, Integer> updatedCrypt = new HashMap<>(crypt);
        log.debug("Processing {} crypt entries", crypt.size());

        for (Map.Entry<String, Integer> entry : crypt.entrySet()) {
            String cardName = entry.getKey();
            boolean multipleGroupsFound = false;
            boolean groupFound = false;
            int foundGroup = -1;

            Path path = Paths.get(imagefolder + File.separator + cardName + ".jpg");
            log.debug("Checking path: {}", path);
            if (cardName.matches(".*g[1-7](adv)?$")) {
                int group = Character.getNumericValue(cardName.charAt(cardName.length() - 1));
                log.debug("Key already ends with group indicator: {}. Checking only group {}", cardName, group);
                checkGroup(imagefolder, cardName, group, true);
            } else {
                for (int group = 1; group <= 7; group++) {
                    if (checkGroup(imagefolder, cardName, group, false)) {
                        if (groupFound) {
                            multipleGroupsFound = true;
                            break;
                        }
                        groupFound = true;
                        foundGroup = group;
                    }
                }
                handleGroupResults(updatedCrypt, cardName, multipleGroupsFound, groupFound, foundGroup, entry.getValue());
            }


        }

        crypt.clear();
        crypt.putAll(updatedCrypt);
    }

    private static boolean checkGroup(String imagefolder, String cardName, int group, boolean isExactKey) {
        String groupSuffix = "g" + group;

        String adjustedCardName;
        if (isExactKey) {
            adjustedCardName = cardName;
        } else {
            if (cardName.endsWith("adv")) {
                adjustedCardName = cardName.substring(0, cardName.length() - 3) + groupSuffix + "adv";
            } else {
                adjustedCardName = cardName + groupSuffix;
            }
        }

        String pathString = imagefolder + File.separator + adjustedCardName + ".jpg";
        Path path = Paths.get(pathString);
        log.debug("Checking path: {}", path);

        if (Files.exists(path)) {
            log.debug("File found for crypt card {} in group {}", adjustedCardName, group);
            return true;
        } else {
            log.error("No File found for crypt card {}!", adjustedCardName);
        }
        return false;
    }

    private static void handleGroupResults(Map<String, Integer> updatedCrypt, String key, boolean multipleGroupsFound, boolean groupFound, Integer foundGroup, int qty) {
        if (multipleGroupsFound) {
            log.error("Multiple groups found for card {}", key);
            throw new RuntimeException("Multiple groups found for card " + key);
        } else if (groupFound) {
            String newKey = key + (foundGroup != null ? "g" + foundGroup : "");
            log.info("Only one group found. Updating key from {} to {}", key, newKey);
            updatedCrypt.put(newKey, qty);
            updatedCrypt.remove(key);
        } else {
            log.warn("No group found for card {}", key);
        }
    }

    private static String buildImageName(String cardName) {
        if (cardName == null) {
            return null;
        }
        return cardName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
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
