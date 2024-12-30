package one.chartsy.misc.captcha.automation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Interface for solving CAPTCHA images by extracting the embedded text.
 */
public interface CaptchaSolver {

    /**
     * Converts CAPTCHA image data to its corresponding text representation.
     *
     * @param imageData the byte array containing the CAPTCHA image data
     * @return the extracted text from the CAPTCHA image
     * @throws IOException if an I/O error occurs while processing the image data
     * @throws InterruptedException if the thread is interrupted during processing
     */
    String imageToText(byte[] imageData) throws IOException, InterruptedException;

    /**
     * Converts a CAPTCHA image located at the specified path to its corresponding text representation.
     *
     * @param imagePath the path to the CAPTCHA image file
     * @return the extracted text from the CAPTCHA image
     * @throws IOException if an I/O error occurs while reading the image file or processing the image data
     * @throws InterruptedException if the thread is interrupted during processing
     */
    default String imageToText(Path imagePath) throws IOException, InterruptedException {
        return imageToText(Files.readAllBytes(imagePath));
    }
}
