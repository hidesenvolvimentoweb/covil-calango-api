package br.com.yugiapp.config;

import br.com.yugiapp.service.ArquivoService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final ArquivoService arquivoService;

    /**
     * Serve os arquivos enviados (imagens de produtos) diretamente do diretório de uploads.
     * Cada upload gera um nome único, então o conteúdo de uma URL nunca muda e pode ficar em cache.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(ArquivoService.URL_PREFIX + "**")
                .addResourceLocations(arquivoService.getRaiz().toUri().toString())
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic());
    }
}
