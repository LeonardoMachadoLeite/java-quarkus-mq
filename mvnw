#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup batch script, version 3.3.2
#
# Optional ENV vars
# -----------------
#   JAVA_HOME - location of a JDK home dir, required when type is jdk
#   MAVEN_OPTS - parameters passed to the Java VM when running Maven
#     e.g. to debug Maven itself, use
#       set MAVEN_OPTS=-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000
#   MAVEN_SKIP_RC - flag to disable loading of mavenrc files
# ----------------------------------------------------------------------------

if [ -z "$MAVEN_SKIP_RC" ]; then
  if [ -f /usr/local/etc/mavenrc ]; then
    . /usr/local/etc/mavenrc
  fi
  if [ -f /etc/mavenrc ]; then
    . /etc/mavenrc
  fi
  if [ -f "$HOME/.mavenrc" ]; then
    . "$HOME/.mavenrc"
  fi
fi

# OS specific support.  $var _must_ be set to either true or false.
cygwin=false
darwin=false
mingw=false
case "$(uname)" in
  CYGWIN*) cygwin=true ;;
  MINGW*) mingw=true ;;
  Darwin*) darwin=true ;;
esac

# Attempt to set APP_HOME
# Resolve links: $0 may be a link
PRG="$0"
# Need this for relative symlinks.
while [ -h "$PRG" ]; do
  ls=$(ls -ld "$PRG")
  link=$(expr "$ls" : '.*-> \(.*\)$')
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG="$(dirname "$PRG")/$link"
  fi
done
SAVED="$(pwd)"
cd "$(dirname "$PRG")" || exit 1
APP_HOME="$(pwd -P)"
cd "$SAVED" || exit 1

APP_NAME="MVN"
APP_BASE_NAME=$(basename "$0")

# Add default JVM options here. You can also use JAVA_OPTS and MAVEN_OPTS to
# pass JVM options to this script.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Find the project base dir, i.e. the directory that contains the folder ".mvn".
# Fallback to current working directory if not found.
find_maven_basedir() {
  if [ -z "$1" ]; then
    echo "Path not specified to find_maven_basedir"
    return 1
  fi
  basedir="$1"
  wdir="$1"
  while [ "$wdir" != '/' ]; do
    if [ -d "$wdir"/.mvn ]; then
      basedir=$wdir
      break
    fi
    if [ -d "${wdir}" ]; then
      wdir=$(cd "$wdir/.." && pwd)
    fi
  done
  printf '%s' "$(cd "$basedir" && pwd)"
}

# concatenates all lines of a file
concat_lines() {
  if [ -f "$1" ]; then
    # Remove \r in case we run on Windows within Git Bash
    # Fallback to cat if 'tr' is not available
    tr -s '\r\n' ' ' < "$1" 2>/dev/null || cat "$1"
  fi
}

log() {
  if [ "$MVNW_VERBOSE" = true ]; then
    printf '%s\n' "$1"
  fi
}

BASE_DIR=$(find_maven_basedir "$(dirname "$0")")
if [ -z "$BASE_DIR" ]; then
  exit 1
fi

##########################################################################################
# Extension to allow automatically downloading the maven-wrapper.jar from Maven-central
# This allows using the maven wrapper in projects that prohibit checking in binary data.
##########################################################################################
wrapperJarPath="$BASE_DIR/.mvn/wrapper/maven-wrapper.jar"
if [ -r "$wrapperJarPath" ]; then
  log "Found $wrapperJarPath"
else
  log "Couldn't find $wrapperJarPath, downloading it ..."
  if [ -n "$MVNW_REPOURL" ]; then
    wrapperUrl="$MVNW_REPOURL/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"
  else
    wrapperUrl="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"
  fi
  while IFS="=" read -r key value; do
    # Remove '\r' (possible Windows line-endings)
    key=$(echo "$key" | tr -d '\r')
    value=$(echo "$value" | tr -d '\r')
    case "$key" in
      wrapperUrl) wrapperUrl="$value" ;;
    esac
  done < "$BASE_DIR/.mvn/wrapper/maven-wrapper.properties"
  log "Downloading from: $wrapperUrl"
  if $cygwin; then
    wrapperJarPath=$(cygpath --path --windows "$wrapperJarPath")
  fi
  if command -v wget > /dev/null; then
    QUIET="--quiet"
    if [ "$MVNW_VERBOSE" = true ]; then
      QUIET=""
    fi
    wget $QUIET -O "$wrapperJarPath" "$wrapperUrl" \
      || rm -f "$wrapperJarPath"
  elif command -v curl > /dev/null; then
    QUIET="--silent"
    if [ "$MVNW_VERBOSE" = true ]; then
      QUIET=""
    fi
    curl $QUIET -o "$wrapperJarPath" "$wrapperUrl" -f \
      || rm -f "$wrapperJarPath"
  else
    echo "Cannot download $wrapperUrl"
    echo "Please install wget or curl to proceed, or download the file manually."
    exit 1
  fi
  log "Done"
fi

##########################################################################################
# End of extension
##########################################################################################

MAVEN_OPTS="$(concat_lines "$BASE_DIR/.mvn/jvm.config") $MAVEN_OPTS"

# For Cygwin, switch paths to Windows format before running java
if $cygwin; then
  [ -n "$JAVA_HOME" ] &&
    JAVA_HOME=$(cygpath --unix "$JAVA_HOME")
  [ -n "$CLASSPATH" ] &&
    CLASSPATH=$(cygpath --path --unix "$CLASSPATH")
fi

# Provide a "standardized" way to retrieve the CLI args that will
# be passed to the JVM. These are used by shell integrations such
# as IDE support.
MAVEN_CMD_LINE_ARGS="$MAVEN_CONFIG $*"
export MAVEN_CMD_LINE_ARGS

WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

WRAPPER_URL="$wrapperUrl"

# shellcheck disable=SC2086
exec "$JAVA_HOME/bin/java" \
  $MAVEN_OPTS \
  $MAVEN_DEBUG_OPTS \
  -classpath "$wrapperJarPath" \
  "-Dmaven.multiModuleProjectDirectory=${BASE_DIR}" \
  ${WRAPPER_LAUNCHER} $MAVEN_CONFIG "$@"
