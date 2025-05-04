package com.joe.task.util;

import cn.hutool.http.ContentType;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.KeyManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.http.HttpTimeoutException;
import java.security.KeyStore;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Function;
import java.time.Duration;

/**
 * 增强型HTTP客户端工具类，用于发送HTTP请求
 * 支持同步/异步请求、SSL、重试策略、连接池和请求/响应拦截器
 * 
 * 使用示例：
 * // 同步GET请求
 * String response = HttpClientUtil.get("http://example.com").execute();
 * 
 * // 异步POST请求
 * CompletableFuture<String> future = HttpClientUtil.post("http://example.com")
 *     .body(jsonData)
 *     .executeAsync();
 * 
 * // 带配置的请求
 * String response = HttpClientUtil.get("http://example.com")
 *     .config(HttpClientUtil.config()
 *         .timeout(5000)
 *         .retryPolicy(new RetryConfig().maxRetries(3)))
 *     .execute();
 */
public class HttpClientUtil {
    private static final Logger logger = LoggerFactory.getLogger(HttpClientUtil.class);

    // 默认超时设置（毫秒）
    private static final int DEFAULT_TIMEOUT = 10000;

    // 默认重试设置
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long DEFAULT_RETRY_DELAY = 1000; // 1秒

    // 用于异步操作的线程池
    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    // 连接管理器（简化实现）
    private static final ConnectionManager connectionManager = new ConnectionManager();

    /**
     * 自定义HTTP异常类
     */
    public static class HttpClientException extends RuntimeException {
        public HttpClientException(String message) {
            super(message);
        }

