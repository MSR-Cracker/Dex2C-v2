# Dex2cxx

Dex2cxx is a DEX-to-native protection tool for Android applications. The project processes selected Android DEX methods and generates native JNI-backed implementations using the Android NDK. It supports class-based filtering, native string handling, APK rebuilding, alignment, and signing.

> **Project note:** This project is based on an existing open-source Dex2cxx project. It has been independently developed further with additional features, improvements, fixes, and modifications. It is not presented as an original project created entirely from scratch.

---

## Author & Development

**MSR Coder / 0Dex**

This version is a continued development of the original Dex2cxx project. The original project provided the foundation, while this version contains additional development and modifications, including improvements to the processing pipeline, native string handling, filtering, APK rebuilding, and build compatibility.

Original project and its original author(s) remain credited through the project's history and source files.

---

## Features

- Android APK processing
- DEX method scanning
- Class-based method filtering
- JNI native wrapper generation
- Native string handling for protected methods
- Multiple DEX support
- Multiple Android ABIs
- Android NDK compilation
- APK rebuilding
- ZIP alignment
- APK signing
- Windows support
- Termux support
- Method report generation
- Automatic NDK configuration

---

## How It Works

Dex2cxx scans the DEX files inside an APK and uses `filter.txt` to determine which classes should be processed. For selected methods, the tool:

1. Extracts the DEX files from the APK.
2. Converts the DEX files into Smali.
3. Finds methods belonging to the selected classes.
4. Creates native JNI entry points.
5. Moves supported string constants from protected methods into native code.
6. Builds the native library using the Android NDK.
7. Rebuilds the APK with the modified DEX files and native libraries.
8. Aligns the APK.
9. Signs the resulting APK.

The current implementation focuses on selected methods/classes rather than converting an entire DEX file into C++.

---

## Requirements

### Java
Java 17 or newer is recommended.

#### Termux
```bash
pkg update
pkg install openjdk-17
```

### Git
Git is optional:
```bash
pkg install git
```

### Storage Permission
If the APK is stored outside the Termux home directory:
```bash
termux-setup-storage
```

### Android NDK
An Android NDK installation is required for native compilation.
The project can automatically configure/download the required NDK when using:
```bash
java -jar dex2cxx.jar --auto
```

---

## Usage

### Using Dex2cxx with Termux

Enter the project directory:
```bash
cd ~/Dex2cxx
```

Then run Dex2cxx using the filter file:
```bash
java -jar dex2cxx.jar \
  -a ./app.apk \
  -o ./app_protected.apk \
  --filter ./filter.txt \
  --report ./methods-report.txt
```

### `filter.txt`

The `filter.txt` file specifies which classes should be processed.

Example:
```text
com.test1.MainActivity
com.test1.LoginActivity
com.test1.SettingsActivity
```

Each class should be placed on a separate line.

Comments can be added using `#`:
```text
# Main application class
com.test1.MainActivity

# Login class
com.test1.LoginActivity

# Settings class
com.test1.SettingsActivity
```

Multiple classes can be added to the same filter file. Dex2cxx will process the matching supported methods belonging to the selected classes.

---

## Key Features Breakdown

### Native Strings

Protected methods containing supported `const-string` instructions can have their string values moved into the native library.

For example:
```smali
const-string v0, "Welcome Shadow"
```

can be transformed into a native string lookup:
```smali
const v1, 0
invoke-static {v1}, Lcom/dex2c/NativeStrings;->get(I)Ljava/String;
move-result-object v0
```

The original string value is then stored inside the generated native library. This applies to supported string constants found inside methods that are processed by Dex2cxx.

### Multiple DEX Files

Dex2cxx supports APKs containing multiple DEX files, including:
```text
classes.dex
classes2.dex
classes3.dex
classes4.dex
...
```

The DEX files are processed independently and rebuilt while preserving their corresponding DEX names.

### Method Report

