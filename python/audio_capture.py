#!/usr/bin/env python3
"""
Audio Capture Script for PhantomQuery

This script captures audio from a specified input device and streams it to the PhantomQuery
backend server via WebSocket connection. It handles real-time audio capture, speech detection,
and streaming of audio data.

Requirements:
- Python 3.6+
- pyaudio
- websocket-client
- numpy

Usage:
    python audio_capture.py [--device DEVICE_ID] [--sample-rate SAMPLE_RATE] [--chunk-size CHUNK_SIZE] [--ws-url WS_URL]
"""

import os
import sys
import json
import time
import wave
import pyaudio
import numpy as np
import websocket
import threading
import argparse
import logging
from datetime import datetime
import uuid
import base64

# Configure logging
logging.basicConfig(
    level=logging.DEBUG,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Constants
DEFAULT_SAMPLE_RATE = 16000
DEFAULT_CHUNK_SIZE = 2048
DEFAULT_WS_URL = "ws://localhost:8080/simple-websocket"
CONFIG_FILE = "device_config.txt"

# Global variables
device_id = None
client_id = None
ws = None
is_connected = False
reconnect_attempts = 0
MAX_RECONNECT_ATTEMPTS = 5
RECONNECT_DELAY = 5  # seconds
last_reconnect_time = 0
MIN_RECONNECT_INTERVAL = 10  # minimum seconds between reconnection attempts

# Audio parameters
CHUNK = 1024  # Reduced from 4096 to 1024 for smaller chunks
FORMAT = pyaudio.paInt16
CHANNELS = 1
RATE = 16000  # 16kHz is optimal for Google Cloud Speech-to-Text
SILENCE_THRESHOLD = 100
SILENCE_DURATION = 1.0  # seconds of silence to consider speech ended
MAX_SEGMENT_SIZE = 8192  # Maximum size of audio segment to send (8KB)
MAX_SPEECH_DURATION = 5.0  # Maximum duration of speech to process (seconds)

def load_device_config():
    """Load device configuration from file."""
    try:
        config_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), CONFIG_FILE)
        if not os.path.exists(config_path):
            logger.error(f"Configuration file not found: {config_path}")
            return None
            
        with open(config_path, "r") as f:
            config = {}
            for line in f:
                if "=" in line:
                    key, value = line.strip().split("=", 1)
                    config[key] = value
                    
            if "device_id" not in config or "device_type" not in config:
                logger.error("Invalid configuration file format")
                return None
                
            if config["device_type"] != "input":
                logger.error("Configuration is not for an input device")
                return None
                
            return config["device_id"]
    except Exception as e:
        logger.error(f"Error loading configuration: {str(e)}")
        return None

def list_audio_devices():
    """List all available audio devices."""
    p = pyaudio.PyAudio()
    info = p.get_host_api_info_by_index(0)
    numdevices = info.get('deviceCount')
    
    print("\nAvailable Audio Devices:")
    print("------------------------")
    
    input_devices = []
    output_devices = []
    
    for i in range(0, numdevices):
        device_info = p.get_device_info_by_host_api_device_index(0, i)
        name = device_info.get('name')
        max_input_channels = device_info.get('maxInputChannels')
        max_output_channels = device_info.get('maxOutputChannels')
        
        if max_input_channels > 0:
            input_devices.append((i, name))
            print(f"Device {i}: {name} (Input)")
        
        if max_output_channels > 0:
            output_devices.append((i, name))
            print(f"Device {i}: {name} (Output)")
    
    p.terminate()
    return input_devices, output_devices

def on_message(ws, message):
    """Handle incoming WebSocket messages."""
    try:
        data = json.loads(message)
        message_type = data.get("type")
        
        if message_type == "transcription":
            text = data.get("text", "")
            print(f"\nTranscription: {text}")
        elif message_type == "error":
            error = data.get("message", "Unknown error")
            logger.error(f"Server error: {error}")
        elif message_type == "started":
            session_id = data.get("sessionId")
            logger.info(f"Stream started with session ID: {session_id}")
        elif message_type == "stopped":
            session_id = data.get("sessionId")
            logger.info(f"Stream stopped for session ID: {session_id}")
    except Exception as e:
        logger.error(f"Error processing message: {str(e)}")

