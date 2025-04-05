# Audio Capture Script for PhantomQuery

This script captures audio from a virtual audio device and sends it to the Java backend via WebSocket for real-time speech-to-text processing.

## Prerequisites

1. Python 3.6 or higher
2. A virtual audio device (e.g., VB-Cable for Windows, Loopback/BlackHole for macOS, PulseAudio for Linux)
3. Java backend running with WebSocket support

## Installation

1. Install the required Python packages:
   ```bash
   pip install -r requirements.txt
   ```

2. On Windows, you may need to install PyAudio using a wheel file:
   ```bash
   # For Python 3.9 64-bit
   pip install pipwin
   pipwin install pyaudio
   ```

## Usage

1. Start the Java backend application.

2. Run the audio capture script:
   ```bash
   python audio_capture.py [options]
   ```

   Options:
   - `--device DEVICE_ID`: Audio device ID (use -1 for default device)
   - `--sample-rate SAMPLE_RATE`: Sample rate (default: 16000)
   - `--chunk-size CHUNK_SIZE`: Chunk size (default: 1024)
   - `--websocket-url WEBSOCKET_URL`: WebSocket URL (default: ws://localhost:8080/audio-stream/websocket)

   Example:
   ```bash
   python audio_capture.py --device 1 --sample-rate 16000 --chunk-size 1024
   ```

3. The script will display available audio devices and start capturing audio from the specified device.

4. Press Ctrl+C to stop the audio capture.

## Troubleshooting

1. **No audio devices found**:
   - Make sure your virtual audio device is properly installed and recognized by your operating system.
   - Check if the device appears in your system's audio settings.

2. **WebSocket connection issues**:
   - Ensure the Java backend is running and accessible at the specified WebSocket URL.
   - Check if there are any firewall rules blocking the WebSocket connection.

3. **Audio quality issues**:
   - Adjust the sample rate and chunk size parameters to optimize audio quality and latency.
   - Make sure your virtual audio device is configured correctly.

4. **PyAudio installation issues**:
   - On Windows, use pipwin to install PyAudio.
   - On Linux, install portaudio19-dev package: `sudo apt-get install portaudio19-dev`
   - On macOS, install portaudio using Homebrew: `brew install portaudio`

## Contributing

Feel free to submit issues and enhancement requests! 