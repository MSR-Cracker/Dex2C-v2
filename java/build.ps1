param(
    [string]$JavaHome = "D:\software\Android Studio\jbr"
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$Lib = Join-Path $PSScriptRoot "lib"
$Build = Join-Path $PSScriptRoot "build"
$Classes = Join-Path $Build "classes"
$Dist = Join-Path $PSScriptRoot "dist"

New-Item -ItemType Directory -Force -Path $Lib, $Classes, $Dist | Out-Null

$Deps = @(
    "https://repo1.maven.org/maven2/org/smali/dexlib2/2.5.2/dexlib2-2.5.2.jar",
    "https://repo1.maven.org/maven2/org/smali/baksmali/2.5.2/baksmali-2.5.2.jar",
    "https://repo1.maven.org/maven2/org/smali/smali/2.5.2/smali-2.5.2.jar",
    "https://repo1.maven.org/maven2/org/smali/util/2.5.2/util-2.5.2.jar",
    "https://repo1.maven.org/maven2/com/google/guava/guava/27.1-android/guava-27.1-android.jar",
    "https://repo1.maven.org/maven2/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar",
    "https://repo1.maven.org/maven2/com/beust/jcommander/1.64/jcommander-1.64.jar",
    "https://repo1.maven.org/maven2/org/antlr/antlr/3.5.2/antlr-3.5.2.jar",
    "https://repo1.maven.org/maven2/org/antlr/antlr-runtime/3.5.2/antlr-runtime-3.5.2.jar",
    "https://repo1.maven.org/maven2/org/antlr/stringtemplate/3.2.1/stringtemplate-3.2.1.jar",
    "https://repo1.maven.org/maven2/io/github/reandroid/ARSCLib/1.3.8/ARSCLib-1.3.8.jar"
)

foreach ($Url in $Deps) {
    $Out = Join-Path $Lib ([IO.Path]::GetFileName($Url))
    if (!(Test-Path $Out)) {
        Write-Host "Downloading $Url"
        Invoke-WebRequest -Uri $Url -OutFile $Out -UseBasicParsing
    }
}

$Javac = Join-Path $JavaHome "bin\javac.exe"
$Jar = Join-Path $JavaHome "bin\jar.exe"
if (!(Test-Path $Javac)) { $Javac = "javac" }
if (!(Test-Path $Jar)) { $Jar = "jar" }

$Classpath = (Get-ChildItem $Lib -Filter *.jar | ForEach-Object FullName) -join ";"
$Sources = Get-ChildItem (Join-Path $PSScriptRoot "src") -Recurse -Filter *.java | ForEach-Object FullName

Remove-Item -Recurse -Force $Classes -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $Classes | Out-Null

& $Javac --release 17 -encoding UTF-8 -cp $Classpath -d $Classes $Sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

$Manifest = Join-Path $Build "MANIFEST.MF"
$LibNames = (Get-ChildItem $Lib -Filter *.jar | ForEach-Object { "../lib/$($_.Name)" }) -join " "
Set-Content -Encoding ASCII -Path $Manifest -Value @(
    "Manifest-Version: 1.0"
    "Main-Class: com.dex2c.cli.Main"
    "Class-Path: $LibNames"
    ""
)

$RootManifest = Join-Path $Build "MANIFEST.root.MF"
Set-Content -Encoding ASCII -Path $RootManifest -Value @(
    "Manifest-Version: 1.0"
    "Main-Class: com.dex2c.cli.Main"
    ""
)

$RootJarOut = Join-Path $Root "dex2cxx.jar"
$Fat = Join-Path $Build "fat"
Remove-Item -Recurse -Force $Fat -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $Fat | Out-Null

foreach ($Dep in Get-ChildItem $Lib -Filter *.jar) {
    Push-Location $Fat
    & $Jar xf $Dep.FullName
    if ($LASTEXITCODE -ne 0) { throw "dependency extract failed: $($Dep.Name)" }
    Pop-Location
}

Remove-Item -Recurse -Force (Join-Path $Fat "META-INF") -ErrorAction SilentlyContinue
Copy-Item -Recurse -Force (Join-Path $Classes "*") $Fat

$EmbeddedTools = Join-Path $Fat "dex2c-tools"
New-Item -ItemType Directory -Force -Path $EmbeddedTools | Out-Null

$ApksignerPath = Join-Path $Root "tools\apksigner.jar"
if (!(Test-Path $ApksignerPath)) {
    $SdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "D:\software\Sdk" }
    $ApksignerPath = Get-ChildItem (Join-Path $SdkRoot "build-tools") -Recurse -Filter apksigner.jar |
            Sort-Object FullName -Descending |
            Select-Object -First 1 -ExpandProperty FullName
}
if (!(Test-Path $ApksignerPath)) {
    throw "Missing apksigner.jar. Install Android SDK build-tools or place tools\apksigner.jar."
}
Copy-Item -LiteralPath $ApksignerPath -Destination (Join-Path $EmbeddedTools "apksigner.jar") -Force

& $Jar cfm $RootJarOut $RootManifest -C $Fat .
if ($LASTEXITCODE -ne 0) { throw "root jar failed" }

Remove-Item -Recurse -Force $Build, $Lib -ErrorAction SilentlyContinue

Write-Host "Built $RootJarOut"
