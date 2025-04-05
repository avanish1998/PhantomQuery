#!/usr/bin/env python3
"""
Audio Capture Script for AI Interview Assistant

This script captures audio from a virtual audio device and sends it to the Java backend
via WebSocket for real-time speech-to-text processing.

Requirements:
- Python 3.6+
- pyaudio
- websocket-client
- numpy

Usage:
    python audio_capture.py [--device DEVICE_ID] [--sample-rate SAMPLE_RATE] [--chunk-size CHUNK_SIZE]

Example:
    python audio_capture.py --device 1 --sample-rate 16000 --chunk-size 1024
"""

import argparse
import base64
import json
import sys
import time
import uuid
import wave
from threading import Thread

import numpy as np
import pyaudio
import websocket
from websocket import WebSocketApp

# Default configuration
DEFAULT_SAMPLE_RATE = 16000
DEFAULT_CHUNK_SIZE = 1024
DEFAULT_CHANNELS = 1
DEFAULT_FORMAT = pyaudio.paInt16
DEFAULT_WEBSOCKET_URL = "ws://localhost:8080/audio-stream/websocket"

class AudioCapture:
    def __init__(self, device_id=None, sample_rate=DEFAULT_SAMPLE_RATE, 
                 chunk_size=DEFAULT_CHUNK_SIZE, channels=DEFAULT_CHANNELS, 
                 format=DEFAULT_FORMAT, websocket_url=DEFAULT_WEBSOCKET_URL):
        self.device_id = device_id
        self.sample_rate = sample_rate
        self.chunk_size = chunk_size
        self.channels = channels
        self.format = format
        self.websocket_url = websocket_url
        self.client_id = str(uuid.uuid4())
        self.session_id = None
        self.is_running = False
        self.audio = pyaudio.PyAudio()
        self.ws = None
        
        # Print available audio devices
        self._print_audio_devices()
    
    def _print_audio_devices(self):
        """Print all available audio devices."""
        print("\nAvailable Audio Devices:")
        print("------------------------")
        for i in range(self.audio.get_device_count()):
            device_info = self.audio.get_device_info_by_index(i)
            device_name = device_info.get('name', 'Unknown')
            max_input_channels = device_info.get('maxInputChannels', 0)
            max_output_channels = device_info.get('maxOutputChannels', 0)
            
            device_type = "Input" if max_input_channels > 0 else "Output"
            if max_input_channels > 0 and max_output_channels > 0:
                device_type = "Input/Output"
            
            print(f"Device {i}: {device_name} ({device_type})")
        
        print("------------------------\n")
    
    def _on_message(self, ws, message):
        """Handle incoming WebSocket messages."""
        try:
            data = json.loads(message)
            message_type = data.get('type')
            
            if message_type == 'started':
                self.session_id = data.get('sessionId')
                print(f"Stream started with session ID: {self.session_id}")
            elif message_type == 'transcription':
                text = data.get('text', '')
                print(f"Transcription: {text}")
            elif message_type == 'error':
                error = data.get('message', 'Unknown error')
                print(f"Error: {error}")
            elif message_type == 'stopped':
                print("Stream stopped")
                self.is_running = False
        except Exception as e:
            print(f"Error processing message: {e}")
    
    def _on_error(self, ws, error):
        """Handle WebSocket errors."""
        print(f"WebSocket error: {error}")
    
    def _on_close(self, ws, close_status_code, close_msg):
        """Handle WebSocket connection close."""
        print("WebSocket connection closed")
        self.is_running = False
    
    def _on_open(self, ws):
        """Handle WebSocket connection open."""
        print("WebSocket connection established")
        
        # Start the stream
        start_message = {
            'clientId': self.client_id
        }
        ws.send(json.dumps(start_message))
    
    def _audio_callback(self, in_data, frame_count, time_info, status):
        """Process audio data and send it to the WebSocket."""
        if self.is_running and self.ws and self.ws.sock and self.ws.sock.connected:
            try:
                # Convert audio data to base64
                audio_data = base64.b64encode(in_data).decode('utf-8')
                
                # Create message
                message = {
                    'clientId': self.client_id,
                    'audioData': audio_data
                }
                
                # Send audio data
                self.ws.send(json.dumps(message))
            except Exception as e:
                print(f"Error sending audio data: {e}")
        
        return (in_data, pyaudio.paContinue)
    
    def start(self):
        """Start capturing audio and sending it to the WebSocket."""
        if self.is_running:
            print("Audio capture is already running")
            return
        
        # Connect to WebSocket
        websocket.enableTrace(True)
        self.ws = WebSocketApp(
            self.websocket_url,
            on_message=self._on_message,
            on_error=self._on_error,
            on_close=self._on_close,
            on_open=self._on_open
        )
        
        # Start WebSocket connection in a separate thread
        ws_thread = Thread(target=self.ws.run_forever)
        ws_thread.daemon = True
        ws_thread.start()
        
        # Wait for WebSocket connection to be established
        time.sleep(1)
        
        # Open audio stream
        stream = self.audio.open(
            format=self.format,
            channels=self.channels,
            rate=self.sample_rate,
            input=True,
            input_device_index=self.device_id,
            frames_per_buffer=self.chunk_size,
            stream_callback=self._audio_callback
        )
        
        # Start the stream
        stream.start_stream()
        self.is_running = True
        
        print(f"Audio capture started (Device ID: {self.device_id}, Sample Rate: {self.sample_rate}, Chunk Size: {self.chunk_size})")
        print("Press Ctrl+C to stop")
        
        try:
            while self.is_running:
                time.sleep(0.1)
        except KeyboardInterrupt:
            print("\nStopping audio capture...")
            self.stop()
    
    def stop(self):
        """Stop capturing audio and close the WebSocket connection."""
        if not self.is_running:
            return
        
        self.is_running = False
        
        # Stop the stream
        if self.ws and self.ws.sock and self.ws.sock.connected:
            stop_message = {
                'clientId': self.client_id
            }
            self.ws.send(json.dumps(stop_message))
            self.ws.close()
        
        # Close PyAudio
        self.audio.terminate()
        
        print("Audio capture stopped")

def main():
    parser = argparse.ArgumentParser(description="Capture audio from a virtual device and send it to the Java backend")
    parser.add_argument("--device", type=int, help="Audio device ID (use -1 for default device)")
    parser.add_argument("--sample-rate", type=int, default=DEFAULT_SAMPLE_RATE, help=f"Sample rate (default: {DEFAULT_SAMPLE_RATE})")
    parser.add_argument("--chunk-size", type=int, default=DEFAULT_CHUNK_SIZE, help=f"Chunk size (default: {DEFAULT_CHUNK_SIZE})")
    parser.add_argument("--websocket-url", type=str, default=DEFAULT_WEBSOCKET_URL, help=f"WebSocket URL (default: {DEFAULT_WEBSOCKET_URL})")
    
    args = parser.parse_args()
    
    # Create audio capture
    audio_capture = AudioCapture(
        device_id=args.device,
        sample_rate=args.sample_rate,
        chunk_size=args.chunk_size,
        websocket_url=args.websocket_url
    )
    
    # Start capturing audio
    audio_capture.start()

if __name__ == "__main__":
    main() 