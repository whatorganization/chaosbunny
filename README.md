# ChaosBunny Cipher

## Overview

ChaosBunny is a custom stream cipher built upon the principles of Chaos Theory to demonstrate the essential concepts of encryption and decryption. Instead of relying on traditional permutation or substitution networks, this algorithm utilizes the mathematical logistic map equation to generate a highly unpredictable pseudorandom keystream. This keystream is then combined with the plaintext using a symmetric XOR operation.

This project is an educational demonstration. It shows how a deterministic algorithm can produce a byte stream that looks irregular, but it should not be used to protect real secrets. The implementation does not provide the authentication, nonce handling, key-stretching, or peer review expected from a production cryptographic library.

The primary security mechanism relies on the **Butterfly Effect** (extreme sensitivity to initial conditions). A microscopic change in the cryptographic key completely alters the trajectory of the chaotic sequence, resulting in a totally different keystream and ensuring the ciphertext remains undecipherable.

At a high level, every operation follows the same sequence:

1. Convert the text key to UTF-8 bytes.
2. Hash those bytes with SHA-256.
3. Derive the logistic map's starting state (`x0`) and control value (`r`) from the hash.
4. Advance the logistic map once for each input byte.
5. Convert each floating-point state into a value from 0 to 255.
6. XOR the input byte with that value.
7. Base64-encode the resulting bytes for display during encryption.

Decryption reverses the representation steps and repeats the same deterministic keystream generation. Since XOR is reversible, the same `processData` method can perform both directions.

## Mathematical Foundation: The Logistic Map

The core engine of the cipher is the logistic map, a polynomial mapping of degree 2 that exhibits chaotic dynamic behavior.

The equation is defined as:


$$x_{n+1} = r \cdot x_n \cdot (1 - x_n)$$

### Inputs and Outputs

* **$x_n$ (Input State):** A floating-point decimal strictly between $0$ and $1$. It represents the current state of the system.
* **$r$ (Control Parameter):** A constant floating-point decimal. For the system to act unpredictably (deep chaos), $r$ must strictly fall within the range of $3.56995$ to $4.0$.
* **$x_{n+1}$ (Output State):** A new floating-point decimal between $0$ and $1$. This becomes the input ($x_n$) for the next loop iteration.

### Why use this?

If $r$ is set to $3.99$, the equation enters a state of deterministic chaos. The output numbers will bounce wildly between $0$ and $1$ with no repeating pattern. However, because it is an equation, it is entirely deterministic: if you start with the exact same $x_0$ and $r$, you will generate the exact same chaotic sequence every time. If $x_0$ is changed by even $0.000000001$, the resulting sequence completely diverges after a few iterations.

The word **deterministic** is important. ChaosBunny does not generate a random key stream independently on each run. It generates the same stream whenever the key and input position are the same. This is what allows the decrypting side to recreate the stream without receiving it separately. It also means that reusing the same key causes the same initial keystream bytes to be reused, which is one of the reasons this design is not suitable for real-world encryption.

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

#### How key derivation works

`MessageDigest.getInstance("SHA-256")` creates a SHA-256 hash function. Calling `digest` converts the UTF-8 representation of the key into a fixed 32-byte hash. A one-character key change normally changes every part of that hash, so the selected bytes also change.

The expressions that combine four bytes use bit shifts to build a larger integer:

* `hash[0] & 0xFFL` removes Java's signed-byte behavior and keeps the byte in the range 0 to 255.
* `<< 24`, `<< 16`, and `<< 8` move each byte into its position.
* `|` combines the four positioned values into one number.

The first four bytes are used for `x0`, and the next four are used for `r`. The multiplication and addition map those values into the intended starting ranges. The method returns a two-element array, so `params[0]` is `x0` and `params[1]` is `r`.

This derivation is deterministic: the same key always produces the same parameters on the same Java implementation. No key is stored, and no random value is transmitted to the decrypting side.

### 2. XOR Substitution and Keystream Quantization (Encryption/Decryption)

