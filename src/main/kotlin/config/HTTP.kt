package mjuzik.le.config

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.thymeleaf.Thymeleaf
import org.thymeleaf.templatemode.TemplateMode
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver
import org.thymeleaf.templateresolver.FileTemplateResolver

fun Application.configureHTTP(env: String) {
    install(DefaultHeaders) {
        header("X-Engine", "Ktor") // will send this header with each response
    }
    install(Compression)
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        anyHost() // @TODO: Don't do this in production if possible. Try to limit it.
    }
    install(Thymeleaf) {
        if (env == "dev") {
            /* use the real files directly so no copy is required */
            setTemplateResolver(FileTemplateResolver().apply {
                prefix = "src/main/resources/templates/thymeleaf/"   // <── project path
                suffix = ".html"
                templateMode = TemplateMode.HTML
                characterEncoding = "UTF-8"
//                cacheable = false                         // live-reload
            })
        } else {
            /* class-path resolver with cache for prod */
            setTemplateResolver(ClassLoaderTemplateResolver().apply {
                prefix = "templates/thymeleaf/"
                suffix = ".html"
                templateMode = TemplateMode.HTML
                characterEncoding = "UTF-8"
//                cacheable = true
            })
        }
    }
}
