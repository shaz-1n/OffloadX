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

# Build paths inside the project like this: BASE_DIR / 'subdir'.
BASE_DIR = Path(__file__).resolve().parent.parent

# SECURITY WARNING: keep the secret key used in production secret!
SECRET_KEY = 'django-insecure-q-@(8b2if$qrj!z2f!^=4@73@ys-&ndrtru+@@j05*7b^*_z13'

# SECURITY WARNING: don't run with debug turned on in production!
DEBUG = True

# Accept connections from any device on the local network
ALLOWED_HOSTS = ['*']


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
    # CSRF disabled for API-only server (mobile app clients)
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

# Default primary key field type
DEFAULT_AUTO_FIELD = 'django.db.models.BigAutoField'

# CORS Settings -- allow mobile app on same LAN
CORS_ALLOW_ALL_ORIGINS = True

# DRF Settings -- no authentication required for local compute hub
REST_FRAMEWORK = {
    'DEFAULT_AUTHENTICATION_CLASSES': [],
    'DEFAULT_PERMISSION_CLASSES': [
        'rest_framework.permissions.AllowAny',
    ],
    'DEFAULT_RENDERER_CLASSES': [
        'rest_framework.renderers.JSONRenderer',
    ],
}


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
