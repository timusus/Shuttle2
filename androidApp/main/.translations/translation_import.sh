#!/bin/sh

#Example: ./translation_import.sh "/mnt/c/users/Tim/Downloads/S2 Music Player.zip"

echo "Clearing temp folder"
rm -r "./temp"

echo "Unzipping files to ./temp/"
mkdir -p "./temp"
unzip "$1" -d "./temp" | awk 'BEGIN {ORS=" "} {print "."}'

echo "copying files from ./temp/strings_media_provider_core.xml"
rsync -va --info=progress2 "./temp/strings_media_provider_core.xml/" "../mediaprovider/core/src/main/res/"
rm -rf "./temp/strings_media_provider_core.xml"

echo "copying files from ./temp/strings_core.xml"
rsync -va --info=progress2 "./temp/strings_core.xml/" "../core/src/main/res/"
rm -rf "./temp/strings_core.xml"

for d in ./temp/**/ ; do
  echo --info=progress2 "copying files from $d"
  rsync -va "$d" "../app/src/main/res/"
done

rm -r "./temp"