def on_error(ws, error):
    """Handle WebSocket errors."""
    logger.error(f"WebSocket error: {str(error)}")

def on_close(ws, close_status_code, close_msg):
    """Handle WebSocket connection close."""
    global is_connected, reconnect_attempts, last_reconnect_time
    logger.info(f"WebSocket connection closed - Status: {close_status_code}, Message: {close_msg}")
    is_connected = False
    
    # Only attempt to reconnect if we haven't exceeded max attempts and enough time has passed
    current_time = time.time()
    if reconnect_attempts < MAX_RECONNECT_ATTEMPTS and (current_time - last_reconnect_time) > MIN_RECONNECT_INTERVAL:
        logger.info(f"Attempting to reconnect in {RECONNECT_DELAY} seconds... (Attempt {reconnect_attempts + 1}/{MAX_RECONNECT_ATTEMPTS})")
        time.sleep(RECONNECT_DELAY)
        reconnect_websocket()
    else:
        logger.error("Maximum reconnection attempts reached or reconnecting too frequently. Please restart the application.")

def on_open(ws):
    """Handle WebSocket connection open."""
    global client_id, is_connected, reconnect_attempts, last_reconnect_time
    logger.info("WebSocket connection established")
    is_connected = True
    reconnect_attempts = 0  # Reset reconnect attempts on successful connection
    last_reconnect_time = time.time()  # Update last reconnect time
    
    # Generate client ID
    client_id = str(uuid.uuid4())
    
    # Send a connection message
    connection_message = {
        "clientId": client_id,
        "type": "connection",
        "message": "Python audio capture connected"
    }
    ws.send(json.dumps(connection_message))
    print("📤 Connected to WebSocket server")
    
    # Start audio capture in a separate thread
    threading.Thread(target=capture_audio, args=(ws, client_id)).start()

def capture_audio(ws, client_id):
    """Capture audio from the specified device and send it through WebSocket."""
    try:
        p = pyaudio.PyAudio()
        stream = p.open(
            format=pyaudio.paInt16,
            channels=1,
            rate=DEFAULT_SAMPLE_RATE,
            input=True,
            input_device_index=int(device_id),
            frames_per_buffer=DEFAULT_CHUNK_SIZE
        )
        
        logger.info(f"Started capturing audio from device {device_id}")
        print("🎤 Audio capture started - Waiting for speech...")
        
        # For speech detection
        SILENCE_THRESHOLD = 100  # Adjust based on your environment
        SILENCE_DURATION = 1.0  # Seconds of silence to consider speech ended
        MIN_SPEECH_DURATION = 0.5  # Minimum duration to consider as speech
        
        # Speech detection variables
        is_speaking = False
        speech_start_time = 0
        silence_start_time = 0
        audio_buffer = []
        last_print_time = time.time()
        
        while True:
            try:
                # Read audio data
                data = stream.read(DEFAULT_CHUNK_SIZE, exception_on_overflow=False)
                audio_data = np.frombuffer(data, dtype=np.int16)
                
                # Calculate audio level
                audio_level = np.abs(audio_data).mean()
                
                # Print audio level every 5 seconds
                if time.time() - last_print_time >= 5.0:
                    print(f"🎤 Audio Level: {audio_level:.2f}")
                    last_print_time = time.time()
                
                # Speech detection logic
                current_time = time.time()
                
                if audio_level > SILENCE_THRESHOLD:
                    # Speech detected
                    if not is_speaking:
                        # Start of speech
                        is_speaking = True
                        speech_start_time = current_time
                        audio_buffer = [data]  # Start new buffer
                        print("🗣️ Speech detected")
                    else:
                        # Continuing speech
                        audio_buffer.append(data)
                    
                    # Reset silence timer
                    silence_start_time = 0
                else:
                    # Silence detected
                    if is_speaking:
                        if silence_start_time == 0:
                            # Start of silence
                            silence_start_time = current_time
                        
                        # Check if silence has lasted long enough to consider speech ended
                        if current_time - silence_start_time >= SILENCE_DURATION:
                            # End of speech
                            speech_duration = current_time - speech_start_time
                            
                            if speech_duration >= MIN_SPEECH_DURATION:
                                # Process complete speech segment
                                print(f"✅ Speech ended (duration: {speech_duration:.2f}s)")
                                
                                # Combine all audio chunks
                                complete_audio = b''.join(audio_buffer)
                                
                                # Convert to base64
                                audio_base64 = base64.b64encode(complete_audio).decode('utf-8')
                                
                                # Create message with complete speech segment
                                message = {
                                    "clientId": client_id,
                                    "type": "speech",
                                    "audioData": audio_base64,
                                    "duration": speech_duration
                                }
                                
                                # Send the message
                                ws.send(json.dumps(message))
                                print(f"📤 Sent speech segment ({len(audio_buffer)} chunks, {len(complete_audio)} bytes)")
                            else:
                                print(f"❌ Speech too short ({speech_duration:.2f}s), ignoring")
                            
                            # Reset for next speech segment
                            is_speaking = False
                            audio_buffer = []
            
            except Exception as e:
                logger.error(f"Error capturing audio: {str(e)}")
                break
                
    except Exception as e:
        logger.error(f"Error setting up audio capture: {str(e)}")
    finally:
        if 'stream' in locals():
            stream.stop_stream()
            stream.close()
        if 'p' in locals():
            p.terminate()
        logger.info("Audio capture stopped")