Like most hardware-level stream ciphers, the actual encryption is performed using a bitwise XOR ($\oplus$) operation between the plaintext byte and the generated chaotic keystream byte.

Because the logistic map outputs a 64-bit floating-point decimal between 0 and 1, the result must be quantized into an 8-bit integer (a byte value from 0 to 255) so it can be combined with standard ASCII/UTF-8 characters. This is achieved by multiplying the float by a large scalar ($10^8$) and applying a modulo 256 operation.

Because XOR is its own mathematical inverse, the exact same method is used for both encryption and decryption.

```java
private static byte[] processData(byte[] data, String key) throws Exception {
    double[] params = deriveKeyParameters(key);
    double x = params[0]; // x0
    double r = params[1]; // control parameter

    byte[] result = new byte[data.length];

    for (int i = 0; i < data.length; i++) {
        // 1. Iterate the chaotic logistic map equation
        x = r * x * (1.0 - x); 
        
        // 2. Quantize the floating-point result into an 8-bit integer (0-255)
        int keystreamByte = (int) ((x * 100000000) % 256); 
        
        // 3. Apply XOR substitution
        result[i] = (byte) (data[i] ^ keystreamByte);
    }
    return result;
}

```

#### How `processData` works

`processData` accepts already-encoded bytes rather than a Java `String`. That makes the method usable for both encryption and decryption:

* During encryption, `data` contains the UTF-8 plaintext bytes.
* During decryption, `data` contains the bytes obtained by Base64-decoding the ciphertext.

The loop maintains one logistic-map state in `x`. Each iteration first computes the next state, then turns it into one keystream byte. The input byte and keystream byte are XORed and written to the same position in `result`.

The XOR operation has this property:

$$A \oplus K \oplus K = A$$

If encryption computes `ciphertext = plaintext ^ keystream`, then decryption computes `plaintext = ciphertext ^ keystream`. The keystream must be generated in exactly the same order, which is why both sides start with the same key-derived `x0` and `r` and advance once per byte.

The expression `(x * 100000000) % 256` is the project's quantization step. It discards most of the floating-point detail and keeps a value that can be used as an 8-bit XOR mask. The cast to `int` removes the fractional part before the XOR operation.

The result is stored in a Java `byte`. Java bytes are signed and range from -128 to 127, but their underlying eight bits still represent all 256 possible byte patterns. Base64 operates on those raw bits, not on their signed decimal display.

### 3. Public Encryption and Decryption Methods

The public methods connect text and raw bytes to the shared processing method:

```java
public static String encrypt(String plaintext, String key) throws Exception {
    byte[] encryptedBytes = processData(plaintext.getBytes(StandardCharsets.UTF_8), key);
    return Base64.getEncoder().encodeToString(encryptedBytes);
}

public static String decrypt(String ciphertextBase64, String key) throws Exception {
    byte[] ciphertext = Base64.getDecoder().decode(ciphertextBase64);
    byte[] decryptedBytes = processData(ciphertext, key);
    return new String(decryptedBytes, StandardCharsets.UTF_8);
}
```

Encryption performs three transformations:

1. `getBytes(StandardCharsets.UTF_8)` converts the input text into bytes. This is important because Java characters and encrypted data are not limited to ASCII.
2. `processData` XORs each plaintext byte with the generated keystream.
3. `Base64.getEncoder().encodeToString` converts arbitrary binary bytes into printable text.

Decryption performs the inverse order:

1. `Base64.getDecoder().decode` restores the encrypted bytes.
2. `processData` applies the same keystream and reverses the XOR.
3. `new String(..., StandardCharsets.UTF_8)` converts the recovered plaintext bytes back into text.

The secret key is never part of the Base64 ciphertext. Both sides must already know the same key.

## Why Base64 Encoding?

When standard letters (like the letter `A`, which is byte value 65) are XORed against random chaotic bytes, the result is almost always a non-printable control character (like byte value 7, which tells a terminal to make a "bell" sound).

If you try to print raw encrypted bytes directly to the screen, the terminal cannot render them, resulting in broken symbols like `G%ѫ`.

