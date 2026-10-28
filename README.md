# Chaos Bunny: The Chaotic Logistic Map Cipher

## Overview

Chaos Bunny is a custom stream cipher built upon the principles of Chaos Theory. Instead of relying on traditional permutation or substitution networks, this algorithm utilizes the mathematical logistic map equation to generate a highly unpredictable pseudorandom keystream. This keystream is then combined with the plaintext using a symmetric XOR operation.

The primary security mechanism relies on the **Butterfly Effect** (extreme sensitivity to initial conditions). A microscopic change in the cryptographic key completely alters the trajectory of the chaotic sequence, resulting in a totally different keystream and ensuring the ciphertext remains undecipherable.

## Mathematical Foundation

The core engine of the cipher is the logistic map, a polynomial mapping of degree 2 that exhibits chaotic dynamic behavior. The equation is defined as:

$$x_{n+1} = r \cdot x_n \cdot (1 - x_n)$$

* $x_n$ is the current state of the system, a floating-point number representing the population ratio ($0 < x < 1$).
* $x_{n+1}$ is the next state of the system.
* $r$ is the control parameter (or growth rate). For the system to remain in a state of deep deterministic chaos, $r$ must strictly fall within the range of $3.56995$ to $4.0$.

## Algorithm Implementation Details

### 1. Deterministic Key Derivation

To seed the chaos equation, the user's text-based secret key must be transformed into the two critical initial conditions: $x_0$ (the starting state) and $r$ (the control parameter).

To ensure that even a single-character difference in the password drastically shifts these initial conditions, the key is hashed using SHA-256. The resulting 32-byte hash is split, converting specific byte blocks into the required floating-point boundaries.

```java
private static double[] deriveKeyParameters(String key) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));

    // Extract first 4 bytes to generate an x0 value strictly between 0.1 and 0.9
    long x0Int = ((hash[0] & 0xFFL) << 24) | ((hash[1] & 0xFFL) << 16) |
                 ((hash[2] & 0xFFL) << 8) | (hash[3] & 0xFFL);
    double x0 = 0.1 + 0.8 * (Math.abs(x0Int) / (double) Integer.MAX_VALUE);

    // Extract next 4 bytes to generate an r value strictly between 3.9 and 3.99 (deep chaos zone)
    long rInt = ((hash[4] & 0xFFL) << 24) | ((hash[5] & 0xFFL) << 16) |
                ((hash[6] & 0xFFL) << 8) | (hash[7] & 0xFFL);
    double r = 3.9 + 0.09 * (Math.abs(rInt) / (double) Integer.MAX_VALUE);

    return new double[]{x0, r};
}

```

### 2. Keystream Generation and Quantization

Once $x_0$ and $r$ are established, the algorithm loops through the logistic map equation for the exact length of the input data.

Because the logistic map outputs a 64-bit floating-point decimal between 0 and 1, the result must be quantized into an 8-bit integer (a byte value from 0 to 255) so it can be combined with standard ASCII/UTF-8 characters. This is achieved by multiplying the float by a large scalar ($10^8$) and applying a modulo 256 operation.

```java
// Iterate the chaotic logistic map equation
x = r * x * (1.0 - x);

// Quantize the floating-point result into an 8-bit integer (0-255)
int keystreamByte = (int) ((x * 100000000) % 256);

```

### 3. XOR Substitution (Encryption/Decryption)

Like most hardware-level stream ciphers, the actual encryption is performed using a bitwise XOR ($\oplus$) operation between the plaintext byte and the generated chaotic keystream byte.

Because XOR is its own mathematical inverse ($A \oplus B = C$, and $C \oplus B = A$), the exact same function is used for both encryption and decryption.

```java
private static byte[] processData(byte[] data, String key) throws Exception {
    double[] params = deriveKeyParameters(key);
    double x = params[0]; // x0
    double r = params[1]; // control parameter

    byte[] result = new byte[data.length];

    for (int i = 0; i < data.length; i++) {
        x = r * x * (1.0 - x); // Next chaos state
        int keystreamByte = (int) ((x * 100000000) % 256); // Quantize to byte
        
        // Apply XOR substitution
        result[i] = (byte) (data[i] ^ keystreamByte);
    }
    return result;
}

```

### 4. Base64 Encoding

The XOR operation frequently produces non-printable control characters. To allow the ciphertext to be safely copied, pasted, and transmitted via the interactive terminal menu, the final byte array is encoded into a Base64 string upon encryption and decoded back into bytes prior to decryption.

## Usage

Compile and run the cipher via the terminal:

```bash
javac ChaoticLogisticCipher.java
java ChaoticLogisticCipher

```

The program will launch an interactive menu allowing you to continuously encrypt strings and decrypt Base64 ciphertexts.