def process_audio_chunk(audio_data):
    """Process a single chunk of audio data."""
    try:
        # Convert to base64
        audio_base64 = base64.b64encode(audio_data).decode('utf-8')
        
        # Create message
        message = {
            "clientId": client_id,
            "type": "speech",
            "audioData": audio_base64,
            "timestamp": int(time.time() * 1000)
        }
        
        # Send message
        if ws and ws.sock and ws.sock.connected:
            ws.send(json.dumps(message))
            print("📤 Sent audio chunk")
        else:
            print("❌ WebSocket not connected, attempting to reconnect...")
            connect_websocket()
            
    except Exception as e:
        print(f"❌ Error processing audio chunk: {str(e)}")
        if "connection was forcibly closed" in str(e):
            print("🔄 Connection lost, attempting to reconnect...")
            connect_websocket()

def connect_websocket():
    """Connect to WebSocket server with retry logic."""
    global ws
    max_retries = 3
    retry_count = 0
    
    while retry_count < max_retries:
        try:
            if ws:
                ws.close()
            
            ws = websocket.WebSocketApp(
                "ws://localhost:8080/simple-websocket",
                on_message=on_message,
                on_error=on_error,
                on_close=on_close,
                on_open=on_open
            )
            
            # Run WebSocket connection in a separate thread
            wst = threading.Thread(target=ws.run_forever)
            wst.daemon = True
            wst.start()
            
            # Wait for connection to establish
            time.sleep(1)
            if ws.sock and ws.sock.connected:
                print("✅ WebSocket connected successfully")
                return True
                
        except Exception as e:
            print(f"❌ Connection attempt {retry_count + 1} failed: {str(e)}")
            retry_count += 1
            time.sleep(2)  # Wait before retrying
    
    print("❌ Failed to connect after maximum retries")
    return False

def on_speech_start():
    """Send speech start message."""
    try:
        message = {
            "clientId": client_id,
            "type": "speech_start",
            "timestamp": int(time.time() * 1000)
        }
        if ws and ws.sock and ws.sock.connected:
            ws.send(json.dumps(message))
            print("🗣️ Speech start message sent")
    except Exception as e:
        print(f"❌ Error sending speech start message: {str(e)}")

