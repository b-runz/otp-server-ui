#!/bin/sh

# Gradle wrapper script for Unix

APP_HOME="`cd "$(dirname "$0")" && pwd`"
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Use the maximum available, or set MAX_FD != maximum.
MAX_FD="maximum"

if [ ! -z "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
elif [ ! -z "$JAVA_HOME" ] && [ -x "$JAVA_HOME/jre/bin/java" ] ; then
    JAVACMD="$JAVA_HOME/jre/bin/java"
else
    JAVACMD="java"
fi

# Determine OS
OS_NAME=`uname -s`
case "$OS_NAME" in
  CYGWIN* | MINGW* | MSYS* )
    CLASSPATH=`cygpath --path --mixed "$CLASSPATH"`
    APP_HOME=`cygpath --path --mixed "$APP_HOME"`
    ;;
esac

GRADLE_OPTS="${GRADLE_OPTS:-}"
DEFAULT_JVM_OPTS="-Xmx64m -Xms64m"

exec "$JAVACMD" $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
