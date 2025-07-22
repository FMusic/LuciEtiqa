FROM eclipse-temurin:21-jdk

# Basic CLI + locales
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        git curl zsh unzip xz-utils ca-certificates locales && \
    locale-gen en_US.UTF-8

# Gradle (for Ktor) — adjust version if you pin it in wrapper
ARG GRADLE_VERSION=8.7
RUN curl -fsSL https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip \
    -o /tmp/gradle.zip && \
    unzip -q /tmp/gradle.zip -d /opt && \
    ln -s /opt/gradle-${GRADLE_VERSION}/bin/gradle /usr/local/bin/gradle && \
    rm /tmp/gradle.zip

# Node 22 for Tailwind builds
ARG NODE_VERSION=22.3.0
RUN curl -fsSL https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz \
    | tar -xJf - -C /opt && \
    ln -s /opt/node-v${NODE_VERSION}-linux-x64/bin/node /usr/local/bin/node && \
    ln -s /opt/node-v${NODE_VERSION}-linux-x64/bin/npm  /usr/local/bin/npm

# Create non-root user that matches devcontainer.json
USER ubuntu
WORKDIR /workspace

# Expose app + debugger
EXPOSE 8080 5005