def on_speech_end(duration):
    """Send speech end message."""
    try:
        message = {
            "clientId": client_id,
            "type": "speech_end",
            "duration": duration,
            "timestamp": int(time.time() * 1000)
        }
        if ws and ws.sock and ws.sock.connected:
            ws.send(json.dumps(message))
            print("✅ Speech end message sent")
    except Exception as e:
        print(f"❌ Error sending speech end message: {str(e)}")

def audio_callback(in_data, frame_count, time_info, status):
    """Callback for audio stream."""
    global is_speaking, speech_start_time, audio_buffer, silence_start_time
    
    try:
        # Convert audio data to numpy array
        audio_data = np.frombuffer(in_data, dtype=np.int16)
        
        # Calculate audio level
        audio_level = np.abs(audio_data).mean()
        print(f"🎤 Audio Level: {audio_level:.2f}")
        
        current_time = time.time()
        
        if audio_level > SILENCE_THRESHOLD:
            # Speech detected
            if not is_speaking:
                # Start of speech
                is_speaking = True
                speech_start_time = current_time
                audio_buffer = [data]  # Start new buffer
                print("🗣️ Speech detected")
                on_speech_start()  # Send speech start message
            else:
                # Continuing speech
                audio_buffer.append(data)
                silence_start_time = None
                
                # Check if speech duration exceeds maximum
                if current_time - speech_start_time > MAX_SPEECH_DURATION:
                    # Force end of speech
                    is_speaking = False
                    speech_duration = current_time - speech_start_time
                    print(f"⚠️ Speech duration exceeded maximum ({MAX_SPEECH_DURATION}s), forcing end")
                    
                    # Send speech end message
                    on_speech_end(speech_duration)
                    
                    # Process complete speech segment
                    process_complete_speech(audio_buffer, speech_duration)
                    
                    # Reset buffers
                    audio_buffer = []
                    silence_start_time = None
        else:
            # Silence detected
            if is_speaking:
                if silence_start_time is None:
                    silence_start_time = current_time
                elif current_time - silence_start_time >= SILENCE_DURATION:
                    # Speech has ended
                    is_speaking = False
                    speech_duration = current_time - speech_start_time
                    print(f"✅ Speech ended (duration: {speech_duration:.2f}s)")
                    
                    # Send speech end message
                    on_speech_end(speech_duration)
                    
                    # Process complete speech segment
                    process_complete_speech(audio_buffer, speech_duration)
                    
                    # Reset buffers
                    audio_buffer = []
                    silence_start_time = None
    except Exception as e:
        print(f"❌ Error in audio callback: {str(e)}")
    
    return (in_data, pyaudio.paContinue)

def process_complete_speech(audio_chunks):
    """Process a complete speech segment and send it to the server."""
    try:
        # Combine audio chunks
        audio_data = b''.join(audio_chunks)
        
        # Check if the audio size is within limits
        if len(audio_data) > MAX_SEGMENT_SIZE:
            print(f"⚠️ Audio too large ({len(audio_data)} bytes), truncating")
            audio_data = audio_data[:MAX_SEGMENT_SIZE]
        
        # Convert to base64
        audio_base64 = base64.b64encode(audio_data).decode('utf-8')
        
        # Send the audio data
        message = {
            "type": "speech",
            "audioData": audio_base64,
            "clientId": client_id,
            "format": {
                "encoding": "LINEAR16",
                "sampleRateHertz": RATE,
                "languageCode": "en-US",
                "audioChannelCount": CHANNELS
            }
        }
        
        # Check if WebSocket is still connected before sending
        if ws and ws.sock and ws.sock.connected and is_connected:
            ws.send(json.dumps(message))
            print(f"📤 Sent speech segment ({len(audio_chunks)} chunks, {len(audio_data)} bytes)")
        else:
            print("❌ WebSocket not connected, attempting to reconnect...")
            # Attempt to reconnect
            if reconnect_websocket():
                # Retry sending the message after reconnection
                time.sleep(2)  # Wait for reconnection
                if ws and ws.sock and ws.sock.connected and is_connected:
                    ws.send(json.dumps(message))
                    print(f"📤 Sent speech segment after reconnection ({len(audio_chunks)} chunks, {len(audio_data)} bytes)")
                else:
                    print("❌ Failed to send speech segment after reconnection attempt")
            else:
                print("❌ Failed to reconnect, speech segment not sent")
        
    except Exception as e:
        print(f"❌ Error processing speech: {str(e)}")
        # Only attempt to reconnect if we haven't exceeded max attempts
        if reconnect_attempts < MAX_RECONNECT_ATTEMPTS:
            reconnect_websocket()

