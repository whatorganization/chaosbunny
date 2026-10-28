import java.security.MessageDigest;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ChaosBunny {

    private static double[] deriveKeyParameters(String key) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));

        long x0Int = ((hash[0] & 0xFFL) << 24) | ((hash[1] & 0xFFL) << 16) |
                     ((hash[2] & 0xFFL) << 8) | (hash[3] & 0xFFL);
        double x0 = 0.1 + 0.8 * (Math.abs(x0Int) / (double) Integer.MAX_VALUE);

        long rInt = ((hash[4] & 0xFFL) << 24) | ((hash[5] & 0xFFL) << 16) |
                    ((hash[6] & 0xFFL) << 8) | (hash[7] & 0xFFL);
        double r = 3.9 + 0.09 * (Math.abs(rInt) / (double) Integer.MAX_VALUE);

        return new double[]{x0, r};
    }

    private static byte[] processData(byte[] data, String key) throws Exception {
        double[] params = deriveKeyParameters(key);
        double x = params[0];
        double r = params[1];

        byte[] result = new byte[data.length];

        for (int i = 0; i < data.length; i++) {
            x = r * x * (1.0 - x);
            int keystreamByte = (int) ((x * 100000000) % 256);
            result[i] = (byte) (data[i] ^ keystreamByte);
        }
        return result;
    }

    public static String encrypt(String plaintext, String key) throws Exception {
        byte[] encryptedBytes = processData(plaintext.getBytes(StandardCharsets.UTF_8), key);
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    public static String decrypt(String ciphertextBase64, String key) throws Exception {
        byte[] ciphertext = Base64.getDecoder().decode(ciphertextBase64);
        byte[] decryptedBytes = processData(ciphertext, key);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            System.out.println("--- Chaos Bunny Cipher ---");
            System.out.println("1. Encrypt text");
            System.out.println("2. Decrypt text");
            System.out.println("3. Exit");
            System.out.print("Choose an option: ");
            
            String choice = scanner.nextLine();
            
            try {
                if (choice.equals("1")) {
                    System.out.print("Enter plaintext: ");
                    String plaintext = scanner.nextLine();
                    System.out.print("Enter secret key: ");
                    String key = scanner.nextLine();
                    
                    String ciphertext = encrypt(plaintext, key);
                    System.out.println("Encrypted (Base64): " + ciphertext + "\n");
                    
                } else if (choice.equals("2")) {
                    System.out.print("Enter Base64 ciphertext: ");
                    String ciphertext = scanner.nextLine();
                    System.out.print("Enter secret key: ");
                    String key = scanner.nextLine();
                    
                    String decrypted = decrypt(ciphertext, key);
                    System.out.println("Decrypted Text: " + decrypted + "\n");
                    
                } else if (choice.equals("3")) {
                    System.out.println("Exiting Chaos Bunny...");
                    break;
                } else {
                    System.out.println("Invalid option. Please enter 1, 2, or 3.\n");
                }
            } catch (IllegalArgumentException e) {
                System.out.println("Error: Invalid Base64 ciphertext provided.\n");
            } catch (Exception e) {
                System.out.println("An error occurred during processing.\n");
            }
        }
        
        scanner.close();
    }
}