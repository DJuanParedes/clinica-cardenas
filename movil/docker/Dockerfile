# Imagen para compilar el APK sin instalar Android Studio: JDK 17 + Gradle + Android SDK 34.
FROM gradle:8.10.2-jdk17

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

RUN apt-get update && apt-get install -y --no-install-recommends wget unzip rsync && rm -rf /var/lib/apt/lists/* \
 && mkdir -p $ANDROID_HOME/cmdline-tools \
 && wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/tools.zip \
 && unzip -q /tmp/tools.zip -d $ANDROID_HOME/cmdline-tools \
 && mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest \
 && rm /tmp/tools.zip \
 && yes | sdkmanager --licenses > /dev/null \
 && sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools" > /dev/null

COPY construir.sh /usr/local/bin/construir.sh
RUN sed -i 's/\r$//' /usr/local/bin/construir.sh && chmod +x /usr/local/bin/construir.sh
WORKDIR /work
ENTRYPOINT ["/usr/local/bin/construir.sh"]
