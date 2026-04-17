from django.contrib import admin
from django.urls import path, include
from django.conf import settings
from django.conf.urls.static import static

urlpatterns = [
    path('admin/', admin.site.urls),
    path('api/', include('core.urls')),
]

# Always serve media files — this is a local LAN computational node, not a public server.
# Files uploaded/processed must be downloadable by the Android client at all times.
urlpatterns += static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)