**Base64 solves this.** It takes the raw, unprintable ciphertext bytes (the exact values outputted by the XOR operation) and translates them into a safe, standardized alphabet consisting only of printable characters: `A-Z`, `a-z`, `0-9`, `+`, and `/`. This allows the chaotic ciphertext to be safely copied, pasted, and transmitted.

Base64 is an encoding, not encryption. It does not add secrecy and it does not change the underlying encrypted bytes. It expands the data by roughly one third because every group of three input bytes becomes four Base64 characters. The `=` characters sometimes seen at the end are padding used when the byte count is not a multiple of three.

## Step-by-Step Walkthrough Example

Let's trace exactly what happens to a single character through the entire code.
Assume the plaintext is the letter **`C`** and the secret key is **`123`**.

The numeric values below are an illustrative walkthrough of the transformations. The code derives the actual values directly from the SHA-256 digest at runtime, so the assumed `x0`, `r`, and intermediate values should not be treated as a hard-coded test vector.

**Step 1: The Input Byte**
The letter `C` is converted to its ASCII/UTF-8 byte value.

* `C` = Decimal **67** (Binary `01000011`)

**Step 2: Deterministic Key Derivation**
The code hashes the password `"123"` using SHA-256. It takes chunks of that hash to generate our chaotic starting points ($x_0$ and $r$).

* Assume the hash logic generates $x_0 = 0.52341$ and $r = 3.98122$.

**Step 3: Chaotic Iteration (Keystream Generation)**
The algorithm feeds $x_0$ into the logistic map to find the first chaotic state ($x_1$).

* $x_1 = 3.98122 \cdot 0.52341 \cdot (1 - 0.52341)$
* $x_1 = 0.99314...$

**Step 4: Quantization (Float to Byte)**
The code cannot XOR a decimal float against a byte. It must scale $x_1$ into an 8-bit integer (0-255). It multiplies the float by $10^8$ and applies modulo 256.

* $(0.99314 \times 100,000,000) \pmod{256} = 173$
* Our keystream byte is **173** (Binary `10101101`).

**Step 5: XOR Encryption**
The algorithm applies bitwise XOR ($\oplus$) between the plaintext byte (67) and the keystream byte (173).

* `01000011` (67)
* `10101101` (173)
* **Result:** `11101110` = Decimal **238**
The ciphertext byte is `238`.

**Step 6: Base64 Encoding (Output)**
Byte `238` is an extended ASCII character that might not print correctly in all terminals. The algorithm passes the byte array `[238]` to the Base64 encoder.

* Base64 converts `[238]` into the string `"7g=="`.
* The user sees: **Encrypted (Base64): 7g==**

**Step 7: Decryption**

1. The user inputs `"7g=="` and the password `"123"`.
2. Base64 decodes `"7g=="` back to the raw ciphertext byte `[238]`.
3. The algorithm hashes `"123"` to perfectly regenerate $x_0 = 0.52341$ and $r = 3.98122$.
4. The logistic map runs, producing the exact same keystream byte: **173**.
5. It XORs the ciphertext against the keystream: `238` $\oplus$ `173` = **67**.
6. Byte 67 is converted back to text, perfectly restoring the letter **`C`**.

## Usage

Compile and run the Java working example via the terminal:

```bash
javac ChaosBunny.java
java ChaosBunny

```

The program will launch an interactive menu allowing you to continuously encrypt strings and decrypt Base64 ciphertexts.

### Interactive flow

When the program starts, `main` creates a `Scanner` connected to standard input and repeatedly displays three choices:

* **1. Encrypt text** reads plaintext and a secret key, then prints a Base64 ciphertext.
* **2. Decrypt text** reads a Base64 ciphertext and the same secret key, then prints the recovered text.
* **3. Exit** ends the loop and closes the scanner.

Invalid Base64 input is caught as an `IllegalArgumentException`. Other processing failures are caught by the general exception handler so the menu can continue instead of terminating immediately.