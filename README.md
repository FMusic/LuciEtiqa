# LuciEtiqa

LuciEtiqa is a monolithic Kotlin web application built using Ktor. It provides a lightweight backend for serving HTML content using a template engine, along with static resources like CSS and JavaScript.

## Features

- Server-rendered HTML pages using templates
- Static content support for CSS, JS, images
- Clean routing and modular structure
- Optimized for performance with response compression
- Configured for scalable deployment (Docker-ready)

## Running the Project

To run the app locally:

```bash
./gradlew run
````

The server will start at [http://localhost:8080](http://localhost:8080)

## Building a Docker Image

```bash
docker build -t lucietiqa .
docker run -p 8080:8080 lucietiqa
```

## License

This project is open source and available under the [MIT License](LICENSE).
