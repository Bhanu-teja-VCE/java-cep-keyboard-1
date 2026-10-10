@echo off
title SwitchType — Assistive Single-Switch Virtual Keyboard
color 0B
cls

echo ================================================================================
echo        SwitchType: Assistive Single-Switch Virtual Keyboard (Java CEP)
echo ================================================================================
echo.
echo   [ QUICK USER MANUAL - HOW TO USE THE KEYBOARD ]
echo   ----------------------------------------------------------------------------
echo   1. THE SWITCH KEY   : Press the SPACEBAR (or Enter). No mouse needed!
echo.
echo   2. HOW TO TYPE      : (Row-Column Scan Mode)
echo      * Step 1: Watch the blue highlight scan down the rows.
echo                Press SPACEBAR when your target ROW is highlighted.
echo      * Step 2: The highlight will now scan individual keys in that row.
echo                Press SPACEBAR when your target LETTER is highlighted.
echo      * The letter will be typed and scanning resets for the next character.
echo.
echo   3. WORD PREDICTION  : 
echo      * The top 4 blue boxes show smart word suggestions.
echo      * Select a suggested word to complete the whole word with 1 press!
echo.
echo   4. CORRECTIONS      : Use 'delete' (backspace) or 'undo word'.
echo.
echo   5. CALIBRATION      : Click Tab 2 ('Calibrate') to run 5 quick reaction
echo                         trials and find your optimal typing speed.
echo   ----------------------------------------------------------------------------
echo.
echo ================================================================================
echo   Detecting Java Environment and Starting SwitchType...
echo ================================================================================

:: Resolve working directories
set "ROOT_DIR=%~dp0"
set "APP_DIR=%ROOT_DIR%CEP_Java_SwitchType\app"
set "JAR_PATH=%APP_DIR%\target\SwitchType.jar"

:: Fallback if run directly inside CEP_Java_SwitchType or app
if not exist "%JAR_PATH%" (
    if exist "%ROOT_DIR%app\target\SwitchType.jar" (
        set "APP_DIR=%ROOT_DIR%app"
        set "JAR_PATH=%ROOT_DIR%app\target\SwitchType.jar"
    )
    if exist "%ROOT_DIR%target\SwitchType.jar" (
        set "APP_DIR=%ROOT_DIR%"
        set "JAR_PATH=%ROOT_DIR%target\SwitchType.jar"
    )
)

:: Find java.exe
set "JAVA_BIN=java"

where java >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
        goto :FOUND_JAVA
    )
    if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" (
        set "JAVA_BIN=C:\Program Files\Android\Android Studio\jbr\bin\java.exe"
        goto :FOUND_JAVA
    )
    for /d %%D in ("C:\Program Files\Java\jdk*" "C:\Program Files\Java\jre*" "C:\Program Files\Eclipse Adoptium\jdk*") do (
        if exist "%%D\bin\java.exe" (
            set "JAVA_BIN=%%D\bin\java.exe"
            goto :FOUND_JAVA
        )
    )
    for /d %%D in ("C:\Program Files\JetBrains\IntelliJ IDEA*\jbr") do (
        if exist "%%D\bin\java.exe" (
            set "JAVA_BIN=%%D\bin\java.exe"
            goto :FOUND_JAVA
        )
    )
    echo [ERROR] Java runtime was not found!
    echo Please install Java or set JAVA_HOME in your environment variables.
    pause
    exit /b 1
)

:FOUND_JAVA
if not exist "%JAR_PATH%" (
    echo [WARNING] SwitchType.jar not found at %JAR_PATH%!
    echo Building SwitchType using bundled Maven...
    cd /d "%APP_DIR%"
    call "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" package -DskipTests
    if not exist "%JAR_PATH%" (
        echo [ERROR] Build failed. Please check your project setup.
        pause
        exit /b 1
    )
)

echo [OK] Java detected: "%JAVA_BIN%"
echo [OK] Launching SwitchType from: "%APP_DIR%"
echo.
echo Launching Application... A Quick Manual dialog will open directly over the app!
echo.

cd /d "%APP_DIR%"
start "" "%JAVA_BIN%" -jar "%JAR_PATH%"

echo ================================================================================
echo   SwitchType is now running! You can minimize this window.
echo ================================================================================
ping 127.0.0.1 -n 3 >nul
exit /b 0
