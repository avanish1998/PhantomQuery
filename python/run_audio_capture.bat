@echo off
echo PhantomQuery - Audio Capture
echo ===================================
echo.

REM Check if Python is installed
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo Python is not installed or not in PATH.
    echo Please install Python from https://www.python.org/downloads/
    echo Make sure to check "Add Python to PATH" during installation.
    pause
    exit /b 1
)

REM Check if required packages are installed
echo Checking required packages...
python -c "import pyaudio" >nul 2>&1
if %errorlevel% neq 0 (
    echo Installing required packages...
    pip install pyaudio websocket-client numpy
    if %errorlevel% neq 0 (
        echo Failed to install required packages.
        echo Please run: pip install pyaudio websocket-client numpy
        pause
        exit /b 1
    )
)

REM Check if device_config.txt exists
if not exist device_config.txt (
    echo No device configuration found.
    echo Running device selection tool...
    echo.
    echo IMPORTANT: You need to select TWO devices:
    echo 1. A CABLE Output device (INPUT) to capture audio
    echo 2. A CABLE Input device (OUTPUT) to route audio to
    echo.
    echo First, select the INPUT device (CABLE Output).
    echo Then, run this tool again to select the OUTPUT device (CABLE Input).
    echo.
    pause
    python select_audio_device.py
    if %errorlevel% neq 0 (
        echo Failed to run device selection tool.
        pause
        exit /b 1
    )
)

REM Check if the configuration is for an input device
python -c "import os; config_file = os.path.join(os.path.dirname(os.path.abspath('device_config.txt')), 'device_config.txt'); f = open(config_file, 'r'); config = dict(line.strip().split('=', 1) for line in f if '=' in line); f.close(); exit(0 if config.get('device_type') == 'input' else 1)" >nul 2>&1
if %errorlevel% neq 0 (
    echo The saved configuration is not for an input device.
    echo Please run the device selection tool and select an INPUT device (CABLE Output).
    echo.
    pause
    python select_audio_device.py
    if %errorlevel% neq 0 (
        echo Failed to run device selection tool.
        pause
        exit /b 1
    )
)

REM Run the audio capture script
echo.
echo Starting audio capture...
python audio_capture.py

pause 