package one.chartsy.kernel.libs.keepass;

import com.microsoft.credentialstorage.SecretStore;
import com.microsoft.credentialstorage.StorageProvider;
import com.microsoft.credentialstorage.model.StoredCredential;
import org.linguafranca.pwdb.Database;
import org.linguafranca.pwdb.kdbx.KdbxCreds;
import org.linguafranca.pwdb.kdbx.jackson.JacksonDatabase;

import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;

final class DefaultKeePassDatabaseLoader {

    private static final String STORAGE_KEY = DefaultKeePassDatabaseLoader.class.getName();
    private static final String FILE_NAME = "chartsy.kdbx";

    /**
     * Retrieves the master password from the secret store.
     * If the password does not exist, it generates a new one, stores it, and returns it.
     *
     * @throws AssertionError if the master password cannot be retrieved or created.
     */
    static char[] getMasterPassword() {
        SecretStore<StoredCredential> credentialStore = StorageProvider
                .getCredentialStorage(true, StorageProvider.SecureOption.REQUIRED);

        StoredCredential existingCredential = credentialStore.get(STORAGE_KEY);
        if (existingCredential == null)
            throw new AssertionError("Failed to retrieve master password.");

        return existingCredential.getPassword();
    }

    static Database<?, ?, ?, ?> getKeePassDatabase() throws IOException {
        return getKeePassDatabase(getMasterPassword());
    }

    static Database<?, ?, ?, ?> getKeePassDatabase(char[] masterPassword) throws IOException {
        KdbxCreds credentials = new KdbxCreds(new String(masterPassword).getBytes(StandardCharsets.UTF_8));
        try (InputStream is = Files.newInputStream(Path.of(FILE_NAME).toAbsolutePath())) {
            return new DefaultKeePassDatabase(JacksonDatabase.load(credentials, is));
        }
    }

    public static class ChangeMasterPasswordCLI {

        private static final String PASSWORD_PROMPT = "Enter new master password (Press Enter to use a suggested password): ";
        private static final String CONFIRM_PASSWORD_PROMPT = "Confirm new master password: ";
        private static final String OVERRIDE_PROMPT = "Credentials already exist. Do you want to override them? (yes/no): ";
        private static final int PASSWORD_LENGTH = 20;

        public static void main(String[] args) {
            SecretStore<StoredCredential> credentialStorage;
            try {
                credentialStorage = StorageProvider.getCredentialStorage(true, StorageProvider.SecureOption.REQUIRED);
            } catch (Exception e) {
                System.err.println("Failed to access credential storage: " + e.getMessage());
                return;
            }

            Console console = System.console();
            if (console == null) {
                System.err.println("No `System::console` available. Interactive mode will be disabled.");
                return;
            }

            char[] newPassword = null;
            char[] confirmPassword = null;
            try {
                // Step 1: Generate a suggested secure password
                String suggestedPassword = generateSecurePassword();
                System.out.println("A secure password has been generated for you: " + suggestedPassword);

                // Step 2: Prompt the user to enter a new password or accept the suggested one
                System.out.print(PASSWORD_PROMPT);
                char[] enteredPassword = (console != null) ? console.readPassword() : new char[0];

                if (enteredPassword.length == 0) {
                    // User accepted the suggested password
                    newPassword = suggestedPassword.toCharArray();
                    System.out.println("Using the suggested password.");
                } else {
                    // User entered a custom password
                    newPassword = enteredPassword;
                    // Prompt for confirmation
                    System.out.print(CONFIRM_PASSWORD_PROMPT);
                    confirmPassword = console.readPassword();

                    if (!Arrays.equals(newPassword, confirmPassword)) {
                        System.out.println("Passwords do not match. Please try again.");
                        // Clear entered passwords
                        Arrays.fill(newPassword, ' ');
                        if (confirmPassword != null) {
                            Arrays.fill(confirmPassword, ' ');
                        }
                        return;
                    }
                }

                // Perform password strength validation here
                if (!isPasswordStrong(newPassword)) {
                    System.out.println("Password does not meet strength requirements.");
                    return;
                }

                // Step 3: Create StoredCredential
                StoredCredential credential = new StoredCredential("user", newPassword);

                // Step 4: Check for existing credentials and confirm override
                if (credentialStorage.get(STORAGE_KEY) != null) {
                    System.out.print(OVERRIDE_PROMPT);
                    String answer = (console != null) ? console.readLine().trim().toLowerCase() : "no";

                    if (!answer.equals("yes") && !answer.equals("y")) {
                        System.out.println("Operation cancelled.");
                        // Clear password arrays
                        Arrays.fill(newPassword, ' ');
                        if (confirmPassword != null) {
                            Arrays.fill(confirmPassword, ' ');
                        }
                        return;
                    }
                }

                // Step 5: Store the credential
                credentialStorage.add(STORAGE_KEY, credential);
                System.out.println("Master password changed successfully.");

            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("An error occurred: " + e);
            } finally {
                // Clear sensitive data from memory
                if (newPassword != null) {
                    Arrays.fill(newPassword, ' ');
                }
                if (confirmPassword != null) {
                    Arrays.fill(confirmPassword, ' ');
                }
            }
        }

