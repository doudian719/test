package com.joe.task.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.http.HttpStatusCode;
import org.springframework.lang.NonNull;
import lombok.extern.slf4j.Slf4j;
import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.security.SecureRandom;
import java.net.HttpURLConnection;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Configuration
public class RestTemplateConfig {
    
    @Bean
    public RestTemplate restTemplate() {
        try {
            RestTemplate restTemplate = new RestTemplate(clientHttpRequestFactory());
            
            // Configure error handler
            restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
                @Override
                public void handleError(@NonNull org.springframework.http.client.ClientHttpResponse response) throws java.io.IOException {
                    HttpStatusCode statusCode = response.getStatusCode();
                    if (statusCode.is5xxServerError()) {
                        log.error("Server error: {}", statusCode);
                    } else if (statusCode.is4xxClientError()) {
                        log.error("Client error: {}", statusCode);
                    }
                    super.handleError(response);
                }
            });
            
            return restTemplate;
        } catch (Exception e) {
            log.error("Failed to create RestTemplate with SSL configuration, falling back to default", e);
            return new RestTemplate();
        }
    }
    
    @Bean
    public ClientHttpRequestFactory clientHttpRequestFactory() throws KeyManagementException, NoSuchAlgorithmException {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(@NonNull HttpURLConnection connection, @NonNull String httpMethod) throws java.io.IOException {
                super.prepareConnection(connection, httpMethod);
                
                if (connection instanceof HttpsURLConnection httpsConnection) {
                    try {
                        httpsConnection.setSSLSocketFactory(createSSLContext().getSocketFactory());
                        httpsConnection.setHostnameVerifier((hostname, session) -> true);
                    } catch (Exception e) {
                        log.error("Failed to configure SSL for connection", e);
                    }
                }
            }
        };
        
        // Set timeout configurations
        factory.setConnectTimeout(5000); // 5 seconds connection timeout
        factory.setReadTimeout(10000);   // 10 seconds read timeout
        
        return factory;
    }
    
    private SSLContext createSSLContext() throws NoSuchAlgorithmException, KeyManagementException {
        // Create a trust manager that trusts all certificates
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
                
                @Override
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    // Trust all client certificates
                }
                
                @Override
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    // Trust all server certificates
                }
            }
        };
        
        // Create SSL context that uses our trust manager
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new SecureRandom());
        
        return sslContext;
    }
} 