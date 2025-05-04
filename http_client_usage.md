# HttpClientUtil 使用示例

`HttpClientUtil` 是一个功能强大的HTTP客户端工具类，采用流式API设计模式，支持同步和异步请求、SSL配置、重试策略、连接池管理以及请求/响应拦截器，这些特性使其非常灵活且易于使用。

## 目录

- [基本使用示例](#基本使用示例)
- [使用自定义配置](#使用自定义配置)
- [处理文件上传](#处理文件上传)
- [异步请求](#异步请求)
- [配置SSL](#配置ssl)
- [使用拦截器](#使用拦截器)
- [设置异常处理](#设置异常处理)
- [使用指标收集](#使用指标收集)
- [综合示例：REST API调用](#综合示例rest-api调用)
- [使用便捷方法](#使用便捷方法)

## 基本使用示例

```java
// 简单的GET请求
String response = HttpClientUtil.get("https://api.example.com/users").execute();
System.out.println("响应内容: " + response);

// 带查询参数的GET请求
String userResponse = HttpClientUtil.get("https://api.example.com/users")
    .queryParam("id", "123")
    .queryParam("include", "profile")
    .execute();
System.out.println("用户数据: " + userResponse);

// 发送POST请求并解析JSON响应
User user = new User("zhang san", "zhangsan@example.com");
HttpResult<LoginResponse> result = HttpClientUtil.post("https://api.example.com/login")
    .body(user)
    .execute(LoginResponse.class);

if (result.isSuccess()) {
    System.out.println("登录成功, Token: " + result.getBody().getToken());
} else {
    System.out.println("登录失败, 状态码: " + result.getStatusCode());
}
```

## 使用自定义配置

```java
// 创建自定义配置
Config config = HttpClientUtil.config()
    .timeout(5000)  // 5秒超时
    .contentType(ContentType.JSON)
    .retryPolicy(new RetryConfig()
        .maxRetries(3)
        .retryDelay(2000)
        .exponentialBackoff(true)
        .retryCallback(attempt -> System.out.println("重试中... 第" + attempt + "次"))
    )
    .defaultHeader("Authorization", "Bearer " + apiToken);

// 使用配置发送请求
String result = HttpClientUtil.post("https://api.example.com/data")
    .config(config)
    .body(requestData)
    .execute();
```

## 处理文件上传

```java
// 上传文件
File document = new File("/path/to/document.pdf");
String response = HttpClientUtil.post("https://api.example.com/upload")
    .config(HttpClientUtil.config().contentType(ContentType.MULTIPART))
    .file("document", document)
    .queryParam("documentType", "pdf")
    .execute();
```

## 异步请求

```java
// 异步GET请求
CompletableFuture<String> future = HttpClientUtil.get("https://api.example.com/data")
    .executeAsync();

// 添加回调处理
future.thenAccept(response -> {
    System.out.println("异步请求完成, 响应: " + response);
}).exceptionally(ex -> {
    System.err.println("请求失败: " + ex.getMessage());
    return null;
});

// 异步POST请求并解析为对象
CompletableFuture<HttpResult<ApiResponse>> futureResult = 
    HttpClientUtil.post("https://api.example.com/process")
    .body(requestData)
    .executeAsync(ApiResponse.class);

futureResult.thenAccept(result -> {
    if (result.isSuccess()) {
        System.out.println("处理成功: " + result.getBody().getMessage());
    }
});
```

## 配置SSL

```java
// 创建SSL配置
SSLConfig sslConfig = SSLConfig.builder()
    .keyStore("/path/to/keystore.jks", "keystorePassword")
    .trustStore("/path/to/truststore.jks", "truststorePassword")
    .build();

// 发送带SSL的请求
String response = HttpClientUtil.get("https://secure-api.example.com/data")
    .ssl(sslConfig)
    .execute();
```

## 使用拦截器

```java
// 创建带请求和响应拦截器的配置
Config config = HttpClientUtil.config()
    // 请求拦截器 - 添加时间戳
    .addRequestInterceptor(request -> {
        request.header("X-Timestamp", String.valueOf(System.currentTimeMillis()));
    })
    // 响应拦截器 - 记录响应头
    .addResponseInterceptor(response -> {
        System.out.println("响应状态码: " + response.getStatus());
        System.out.println("响应内容类型: " + response.header("Content-Type"));
        return response;
    });

// 使用带拦截器的配置发送请求
HttpResult<DataResponse> result = HttpClientUtil.get("https://api.example.com/data")
    .config(config)
    .execute(DataResponse.class);
```

## 设置异常处理

```java
try {
    String response = HttpClientUtil.get("https://api.example.com/resource")
        .queryParam("id", "invalid-id")
        .execute();
    
    // 处理成功响应
    System.out.println("成功: " + response);
    
} catch (HttpClientUtil.HttpResponseException e) {
    // 处理HTTP错误响应
    System.err.println("HTTP错误: " + e.getStatusCode());
    System.err.println("错误响应: " + e.getResponseBody());
    
} catch (HttpClientUtil.HttpTimeoutException e) {
    // 处理超时异常
    System.err.println("请求超时: " + e.getMessage());
    
} catch (HttpClientUtil.HttpConnectionException e) {
    // 处理连接异常
    System.err.println("连接错误: " + e.getMessage());
    
} catch (HttpClientUtil.HttpClientException e) {
    // 处理其他HTTP客户端异常
    System.err.println("HTTP客户端错误: " + e.getMessage());
}
```

## 使用指标收集

```java
// 实现指标收集接口
class SimpleMetricsCollector implements HttpClientUtil.MetricsCollector {
    @Override
    public void recordRequestStart(String method, String url) {
        System.out.println("开始请求: " + method + " " + url);
    }
    
    @Override
    public void recordRequestComplete(String method, String url, int statusCode, long duration) {
        System.out.println("完成请求: " + method + " " + url + 
                           ", 状态码: " + statusCode + 
                           ", 耗时: " + duration + "ms");
    }
    
    @Override
    public void recordRequestFailure(String method, String url, Throwable error) {
        System.err.println("请求失败: " + method + " " + url + 
                           ", 错误: " + error.getMessage());
    }
}

// 使用指标收集器
Config config = HttpClientUtil.config()
    .metricsCollector(new SimpleMetricsCollector());

// 发送请求
HttpClientUtil.get("https://api.example.com/data")
    .config(config)
    .execute();
```

## 综合示例：REST API调用

```java
// 定义响应类
class UserResponse {
    private long id;
    private String name;
    private String email;
    
    // getters and setters
}

// 创建配置
Config apiConfig = HttpClientUtil.config()
    .timeout(10000)
    .defaultHeader("Authorization", "Bearer " + apiToken)
    .defaultHeader("Accept", "application/json")
    .retryPolicy(new RetryConfig()
        .maxRetries(2)
        .exponentialBackoff(true)
        .retryOnStatusCodes(code -> code == 429 || code >= 500)
    );

// 获取用户列表
HttpResult<List<UserResponse>> userListResult = HttpClientUtil.get("https://api.example.com/users")
    .config(apiConfig)
    .queryParam("page", "1")
    .queryParam("size", "10")
    .execute(new TypeReference<List<UserResponse>>() {}.getType());

if (userListResult.isSuccess()) {
    List<UserResponse> users = userListResult.getBody();
    users.forEach(user -> {
        System.out.println("用户ID: " + user.getId() + ", 名称: " + user.getName());
    });
}

// 创建新用户
Map<String, Object> newUser = new HashMap<>();
newUser.put("name", "李四");
newUser.put("email", "lisi@example.com");
newUser.put("role", "user");

HttpResult<UserResponse> createResult = HttpClientUtil.post("https://api.example.com/users")
    .config(apiConfig)
    .body(newUser)
    .execute(UserResponse.class);

if (createResult.isSuccess()) {
    System.out.println("创建成功, 新用户ID: " + createResult.getBody().getId());
} else {
    System.err.println("创建失败, 状态码: " + createResult.getStatusCode());
}

// 异步更新用户
Map<String, Object> updateData = new HashMap<>();
updateData.put("name", "李四 (已更新)");

CompletableFuture<HttpResult<UserResponse>> updateFuture = 
    HttpClientUtil.put("https://api.example.com/users/101")
    .config(apiConfig)
    .body(updateData)
    .executeAsync(UserResponse.class);

updateFuture.thenAccept(result -> {
    if (result.isSuccess()) {
        System.out.println("更新成功: " + result.getBody().getName());
    } else {
        System.err.println("更新失败: " + result.getStatusCode());
    }
});
```

## 使用便捷方法

```java
// 使用便捷方法发送请求
String userData = HttpClientUtil.getAsString("https://api.example.com/users/123");

UserResponse user = HttpClientUtil.getAsObject("https://api.example.com/users/123", UserResponse.class).getBody();

Map<String, Object> updateData = new HashMap<>();
updateData.put("status", "active");
String updateResponse = HttpClientUtil.putAsString("https://api.example.com/users/123", updateData);

boolean deleted = HttpClientUtil.deleteAsString("https://api.example.com/users/123").contains("success");
```

## 最佳实践

1. **使用合适的超时设置**：根据API响应时间调整超时设置，避免过长等待或过早超时。

2. **利用重试策略**：对于不稳定的网络环境，配置合适的重试策略可以提高请求成功率。

3. **处理异常**：总是捕获并适当处理HTTP异常，区分不同类型的异常（连接问题、超时、服务器错误等）。

4. **关闭连接**：长时间运行的应用程序应在适当的时候调用`HttpClientUtil.shutdown()`释放资源。

5. **使用连接池**：对于高频请求，启用连接池可以显著提高性能。

6. **使用异步请求**：对于不需要立即响应的操作，使用异步请求可以提高应用程序的响应性。

7. **添加日志和指标**：在生产环境中，配置指标收集器来监控HTTP请求的性能和成功率。