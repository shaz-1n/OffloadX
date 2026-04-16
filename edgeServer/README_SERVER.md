# OffloadX Local Cloud Server

This is the backend server for the OffloadX application, built with Django. It simulates a cloud environment using your laptop's local storage and processing power.

## Prerequisites

- **Python 3.8+** installed on your system.
- **Firebase Service Account Key**:
    1.  Go to your Firebase Console > Project Settings > Service Accounts.
    2.  Generate a new private key.
    3.  Save the file as `backend/serviceAccountKey.json`.

## Quick Start

1.  **Run the Server**:
    Double-click on `backend/run_server.bat`. 
    
    *Alternatively, run manually:*
    ```bash
    cd backend
    python -m venv venv
    venv\Scripts\activate
    pip install -r requirements.txt
    python manage.py migrate
    python run_server.py
    ```

2.  **Server Address**:
    The server will print its local IP address (e.g., `http://192.168.1.5:8000/`). Use this base URL in your Android/Flutter app.

## API Endpoints

### 1. Upload Task (JSON)
- **URL**: `/api/compute/`
- **Method**: `POST`
- **Body**: JSON
    - `data`: Payload
    - `task_type`: 'COMPOSITE' or 'COMPLEX'.

### 2. Upload File (Image Processing)
- **URL**: `/api/upload/`
- **Method**: `POST`
- **Content-Type**: `multipart/form-data`
- **Body**:
    - `file`: The image file.
    - `task_type`: 'IMAGE_GRAYSCALE'
    - `device_id`: Your Device ID.
- **Response**: JSON with processed file URL.

### 2. Check Status
- **URL**: `/api/status/<task_id>/`
- **Method**: `GET`
- **Response**: JSON object with current status ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED').

### 3. Download Result
- **URL**: `/api/download/<task_id>/`
- **Method**: `GET`
- **Response**: The processed file download.

## Logic Customization

The processing logic is currently a mock implementation in `backend/core/utils.py`. 
To implement real processing:
1.  Open `backend/core/utils.py`.
2.  Modify the `mock_processing` function (or rename/replace it) to perform the actual calculations or file transformations.
3.  Ensure you save the output file to `media/processed/` and update the `OutputFile` record.

## Firebase Integration

If `serviceAccountKey.json` is present, Firebase Admin SDK is initialized in `settings.py`. You can use it in your views to send push notifications or verify auth tokens.
