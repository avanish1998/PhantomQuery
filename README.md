# PhantomQuery

A real-time speech processing and AI response system that seamlessly converts audio input to text and generates intelligent responses.

## Features

- Real-time speech detection and transcription using Google Cloud Speech-to-Text
- AI-powered responses using OpenAI's GPT models
- WebSocket-based communication between Python audio capture and Java backend
- Spring Boot backend with WebSocket support
- Modern web interface with real-time updates

## Prerequisites

- Java 17 or higher
- Python 3.8 or higher
- Google Cloud account with Speech-to-Text API enabled
- OpenAI API key

## Setup

1. Clone the repository
2. Set up Google Cloud credentials:
   - Create a service account with Speech-to-Text API access
   - Download the JSON key file
   - Set the `GOOGLE_APPLICATION_CREDENTIALS` environment variable to point to the key file

3. Set up OpenAI API key:
   - Create a `.env` file in the root directory
   - Add your OpenAI API key: `OPENAI_API_KEY=your_key_here`

4. Install Python dependencies:
   ```bash
   cd python
   pip install -r requirements.txt
   ```

5. Build and run the Java application:
   ```bash
   ./mvnw spring-boot:run
   ```

6. Run the Python audio capture script:
   ```bash
   cd python
   python audio_capture.py
   ```

## Project Structure

- `src/` - Java Spring Boot application
  - `controller/` - WebSocket and REST controllers
  - `service/` - Business logic and external service integration
  - `model/` - Data models and DTOs
  - `config/` - Application configuration

- `python/` - Python audio capture application
  - `audio_capture.py` - Main audio capture and WebSocket client
  - `select_audio_device.py` - Audio device selection utility
  - `requirements.txt` - Python dependencies

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.