        /**
         * Generates a secure password using a combination of upper case letters, lower case letters,
         * digits, and special characters. The password is shuffled to ensure randomness.
         *
         * @return A securely generated password as a String.
         */
        public static String generateSecurePassword() {
            return generateSecurePassword(PASSWORD_LENGTH);
        }

        /**
         * Generates a secure password of the specified length using a combination of upper case letters,
         * lower case letters, digits, and special characters. Ensures that the password contains at least
         * one character from each category and shuffles the characters to enhance randomness.
         *
         * @param length The desired length of the password.
         * @return A securely generated password as a String.
         */
        public static String generateSecurePassword(int length) {
            final String upperCase = "ABCDEFGHJKLMNPQRSTUVWXYZ"; // Excludes 'I' and 'O'
            final String lowerCase = "abcdefghijkmnopqrstuvwxyz"; // Excludes 'l'
            final String digits = "23456789"; // Excludes '0' and '1'
            final String specialChars = "!@#$%^&*()-_=+[]{};:,.<>?";

            SecureRandom random = new SecureRandom();
            StringBuilder password = new StringBuilder(length);

            // Ensure the password has at least one character from each required set
            password.append(upperCase.charAt(random.nextInt(upperCase.length())));
            password.append(lowerCase.charAt(random.nextInt(lowerCase.length())));
            password.append(digits.charAt(random.nextInt(digits.length())));
            password.append(specialChars.charAt(random.nextInt(specialChars.length())));

            // Fill the remaining length with a mix of all allowed characters
            String allAllowed = upperCase + lowerCase + digits + specialChars;
            for (int i = 4; i < length; i++) {
                password.append(allAllowed.charAt(random.nextInt(allAllowed.length())));
            }

            // Shuffle the characters to ensure randomness
            char[] passwordArray = password.toString().toCharArray();
            for (int i = 0; i < passwordArray.length; i++) {
                int randomIndex = random.nextInt(passwordArray.length);
                char temp = passwordArray[i];
                passwordArray[i] = passwordArray[randomIndex];
                passwordArray[randomIndex] = temp;
            }

            return new String(passwordArray);
        }

        /**
         * Optional method to validate password strength. Implement your own criteria as needed.
         *
         * @param password The password to validate as a char array.
         * @return True if the password meets strength requirements, false otherwise.
         */
        private static boolean isPasswordStrong(char[] password) {
            String pwd = new String(password);
            // Example criteria: at least 8 characters, contains upper and lower case letters, digits, and special characters
            if (pwd.length() < 8)
                return false;
            else if (!pwd.matches(".*[A-Z].*"))
                return false;
            else if (!pwd.matches(".*[a-z].*"))
                return false;
            else if (!pwd.matches(".*\\d.*"))
                return false;
            else if (!pwd.matches(".*[!@#$%^&*()\\-_=+\\[\\]{};:,.<>?].*"))
                return false;
            else
                return true;
        }
    }
}