# Java dexlib2 build

This folder builds the Java dex2cxx jar. It uses dexlib2 to read
APK/DEX files, applies the same whitelist/blacklist filter style from
`filter.txt`, patches selected smali methods to native wrappers, builds the
JNI project with Android NDK, rebuilds the APK with apktool, then aligns and
signs the result.

## Build

```powershell
powershell -ExecutionPolicy Bypass -File java\build.ps1
```

Termux/Linux:

```sh
sh java/build.sh
```

Output:

```text
dex2cxx.jar
```

## Run
Use `--list-only` to print matching methods to the console without rebuilding.