A method report can be generated using:
```bash
java -jar dex2cxx.jar \
  -a ./app.apk \
  --filter ./filter.txt \
  --report ./methods-report.txt
```

Example report entries:
```text
classes2.dex 1 Lcom/test1/MainActivity;->initialize()V
classes2.dex 4 Lcom/test1/MainActivity;->onCreate(Landroid/os/Bundle;)V
```

The report can be used to inspect the methods matched by the selected filters.

---

## Command Reference

### Windows

Protect an APK:
```cmd
java -jar dex2cxx.jar -a .\app.apk -o .\app_protected.apk --filter .\filter.txt --report .\methods-report.txt
```

Auto setup:
```cmd
java -jar dex2cxx.jar --auto
```

### Termux

Protect an APK:
```bash
java -jar dex2cxx.jar -a ./app.apk -o ./app_protected.apk --filter ./filter.txt --report ./methods-report.txt
```

Auto setup:
```bash
java -jar dex2cxx.jar --auto
```

---

## Configuration

The main configuration file is `dxx.cfg`. It contains paths for the required tools and APK signing configuration.

Example:
```json
{
  "apktool": "tools/apktool.jar",
  "ndk_dir": "android-ndk",
  "signature": {
    "keystore_path": "keystore/debug.keystore",
    "alias": "androiddebugkey",
    "keystore_pass": "android",
    "store_pass": "android",
    "v1_enabled": true,
    "v2_enabled": true,
    "v3_enabled": true
  }
}
```

---

## Project Structure

```text
Dex2cxx/
├── java/
│   ├── src/
│   │   └── com/dex2c/
│   │       ├── cli/
│   │       ├── dex/
│   │       ├── filter/
│   │       ├── model/
│   │       └── pipeline/
│   │           └── ...
│   ├── project/
│   └── jni/
│       ├── Android.mk
│       ├── Application.mk
│       └── nc/
│   ├── filter.txt
├── dxx.cfg
└── README.md
```

---

## Android NDK & Architecture

The native part of Dex2cxx is compiled using the Android NDK.

The native library generated by the build is `librevdex.so`.

Supported ABIs include:
- `armeabi-v7a`
- `arm64-v8a`
- `x86`
- `x86_64`

The native build uses the project's Android NDK configuration and generates the required native libraries before the APK is rebuilt.

---

## APK Processing Pipeline

```text
APK
 │
 ├── DEX extraction
 ├── Smali disassembly
 ├── Filter matching
 ├── Method processing
 ├── Native JNI generation
 ├── Native string generation
 ├── Android NDK compilation
 ├── DEX reassembly
 ├── APK rebuilding
 ├── ZIP alignment
 └── APK signing
 │
 ▼
Protected APK
```

---

## GitHub Actions

Dex2cxx can also be integrated into a GitHub Actions workflow:

1. Set up Java.
2. Configure the Android NDK.
3. Build Dex2cxx.
4. Process an APK using `filter.txt`.
5. Generate the protected APK.
6. Upload the resulting APK as a workflow artifact.

---

## Contributing

Contributions and improvements are welcome. To contribute:

1. Fork the repository.
2. Create a new branch.
3. Make your changes.
4. Test the changes.
5. Submit a pull request with a clear description.

---

## Support

If you encounter an issue, open a GitHub issue and provide:
- Operating system
- Java version
- NDK version
- Command used
- Error output
- Relevant build logs
- Whether the problem occurs during DEX processing, native compilation, APK rebuilding, or signing

Providing the complete error log makes troubleshooting much easier.

---

## Credits & Attribution

This project is a continued development based on the original Dex2cxx project.

The original project was used as the foundation for this version, and further development was performed on top of that codebase. Additional development includes modifications and improvements to the processing pipeline, filtering, native string handling, native wrapper generation, APK rebuilding, and build configuration.

**Current development and maintenance:**
- **MSR Coder / 0Dex**

Original authorship and historical credits remain preserved in the repository where applicable.

---

## License

Please refer to the repository's license file for the applicable license and usage conditions.
