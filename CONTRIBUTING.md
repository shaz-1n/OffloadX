Firstly, thank you for considering contributing to OffloadX.

## Local Environment Setup
OffloadX is a split-architecture project (Android + Django). Heres how to get everything setup:

## Django backend 
1. Clone the repo.
2. Setup venv and activate it.
3. Install dependencies (Ensure you have NVIDIA GPU with cuda cores support).
4. Database setup:
`python manage.py migrate`
5. Launch server: `python manage.py runserver 0.0.0.0:8000`

## Android Setup
1. Open the project in Android Studio.
2. Sync project with Gradle files.
3. Configure your IP: Ensure your `HubOffloadClient.kt` is pointing to your Laptop's local IP address
4. Build and run on a physical device.

## Branching Strategy
* we use main branch for stable release only
* Create a new branch for adding feature of fix `git checkout -b feature/your-feature-name`
* Submit a PR to the `offloading` branch for review before we'll merge into `main`

## (?) Have questions
If youre stuck, feel free to open an Issue using "Bug Report" template!!
