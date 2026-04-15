"""
Django settings for offloadx_server project.

Architecture: Hybrid Storage
  - SQLite (on mobile device) : Performance logs, simple local data
  - Firebase (cloud)          : Persistent, accessible data storage
  - Laptop Hub (this server)  : Computational Node using Django framework

The Hub does NOT act as a primary storage node.
It processes data in real-time and returns results.
"""

from pathlib import Path
import os
from dotenv import load_dotenv

# Build paths inside the project like this: BASE_DIR / 'subdir'.
BASE_DIR = Path(__file__).resolve().parent.parent

# Load environment variables from .env file
load_dotenv(os.path.join(BASE_DIR, '.env'))

# SECURITY WARNING: keep the secret key used in production secret!
# Pulled from environment variables to prevent leaking keys into version control.
# Fallback provided for local/development use.
SECRET_KEY = os.environ.get(
    'DJANGO_SECRET_KEY',
    'django-insecure-fallback-only-do-not-use-in-production'
)

# SECURITY WARNING: don't run with debug turned on in production!
DEBUG = os.environ.get('DEBUG', 'False').lower() == 'true'

# Accept connections from any device on the local network (e.g., Android clients via IP)
ALLOWED_HOSTS = [h.strip() for h in os.environ.get('ALLOWED_HOSTS', '*').split(',')]


# Application definition

INSTALLED_APPS = [
    'django.contrib.admin',
    'django.contrib.auth',
    'django.contrib.contenttypes',
    'django.contrib.sessions',
    'django.contrib.messages',
    'django.contrib.staticfiles',
    'rest_framework',
    'corsheaders',
    'core',
]

MIDDLEWARE = [
    'corsheaders.middleware.CorsMiddleware',
    'django.middleware.security.SecurityMiddleware',
    'django.contrib.sessions.middleware.SessionMiddleware',
    'django.middleware.common.CommonMiddleware',
    # CSRF disabled for API-only server (mobile app clients do not use sessions/cookies)
    # 'django.middleware.csrf.CsrfViewMiddleware',
    'django.contrib.auth.middleware.AuthenticationMiddleware',
    'django.contrib.messages.middleware.MessageMiddleware',
    'django.middleware.clickjacking.XFrameOptionsMiddleware',
]

ROOT_URLCONF = 'offloadx_server.urls'

TEMPLATES = [
    {
        'BACKEND': 'django.template.backends.django.DjangoTemplates',
        'DIRS': [],
        'APP_DIRS': True,
        'OPTIONS': {
            'context_processors': [
                'django.template.context_processors.request',
                'django.contrib.auth.context_processors.auth',
                'django.contrib.messages.context_processors.messages',
            ],
        },
    },
]

WSGI_APPLICATION = 'offloadx_server.wsgi.application'


# Database
# The Hub uses SQLite only for lightweight, temporary task tracking.
# This is NOT the primary data store -- Firebase is.
DATABASES = {
    'default': {
        'ENGINE': 'django.db.backends.sqlite3',
        'NAME': BASE_DIR / 'db.sqlite3',
    }
}


# Password validation
AUTH_PASSWORD_VALIDATORS = [
    {'NAME': 'django.contrib.auth.password_validation.UserAttributeSimilarityValidator'},
    {'NAME': 'django.contrib.auth.password_validation.MinimumLengthValidator'},
    {'NAME': 'django.contrib.auth.password_validation.CommonPasswordValidator'},
    {'NAME': 'django.contrib.auth.password_validation.NumericPasswordValidator'},
]


# Internationalization
LANGUAGE_CODE = 'en-us'
TIME_ZONE = 'Asia/Kolkata'
USE_I18N = True
USE_TZ = True


# Static files (CSS, JavaScript, Images)
STATIC_URL = 'static/'

MEDIA_URL = '/media/'
MEDIA_ROOT = os.path.join(BASE_DIR, 'media')

# Default primary key field type
DEFAULT_AUTO_FIELD = 'django.db.models.BigAutoField'

# CORS Settings -- enable for local dev clients (native Android apps don't inherently use Origins, but permitted for debuggers)
CORS_ALLOW_ALL_ORIGINS = True

# DRF Settings -- Defines the local compute hub API rules
REST_FRAMEWORK = {
    'DEFAULT_AUTHENTICATION_CLASSES': [
        'rest_framework.authentication.TokenAuthentication',
    ],
    'DEFAULT_PERMISSION_CLASSES': [
        'rest_framework.permissions.AllowAny', # Open to all unauthenticated LAN clients submitting offload jobs
    ],
    'DEFAULT_RENDERER_CLASSES': [
        'rest_framework.renderers.JSONRenderer',
    ],
    'DEFAULT_THROTTLE_CLASSES': [
        'rest_framework.throttling.AnonRateThrottle',
    ],
    'DEFAULT_THROTTLE_RATES': {
        'anon': '1000/minute',  # Elevated for rapid micro-offloads from single connected devices
    }
}


# =============================================================================
# Upload Limits (Increased for Heavy Offload Tasks - up to 1GB)
# =============================================================================
DATA_UPLOAD_MAX_MEMORY_SIZE = 1048576000  # 1GB
FILE_UPLOAD_MAX_MEMORY_SIZE = 1048576000  # 1GB

# =============================================================================
# Firebase Configuration
# =============================================================================
# Firebase is the persistent cloud storage layer. The Hub pushes
# computation results to Firestore after processing.
# Place your serviceAccountKey.json in the backend/ directory.
# =============================================================================

import firebase_admin
from firebase_admin import credentials

FIREBASE_CREDENTIALS_PATH = os.path.join(BASE_DIR, 'serviceAccountKey.json')

if os.path.exists(FIREBASE_CREDENTIALS_PATH):
    cred = credentials.Certificate(FIREBASE_CREDENTIALS_PATH)
    firebase_admin.initialize_app(cred)
    print("[Hub] Firebase initialized successfully. Results will be pushed to Firestore.")
else:
    print(f"[Hub] WARNING: Firebase credentials not found at {FIREBASE_CREDENTIALS_PATH}")
    print("[Hub] Results will still be returned directly to the app, but NOT persisted to cloud.")
