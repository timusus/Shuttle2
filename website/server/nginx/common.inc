# Shared by the live and preview server blocks in site.conf (not a .conf, so nginx doesn't load it on its own).
# No add_header here: it would replace the preview block's X-Robots-Tag. Caching uses `expires` instead.

index index.html;
charset utf-8;
server_tokens off;
error_page 404 /404.html;

gzip on;
gzip_types text/css image/svg+xml application/xml text/plain;

# Extensionless URLs, as the old site's .htaccess served them: /privacy -> privacy.html. /privacy.html keeps working.
location / {
    try_files $uri $uri.html $uri/ =404;
}

location /assets/ {
    expires 30d;
}

location /css/ {
    expires 1h;
}

# Kept from the old site, outside the deployed release (copied once into /srv/site/legacy, see README.md).
# /chromecast/ may hold the Cast receiver's stylesheet; /cv/ is a personal page.
location ^~ /chromecast/ {
    root /srv/site/legacy;
}

location ^~ /cv/ {
    root /srv/site/legacy;
}

# The old site's contact form and old assets are gone for good.
location = /php/contactform.php {
    return 410;
}

location ~ ^/(images|js)/ {
    return 410;
}

location = /404.html {
    internal;
}
