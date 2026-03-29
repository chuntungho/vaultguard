package com.vaultguard.config;

import java.nio.file.Path;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebVaultConfig implements WebMvcConfigurer {

    private final VaultGuardProperties props;

    public WebVaultConfig(VaultGuardProperties props) {
        this.props = props;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Admin static pages from classpath
        registry.addResourceHandler("/admin/**")
            .addResourceLocations("classpath:/static/admin/");

        // Web vault from filesystem path (optional)
        String vaultPath = props.getWebVaultPath();
        if (vaultPath != null && !vaultPath.isBlank()) {
            String location = "file:" + Path.of(vaultPath).toAbsolutePath().normalize().toString().replace("\\", "/");
            if (!location.endsWith("/")) location += "/";
            registry.addResourceHandler("/app/**", "/assets/**", "/vw_static/**", "/")
                .addResourceLocations(location)
                .resourceChain(false);
        }
    }
}
