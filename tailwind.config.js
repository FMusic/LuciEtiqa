/** @type {import('tailwindcss').Config} */
module.exports = {
    content: [
        "./src/main/resources/templates/thymeleaf/**/*.html",
        "./src/main/resources/templates/thymeleaf/*.html",
        "./src/main/resources/static/**/*.html",
        "./src/main/resources/static/*.html",
    ],
    theme: {
        extend: {},
    },
    plugins: [],
}
