package com.forenstorage.web.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
public class DataSourceConfiguration {
    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(DataSourceProperties properties) throws IOException {
        String url = properties.determineUrl();
        if (url.startsWith("jdbc:sqlite:") && !url.equals("jdbc:sqlite::memory:")) {
            String filename = url.substring("jdbc:sqlite:".length());
            // URI-style SQLite URLs remain managed by the driver, without guessing their filesystem path.
            if (!filename.startsWith("file:") && !filename.isBlank()) {
                Files.createDirectories(Path.of(filename).toAbsolutePath().normalize().getParent());
            }
        }
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }
}
