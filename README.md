## proxy-spring-boot-starter

最近遇到一个问题，在内网环境中部署的项目需要调用外网完成一些应用，一般情况我们可以通过增加一台机器，部署到可以访问外网的服务器上，然后内网直接连接该机器通过Nginx进行代理即可。但是出于安全考虑以及各个服务都是由多个微服务组成，需要接入SSO实现认证后才能访问


### 实现过程

1. 定义一个配置文件，后面可以在`application.yml`中通过配置实现代理不同网站
```java
@Getter
@Setter
@ConfigurationProperties(prefix = "proxy")
public class ProxyProperties {

    /**
     * 需要代理的服务列表
     */
    private Map<String,Server> servers;

    /**
     *
     */
    @Getter@Setter
    public static class Server{

        private String path;

        private String target;

        private String name;

    }

}
```
2. 引入Spring依赖，在编写配置时可以自动提示
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure-processor</artifactId>
    <optional>true</optional>
</dependency> 
```

3. 该功能实现的主要依赖
```xml
<dependency>
    <groupId>org.mitre.dsmiley.httpproxy</groupId>
    <artifactId>smiley-http-proxy-servlet</artifactId>
    <version>2.0</version>
</dependency> 
```
4. 增加AutoConfiguration，包含两个步骤

 a) 定义一个ProxyServletConfiguration配置
这里我们基于`ImportBeanDefinitionRegistrar`接口，动态读取代理服务列表，然后通过`ServletRegistrationBean`创建Servlet代码
```java
    public static class ProxyServleImportBeanDefinitionRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

        private ProxyProperties properties;

        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
            Map<String, ProxyProperties.Server> servers = properties.getServers();
            if( !CollectionUtils.isEmpty( servers ) ){
                for(Map.Entry<String, ProxyProperties.Server> entry : servers.entrySet()){
                    ProxyProperties.Server server = entry.getValue();
                    LOGGER.info("开始注册服务代理：{} ( {} => {})", server.getName(), server.getPath(), server.getTarget());
                    BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(ServletRegistrationBean.class);
//                    builder.addConstructorArgValue(new ProxyServlet() ).addConstructorArgValue(server.getPath());
                    builder.setFactoryMethodOnBean("getServletRegistrationBean", "proxyServletFactory");
                    builder.addConstructorArgValue(entry.getKey()).addConstructorArgValue(entry.getValue());
                    registry.registerBeanDefinition(entry.getKey()+"ServletRegistrationBean", builder.getBeanDefinition());
                }
            }
        }

        @Override
        public void setEnvironment(Environment environment) {
            String prefix = Objects.requireNonNull(AnnotationUtils.getAnnotation(ProxyProperties.class, ConfigurationProperties.class)).prefix();
            properties = Binder.get(environment).bind(prefix, ProxyProperties.class).get();
        }

    }
```

```java
    public static ServletRegistrationBean createServletRegistrationBean(String key, ProxyProperties.Server server){
        ServletRegistrationBean servletRegistrationBean = new ServletRegistrationBean(new ProxyServlet(), server.getPath());
        servletRegistrationBean.setName(server.getName());
        servletRegistrationBean.addInitParameter(ProxyServlet.P_TARGET_URI, server.getTarget());
        servletRegistrationBean.addInitParameter(ProxyServlet.P_LOG, String.valueOf(true));
        // 自动处理重定向
//        servletRegistrationBean.addInitParameter(ProxyServlet.P_HANDLEREDIRECTS, String.valueOf(false));
//        // 保持 COOKIES 不变
        servletRegistrationBean.addInitParameter(ProxyServlet.P_PRESERVECOOKIES, String.valueOf(true));
//        // Set-Cookie 服务器响应标头中保持 cookie 路径不变
        servletRegistrationBean.addInitParameter(ProxyServlet.P_PRESERVECOOKIEPATH, String.valueOf(true));
//        // 保持 HOST 参数不变
//        servletRegistrationBean.addInitParameter(ProxyServlet.P_PRESERVEHOST, String.valueOf(true));
        return servletRegistrationBean;
    }
```

b) 通过在`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`中引入上面的配置，这样其他模块只需要引入该jar则会由Spring自动注入 Configuration
```
cn.cycad.proxy.config.ProxyServletConfiguration
```

### 使用方法

使用方式非常简单，在配置文件中添加代理的配置，启动服务即可

1. 修改配置文件application.yml
```yml
proxy:
  servers:
    Baidu:
      path: /baidu/*
      target: 'https://www.baidu.com'
      name: 百度
    Shici:
      path: /shici/*
      target: 'https://v1.jinrishici.com'
      name: 诗词
```

2. 启动服务


3. 示例
   http://localhost:8080/shici

可以看到返回的内容与`https://v1.jinrishici.com`相同
```json
{
  "welcome": "欢迎使用古诗词·一言",
  "api-document": "下面为本API可用的所有类型，使用时，在链接最后面加上 .svg / .txt / .json / .png 可以获得不同格式的输出",
  "help": "具体安装方法请访问项目首页 https://gushi.ci/",
  "list": [
    {
      "全部": "https://v1.jinrishici.com/all"
    },
    {
      "抒情": "https://v1.jinrishici.com/shuqing"
    },
    {
      "四季": "https://v1.jinrishici.com/siji"
    },
    {
      "山水": "https://v1.jinrishici.com/shanshui"
    },
    {
      "天气": "https://v1.jinrishici.com/tianqi"
    }
  ]
}
```


### 优缺点

+ 通过该方式实现首先需要有一个可以访问外网的服务器，同时该服务器和内网环境互通
+ 如果需要添加认证模块，直接引入即可
+ 如果代理的网站需要更多的信息才能访问，则需要进一步扩展

### 扩展

