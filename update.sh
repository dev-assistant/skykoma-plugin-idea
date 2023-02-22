#!/bin/bash
# VERSION="0.0.1"
# usage ./update.sh 0.0.1
VERSION=$1
FILE_NAME="skykoma-plugin-idea-$VERSION.zip"
echo $FILE_NAME
echo "<?xml version=\"1.0\" encoding=\"UTF-8\"?><plugins><plugin id=\"cn.hylstudio.skykoma.plugin.idea\" url=\"https://your.domain/path/to/zip/$FILE_NAME\" version=\"$VERSION\"><idea-version since-build=\"212\" until-build=\"221.*\"/></plugin></plugins>" >  /path/to/updatePlugins.xml