        public HttpClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class HttpConnectionException extends HttpClientException {
        public HttpConnectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class HttpTimeoutException extends HttpClientException {
        public HttpTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class HttpResponseException extends HttpClientException {
        private final int statusCode;
        private final String responseBody;

        public HttpResponseException(int statusCode, String responseBody) {
            super("HTTP请求失败，状态码: " + statusCode);
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getResponseBody() {
            return responseBody;
        }
    }

    /**
     * HTTP客户端配置
     */
    public static class Config {
        private int timeout = DEFAULT_TIMEOUT;
        private RetryConfig retryConfig = new RetryConfig();
        private List<Consumer<HttpRequest>> requestInterceptors = new ArrayList<>();
        private List<Function<HttpResponse, HttpResponse>> responseInterceptors = new ArrayList<>();
        private ContentType contentType = ContentType.JSON;
        private boolean useConnectionPool = true;
        private Map<String, String> defaultHeaders = new HashMap<>();
        private MetricsCollector metricsCollector = null;

        public Config timeout(int timeout) {
            this.timeout = timeout;
            return this;
        }

        public Config retryPolicy(RetryConfig retryConfig) {
            this.retryConfig = retryConfig;
            return this;
        }

        public Config addRequestInterceptor(Consumer<HttpRequest> interceptor) {
            this.requestInterceptors.add(interceptor);
            return this;
        }

        public Config addResponseInterceptor(Function<HttpResponse, HttpResponse> interceptor) {
            this.responseInterceptors.add(interceptor);
            return this;
        }

        public Config contentType(ContentType contentType) {
            this.contentType = contentType;
            return this;
        }

        public Config useConnectionPool(boolean useConnectionPool) {
            this.useConnectionPool = useConnectionPool;
            return this;
        }

        public Config defaultHeader(String name, String value) {
            this.defaultHeaders.put(name, value);
            return this;
        }

        public Config metricsCollector(MetricsCollector metricsCollector) {
            this.metricsCollector = metricsCollector;
            return this;
        }
    }

    /**
     * 内容类型枚举
     */
    public enum ContentType {
        JSON("application/json"),
        XML("application/xml"),
        FORM("application/x-www-form-urlencoded"),
        MULTIPART("multipart/form-data"),
        TEXT("text/plain");

        private final String value;

        ContentType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 重试配置
     */
    public static class RetryConfig {
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private long initialRetryDelay = DEFAULT_RETRY_DELAY;
        private boolean useExponentialBackoff = false;
        private double backoffMultiplier = 2.0;
        private Predicate<Throwable> retryPredicate = e -> true;
        private Predicate<Integer> statusCodeRetryPredicate = code -> code >= 500;
        private Consumer<Integer> retryCallback = null;

        public RetryConfig maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public RetryConfig retryDelay(long retryDelay) {
            this.initialRetryDelay = retryDelay;
            return this;
        }

        public RetryConfig exponentialBackoff(boolean useExponentialBackoff) {
            this.useExponentialBackoff = useExponentialBackoff;
            return this;
        }

        public RetryConfig backoffMultiplier(double multiplier) {
            this.backoffMultiplier = multiplier;
            return this;
        }

        public RetryConfig retryOn(Predicate<Throwable> retryPredicate) {
            this.retryPredicate = retryPredicate;
            return this;
        }

        public RetryConfig retryOnStatusCodes(Predicate<Integer> statusCodePredicate) {
            this.statusCodeRetryPredicate = statusCodePredicate;
            return this;
        }

        public RetryConfig retryCallback(Consumer<Integer> callback) {
            this.retryCallback = callback;
            return this;
        }

        public long getDelayForAttempt(int attempt) {
            if (!useExponentialBackoff) {
                return initialRetryDelay;
            }
            return (long) (initialRetryDelay * Math.pow(backoffMultiplier, attempt - 1));
        }
    }

    /**
     * SSL配置（使用构建器模式）
     */
    public static class SSLConfig {
        private String keyStorePath;
        private String keyStorePassword;
        private String trustStorePath;
        private String trustStorePassword;
        private KeyStore keyStore;
        private KeyStore trustStore;
        private String keyStoreType = "JKS";
        private String trustStoreType = "JKS";

        private SSLConfig() {
            // 私有构造函数，用于构建器模式
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private SSLConfig config = new SSLConfig();

            public Builder keyStore(String path, String password) {
                config.keyStorePath = path;
                config.keyStorePassword = password;
                return this;
            }

            public Builder keyStore(KeyStore keyStore, String password) {
                config.keyStore = keyStore;
                config.keyStorePassword = password;
                return this;
            }

            public Builder keyStoreType(String type) {
                config.keyStoreType = type;
                return this;
            }

            public Builder trustStore(String path, String password) {
                config.trustStorePath = path;
                config.trustStorePassword = password;
                return this;
            }

            public Builder trustStore(KeyStore trustStore, String password) {
                config.trustStore = trustStore;
                config.trustStorePassword = password;
                return this;
            }

            public Builder trustStoreType(String type) {
                config.trustStoreType = type;
                return this;
            }

            public SSLConfig build() {
                return config;
            }
        }
    }

    /**
     * 简单连接管理器
     */
    private static class ConnectionManager {
        // 这是一个简化实现
        // 在实际项目中，应该使用专门的连接池库

        public HttpRequest getConnection(String url, boolean usePool) {
            // 在实际实现中，这里应该从连接池返回连接
            // 或者在没有可用连接时创建新的连接
            return HttpRequest.of(url);
        }

        public void releaseConnection(HttpRequest request) {
            // 在实际实现中，这里应该将连接归还到连接池
        }
    }

    /**
     * HTTP响应包装器
     */
    public static class HttpResult<T> {
        private final int statusCode;
        private final Map<String, List<String>> headers;
        private final T body;
        private final String rawBody;
        private final long requestTime;

        public HttpResult(int statusCode, Map<String, List<String>> headers, T body, String rawBody, long requestTime) {
            this.statusCode = statusCode;
            this.headers = headers;
            this.body = body;
            this.rawBody = rawBody;
            this.requestTime = requestTime;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public Map<String, List<String>> getHeaders() {
            return headers;
        }

        public T getBody() {
            return body;
        }

        public String getRawBody() {
            return rawBody;
        }

        public long getRequestTime() {
            return requestTime;
        }

        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }
    }

    /**
     * 指标收集接口
     */
    public interface MetricsCollector {
        void recordRequestStart(String method, String url);
        void recordRequestComplete(String method, String url, int statusCode, long duration);
        void recordRequestFailure(String method, String url, Throwable error);
    }

    /**
     * 请求构建器（流式API）
     */
    public static class RequestBuilder {
        private final String url;
        private final String method;
        private Map<String, String> headers = new HashMap<>();
        private Object body;
        private Config config;
        private SSLConfig sslConfig;
        private Map<String, String> queryParams = new HashMap<>();
        private Map<String, File> fileParams = new HashMap<>();

        private RequestBuilder(String url, String method) {
            this.url = url;
            this.method = method;
        }

        public RequestBuilder header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        public RequestBuilder headers(Map<String, String> headers) {
            this.headers.putAll(headers);
            return this;
        }

        public RequestBuilder body(Object body) {
            this.body = body;
            return this;
        }

        public RequestBuilder config(Config config) {
            this.config = config;
            return this;
        }

        public RequestBuilder ssl(SSLConfig sslConfig) {
            this.sslConfig = sslConfig;
            return this;
        }

        public RequestBuilder queryParam(String name, String value) {
            queryParams.put(name, value);
            return this;
        }

        public RequestBuilder queryParams(Map<String, String> params) {
            queryParams.putAll(params);
            return this;
        }

        public RequestBuilder file(String name, File file) {
            fileParams.put(name, file);
            return this;
        }

        public String execute() {
            return executeRequest(this);
        }

        public <T> HttpResult<T> execute(Class<T> responseType) {
            return executeRequest(this, responseType);
        }

        public CompletableFuture<String> executeAsync() {
            return CompletableFuture.supplyAsync(() -> execute(), executorService);
        }

        public <T> CompletableFuture<HttpResult<T>> executeAsync(Class<T> responseType) {
            return CompletableFuture.supplyAsync(() -> execute(responseType), executorService);
        }
    }

    /**
     * 创建新的请求构建器
     */
    public static RequestBuilder get(String url) {
        return new RequestBuilder(url, "GET");
    }

    public static RequestBuilder post(String url) {
        return new RequestBuilder(url, "POST");
    }

    public static RequestBuilder put(String url) {
        return new RequestBuilder(url, "PUT");
    }

    public static RequestBuilder delete(String url) {
        return new RequestBuilder(url, "DELETE");
    }

    public static RequestBuilder patch(String url) {
        return new RequestBuilder(url, "PATCH");
    }

    public static RequestBuilder head(String url) {
        return new RequestBuilder(url, "HEAD");
    }

    public static RequestBuilder options(String url) {
        return new RequestBuilder(url, "OPTIONS");
    }

    /**
     * 创建新的配置构建器
     */
    public static Config config() {
        return new Config();
    }

    /**
     * 执行HTTP请求
     * @param builder 请求构建器
     * @return 响应体字符串
     */
    private static String executeRequest(RequestBuilder builder) {
        Config config = builder.config != null ? builder.config : new Config();
        HttpRequest request = createRequest(builder, config);

        try {
            if (builder.sslConfig != null) {
                configureSSL(request, builder.sslConfig);
            }

            return executeWithRetry(request, config);
        } catch (Exception e) {
            handleException(e, builder.url, builder.method, config);
            return null; // 由于异常处理，这行代码永远不会被执行
        } finally {
            if (config.useConnectionPool) {
                connectionManager.releaseConnection(request);
            }
        }
    }

    /**
     * 执行HTTP请求并将响应转换为指定类型
     * @param builder 请求构建器
     * @param responseType 要转换的类型
     * @return 包含转换后响应的HTTP结果
     */
    private static <T> HttpResult<T> executeRequest(RequestBuilder builder, Class<T> responseType) {
        Config config = builder.config != null ? builder.config : new Config();
        HttpRequest request = createRequest(builder, config);

        long startTime = System.currentTimeMillis();
        try {
            if (builder.sslConfig != null) {
                configureSSL(request, builder.sslConfig);
            }

            HttpResponse response = executeRequestWithRetry(request, config);
            String responseBody = response.body();
            T convertedBody = null;

            if (responseType == String.class) {
                convertedBody = (T) responseBody;
            } else if (responseBody != null && !responseBody.isEmpty()) {
                convertedBody = JSONUtil.toBean(responseBody, responseType);
            }

            long requestTime = System.currentTimeMillis() - startTime;
            if (config.metricsCollector != null) {
                config.metricsCollector.recordRequestComplete(
                        builder.method, builder.url, response.getStatus(), requestTime);
            }

            return new HttpResult<>(
                    response.getStatus(),
                    response.headers(),
                    convertedBody,
                    responseBody,
                    requestTime
            );
        } catch (Exception e) {
            if (config.metricsCollector != null) {
                config.metricsCollector.recordRequestFailure(builder.method, builder.url, e);
            }
            handleException(e, builder.url, builder.method, config);
            return null; // 由于异常处理，这行代码永远不会被执行
        } finally {
            if (config.useConnectionPool) {
                connectionManager.releaseConnection(request);
            }
        }
    }

    /**
     * 从构建器创建HTTP请求
     */
    private static HttpRequest createRequest(RequestBuilder builder, Config config) {
        String url = builder.url;

        // 添加查询参数
        if (!builder.queryParams.isEmpty()) {
            StringBuilder sb = new StringBuilder(url);
            if (!url.contains("?")) {
                sb.append("?");
            } else if (!url.endsWith("&") && !url.endsWith("?")) {
                sb.append("&");
            }

            boolean first = true;
            for (Map.Entry<String, String> entry : builder.queryParams.entrySet()) {
                if (!first) {
                    sb.append("&");
                }
                sb.append(entry.getKey()).append("=").append(entry.getValue());
                first = false;
            }

            url = sb.toString();
        }

        HttpRequest request;
        switch (builder.method) {
            case "GET":
                request = HttpRequest.get(url);
                break;
            case "POST":
                request = HttpRequest.post(url);
                break;
            case "PUT":
                request = HttpRequest.put(url);
                break;
            case "DELETE":
                request = HttpRequest.delete(url);
                break;
            case "PATCH":
                // 使用POST和X-HTTP-Method-Override头，因为Hutool不直接支持PATCH
                request = HttpRequest.post(url).header("X-HTTP-Method-Override", "PATCH");
                break;
            case "HEAD":
                request = HttpRequest.head(url);
                break;
            case "OPTIONS":
                request = HttpRequest.options(url);
                break;
            default:
                throw new IllegalArgumentException("不支持的HTTP方法: " + builder.method);
        }

        // 根据配置设置内容类型
        String contentTypeValue = config.contentType.getValue();
        request.contentType(contentTypeValue);

        // 设置默认请求头
        for (Map.Entry<String, String> entry : config.defaultHeaders.entrySet()) {
            request.header(entry.getKey(), entry.getValue());
        }

        // 设置自定义请求头
        for (Map.Entry<String, String> entry : builder.headers.entrySet()) {
            request.header(entry.getKey(), entry.getValue());
        }

        // 设置超时
        request.timeout(config.timeout);

        // 为支持的方法设置请求体
        if (builder.body != null && (builder.method.equals("POST") || builder.method.equals("PUT")
                || builder.method.equals("PATCH"))) {
            if (builder.body instanceof String) {
                request.body((String) builder.body);
            } else if (contentTypeValue.contains("json")) {
                request.body(JSONUtil.toJsonStr(builder.body));
            } else {
                // 处理其他内容类型
                request.body(builder.body.toString());
            }
        }

        // 为multipart请求添加文件上传
        if (contentTypeValue.equals(ContentType.MULTIPART.getValue()) && !builder.fileParams.isEmpty()) {
            for (Map.Entry<String, File> entry : builder.fileParams.entrySet()) {
                request.form(entry.getKey(), entry.getValue());
            }
        }

        // 应用请求拦截器
        for (Consumer<HttpRequest> interceptor : config.requestInterceptors) {
            interceptor.accept(request);
        }

        if (config.metricsCollector != null) {
            config.metricsCollector.recordRequestStart(builder.method, builder.url);
        }

        return request;
    }

    /**
     * 为请求配置SSL
     */
    private static void configureSSL(HttpRequest request, SSLConfig sslConfig) {
        try {
            // 创建SSL上下文
            SSLContext sslContext = SSLContext.getInstance("TLS");

            // 处理密钥库
            KeyStore keyStore;
            if (sslConfig.keyStore != null) {
                keyStore = sslConfig.keyStore;
            } else {
                keyStore = KeyStore.getInstance(sslConfig.keyStoreType);
                try (InputStream is = new FileInputStream(sslConfig.keyStorePath)) {
                    keyStore.load(is, sslConfig.keyStorePassword.toCharArray());
                }
            }

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, sslConfig.keyStorePassword.toCharArray());

            // 处理信任库
            KeyStore trustStore;
            if (sslConfig.trustStore != null) {
                trustStore = sslConfig.trustStore;
            } else {
                trustStore = KeyStore.getInstance(sslConfig.trustStoreType);
                try (InputStream is = new FileInputStream(sslConfig.trustStorePath)) {
                    trustStore.load(is, sslConfig.trustStorePassword.toCharArray());
                }
            }

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            // 初始化SSL上下文
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            // 设置SSL套接字工厂
            request.setSSLSocketFactory(sslSocketFactory);
        } catch (Exception e) {
            logger.error("设置SSL配置时出错", e);
            throw new HttpClientException("设置SSL配置失败", e);
        }
    }

    /**
     * 执行带重试逻辑的HTTP请求
     */
    private static String executeWithRetry(HttpRequest request, Config config) {
        HttpResponse response = executeRequestWithRetry(request, config);
        return response.body();
    }

    /**
     * 执行带重试逻辑的HTTP请求并返回HttpResponse
     */
    private static HttpResponse executeRequestWithRetry(HttpRequest request, Config config) {
        RetryConfig retryConfig = config.retryConfig;
        int retryCount = 0;

        while (true) {
            try {
                HttpResponse response = request.execute();

                // 应用响应拦截器
                for (Function<HttpResponse, HttpResponse> interceptor : config.responseInterceptors) {
                    response = interceptor.apply(response);
                }

                int statusCode = response.getStatus();

                // 检查是否需要基于状态码重试
                if (statusCode >= 200 && statusCode < 300 ||
                        !retryConfig.statusCodeRetryPredicate.test(statusCode) ||
                        retryCount >= retryConfig.maxRetries) {

                    // 如果不是成功且不重试，抛出异常
                    if (!(statusCode >= 200 && statusCode < 300)) {
                        throw new HttpResponseException(statusCode, response.body());
                    }

                    return response;
                }

                // 需要重试
                retryCount++;

                // 如果提供了回调，通知回调
                if (retryConfig.retryCallback != null) {
                    retryConfig.retryCallback.accept(retryCount);
                }

                long delay = retryConfig.getDelayForAttempt(retryCount);
                TimeUnit.MILLISECONDS.sleep(delay);

            } catch (Exception e) {
                // 检查是否应该重试此异常
                if (retryCount >= retryConfig.maxRetries || !retryConfig.retryPredicate.test(e)) {
                    handleException(e, request.getUrl(), request.getMethod().toString(), config);
                }

                retryCount++;

                // 如果提供了回调，通知回调
                if (retryConfig.retryCallback != null) {
                    retryConfig.retryCallback.accept(retryCount);
                }

                try {
                    long delay = retryConfig.getDelayForAttempt(retryCount);
                    TimeUnit.MILLISECONDS.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new HttpClientException("请求被中断", ie);
                }
            }
        }
    }

    /**
     * 处理HTTP请求异常并转换为适当的异常类型
     */
    private static void handleException(Exception e, String url, String method, Config config) {
        if (e instanceof HttpClientException) {
            throw (HttpClientException) e;
        } else if (e instanceof java.net.SocketTimeoutException) {
            throw new HttpTimeoutException("请求超时: " + url, e);
        } else if (e instanceof java.net.ConnectException || e instanceof java.net.UnknownHostException) {
            throw new HttpConnectionException("连接失败: " + url, e);
        } else {
            throw new HttpClientException("执行HTTP请求失败: " + url, e);
        }
    }

    /**
     * 关闭执行器服务
     */
    public static void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 便捷方法：发送GET请求并返回字符串响应
     */
    public static String getAsString(String url) {
        return get(url).execute();
    }

    /**
     * 便捷方法：发送GET请求并返回指定类型的响应
     */
    public static <T> HttpResult<T> getAsObject(String url, Class<T> responseType) {
        return get(url).execute(responseType);
    }

    /**
     * 便捷方法：发送POST请求并返回字符串响应
     */
    public static String postAsString(String url, Object body) {
        return post(url).body(body).execute();
    }

    /**
     * 便捷方法：发送POST请求并返回指定类型的响应
     */
    public static <T> HttpResult<T> postAsObject(String url, Object body, Class<T> responseType) {
        return post(url).body(body).execute(responseType);
    }

    /**
     * 便捷方法：发送PUT请求并返回字符串响应
     */
    public static String putAsString(String url, Object body) {
        return put(url).body(body).execute();
    }

    /**
     * 便捷方法：发送PUT请求并返回指定类型的响应
     */
    public static <T> HttpResult<T> putAsObject(String url, Object body, Class<T> responseType) {
        return put(url).body(body).execute(responseType);
    }

    /**
     * 便捷方法：发送DELETE请求并返回字符串响应
     */
    public static String deleteAsString(String url) {
        return delete(url).execute();
    }

    /**
     * 便捷方法：发送DELETE请求并返回指定类型的响应
     */
    public static <T> HttpResult<T> deleteAsObject(String url, Class<T> responseType) {
        return delete(url).execute(responseType);
    }
}