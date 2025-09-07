package com.suhas.codeAnalyzer.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Properties;

/**
 * Environment post-processor to automatically load .env files based on active profiles.
 * This ensures development environment variables are loaded automatically when running the application.
 */
public class EnvironmentConfig implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // Get active profiles, default to 'development' if none set
        String[] activeProfiles = environment.getActiveProfiles();
        String profile = activeProfiles.length > 0 ? activeProfiles[0] : "development";
        
        // Load .env file for the active profile
        String envFileName = ".env." + profile;
        Resource envResource = new FileSystemResource(envFileName);
        
        if (envResource.exists()) {
            try {
                Properties properties = new Properties();
                properties.load(envResource.getInputStream());
                
                // Convert properties to Spring Boot format and add to environment
                Properties springProperties = new Properties();
                properties.forEach((key, value) -> {
                    String springKey = convertToSpringBootFormat(key.toString());
                    springProperties.setProperty(springKey, value.toString().replace("${GROQ_API_KEY}", 
                        environment.getProperty("GROQ_API_KEY", value.toString())));
                });
                
                PropertiesPropertySource propertySource = new PropertiesPropertySource(
                    "env-" + profile, springProperties);
                environment.getPropertySources().addLast(propertySource);
                
                System.out.println("✅ Loaded environment file: " + envFileName + " (" + springProperties.size() + " properties)");
                
                // Debug: Print MongoDB-related properties
                springProperties.entrySet().stream()
                    .filter(entry -> entry.getKey().toString().contains("mongodb"))
                    .forEach(entry -> {
                        String value = entry.getValue().toString();
                        if (entry.getKey().toString().contains("password")) {
                            value = "***HIDDEN***";
                        }
                        System.out.println("  🔧 " + entry.getKey() + " = " + value);
                    });
                
            } catch (IOException e) {
                System.out.println("⚠️  Could not load environment file: " + envFileName + " - " + e.getMessage());
            }
        } else {
            System.out.println("⚠️  Environment file not found: " + envFileName);
        }
        
        // Also try to load .env (without profile suffix)
        Resource defaultEnvResource = new FileSystemResource(".env");
        if (defaultEnvResource.exists()) {
            try {
                Properties properties = new Properties();
                properties.load(defaultEnvResource.getInputStream());
                
                Properties springProperties = new Properties();
                properties.forEach((key, value) -> {
                    String springKey = convertToSpringBootFormat(key.toString());
                    if (!environment.containsProperty(springKey)) { // Don't override profile-specific values
                        springProperties.setProperty(springKey, value.toString());
                    }
                });
                
                if (!springProperties.isEmpty()) {
                    PropertiesPropertySource propertySource = new PropertiesPropertySource("env-default", springProperties);
                    environment.getPropertySources().addLast(propertySource);
                    System.out.println("✅ Loaded default environment file: .env");
                }
                
            } catch (IOException e) {
                System.out.println("⚠️  Could not load default environment file: .env - " + e.getMessage());
            }
        }
    }
    
    /**
     * Convert environment variable names to Spring Boot property format.
     * Example: SPRING_DATA_MONGODB_HOST -> spring.data.mongodb.host
     */
    private String convertToSpringBootFormat(String envVar) {
        return envVar.toLowerCase().replace('_', '.');
    }
}