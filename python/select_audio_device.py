#!/usr/bin/env python3
"""
Audio Device Selection Tool for PhantomQuery

This script helps users select the appropriate audio devices for PhantomQuery:
1. Input device for capturing speech
2. Output device for playing responses

Requirements:
- Python 3.6+
- pyaudio

Usage:
    python select_audio_device.py
"""

import os
import pyaudio
import sys

def list_audio_devices():
    """List all available audio devices and return them as lists."""
    p = pyaudio.PyAudio()
    info = p.get_host_api_info_by_index(0)
    numdevices = info.get('deviceCount')
    
    print("\n=== Available Audio Devices ===")
    input_devices = []
    output_devices = []
    
    for i in range(0, numdevices):
        device_info = p.get_device_info_by_host_api_device_index(0, i)
        name = device_info.get('name')
        max_input_channels = device_info.get('maxInputChannels')
        max_output_channels = device_info.get('maxOutputChannels')
        
        if max_input_channels > 0:
            input_devices.append((i, name))
            print(f"INPUT  {len(input_devices)}. {name}")
        
        if max_output_channels > 0:
            output_devices.append((i, name))
            print(f"OUTPUT {len(output_devices)}. {name}")
    
    p.terminate()
    return input_devices, output_devices

def select_device(input_devices, output_devices):
    """Let the user select a device from the list."""
    print("\n=== Device Selection ===")
    print("For PhantomQuery, you need to:")
    print("1. Select a CABLE Output device (INPUT) to capture audio")
    print("2. Make sure your system audio is routed to a CABLE Input device (OUTPUT)")
    print("\nWhich type of device do you want to select?")
    print("1. Input device (for capturing audio)")
    print("2. Output device (for routing audio)")
    print("q. Quit")
    
    device_type = input("\nEnter your choice: ")
    
    if device_type.lower() == 'q':
        return None, None
    
    if device_type == '1':
        devices = input_devices
        device_type_name = "input"
    elif device_type == '2':
        devices = output_devices
        device_type_name = "output"
    else:
        print("Invalid choice")
        return None, None
    
    if not devices:
        print(f"No {device_type_name} devices found.")
        return None, None
    
    print(f"\nAvailable {device_type_name} devices:")
    for i, (device_id, name) in enumerate(devices):
        print(f"{i+1}. {name}")
    
    while True:
        selection = input(f"\nEnter the number of the {device_type_name} device to use (or 'q' to quit): ")
        if selection.lower() == 'q':
            return None, None
        
        try:
            index = int(selection) - 1
            if 0 <= index < len(devices):
                return devices[index], device_type_name
            else:
                print(f"Please enter a number between 1 and {len(devices)}")
        except ValueError:
            print("Please enter a valid number")

def save_device_config(device_id, device_name, device_type):
    """Save the selected device configuration to a file."""
    config_dir = os.path.dirname(os.path.abspath(__file__))
    config_file = os.path.join(config_dir, "device_config.txt")
    
    with open(config_file, "w") as f:
        f.write(f"device_id={device_id}\n")
        f.write(f"device_name={device_name}\n")
        f.write(f"device_type={device_type}\n")
    
    print(f"\nDevice configuration saved to {config_file}")

def main():
    print("=== Audio Device Selection Tool ===")
    print("This tool helps you select audio devices for PhantomQuery.")
    
    input_devices, output_devices = list_audio_devices()
    selected_device, device_type = select_device(input_devices, output_devices)
    
    if selected_device:
        device_id, device_name = selected_device
        print(f"\nSelected {device_type} device: {device_name} (ID: {device_id})")
        
        # Save the configuration
        save_device_config(device_id, device_name, device_type)
        
        # Print instructions based on device type
        if device_type == "input":
            print("\nThis device will be used to capture audio.")
            print("Make sure your system audio is routed to a CABLE Input device.")
            print("\nYou can now run run_audio_capture.bat to start capturing audio.")
        else:
            print("\nFor this output device, you need to:")
            print("1. Open Windows Sound settings")
            print("2. Go to 'App volume and device preferences'")
            print("3. Find the application you want to capture audio from (e.g., Chrome, Spotify)")
            print("4. Set its output to this device")
            print("\nAfter setting this up, run the audio capture script with a CABLE Output device.")
    else:
        print("\nNo device selected. Exiting.")

if __name__ == "__main__":
    main() 