def reconnect_websocket():
    """Reconnect the WebSocket connection."""
    global ws, reconnect_attempts, last_reconnect_time
    
    try:
        # Check if we're already connected
        if ws and ws.sock and ws.sock.connected and is_connected:
            logger.info("WebSocket already connected, skipping reconnection")
            return True
            
        reconnect_attempts += 1
        last_reconnect_time = time.time()
        
        # Close existing connection if any
        if ws and ws.sock:
            ws.close()
        
        logger.info("Attempting to reconnect to WebSocket server...")
        
        # Create a new WebSocket connection
        ws = websocket.WebSocketApp(
            DEFAULT_WS_URL,
            on_message=on_message,
            on_error=on_error,
            on_close=on_close,
            on_open=on_open
        )
        
        # Run WebSocket connection in a separate thread
        ws_thread = threading.Thread(target=ws.run_forever)
        ws_thread.daemon = True
        ws_thread.start()
        
        # Wait for connection to establish
        time.sleep(2)
        
        if ws and ws.sock and ws.sock.connected:
            logger.info("WebSocket reconnection successful")
            return True
        else:
            logger.error("WebSocket reconnection failed")
            return False
            
    except Exception as e:
        logger.error(f"Error during WebSocket reconnection: {str(e)}")
        return False

def main():
    global device_id, client_id, ws, is_connected, last_reconnect_time
    
    parser = argparse.ArgumentParser(description="Audio Capture for PhantomQuery")
    parser.add_argument("--device", type=int, help="Input device ID")
    parser.add_argument("--sample-rate", type=int, default=DEFAULT_SAMPLE_RATE, help="Sample rate")
    parser.add_argument("--chunk-size", type=int, default=DEFAULT_CHUNK_SIZE, help="Chunk size")
    parser.add_argument("--ws-url", type=str, default=DEFAULT_WS_URL, help="WebSocket URL")
    parser.add_argument("--list-devices", action="store_true", help="List available audio devices")
    args = parser.parse_args()
    
    if args.list_devices:
        list_audio_devices()
        return
        
    # Load device ID from configuration if not specified
    if args.device is None:
        device_id = load_device_config()
        if device_id is None:
            print("Please run select_audio_device.py first and select an INPUT device, or specify a device with --device")
            return
    else:
        device_id = args.device
        
    # Enable WebSocket debug logging
    websocket.enableTrace(False)
    
    # Connect to WebSocket server
    ws = websocket.WebSocketApp(
        args.ws_url,
        on_message=on_message,
        on_error=on_error,
        on_close=on_close,
        on_open=on_open
    )
    
    # Run WebSocket connection in a separate thread
    ws_thread = threading.Thread(target=ws.run_forever)
    ws_thread.daemon = True
    ws_thread.start()
    
    try:
        while True:
            # Only check connection status if we're not already connected and enough time has passed
            current_time = time.time()
            if not is_connected and reconnect_attempts < MAX_RECONNECT_ATTEMPTS and (current_time - last_reconnect_time) > MIN_RECONNECT_INTERVAL:
                logger.warning("WebSocket connection lost, attempting to reconnect...")
                reconnect_websocket()
            
            time.sleep(5)  # Check connection status every 5 seconds
    except KeyboardInterrupt:
        logger.info("Stopping audio capture...")
        if client_id:
            # Send stop-stream message
            stop_message = {
                "clientId": client_id
            }
            if ws and ws.sock and ws.sock.connected and is_connected:
                ws.send(json.dumps(stop_message))
        if ws:
            ws.close()

if __name__ == "__main__":
    main() 