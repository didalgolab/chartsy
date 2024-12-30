package one.chartsy.misc.captcha.automation;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openide.util.Lookup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AntiCaptchaClientTest {

    @Disabled("""
            To enable:
            - Add `chartsy.kdbx` file to project root directory.
            - Configure KeePass entry `one.chartsy.misc.captcha.automation.AntiCaptchaClient` with the correct API key.
            """)
    @Test
    void imageToText() throws IOException, InterruptedException {
        var captchaSolver = Lookup.getDefault().lookup(AntiCaptchaClient.class);
        var imagePath = Path.of("src/test/resources/captcha.png").toAbsolutePath();
        var captchaText = captchaSolver.imageToText(Files.readAllBytes(imagePath));

        assertEquals("7EJZ", captchaText);
    }
}