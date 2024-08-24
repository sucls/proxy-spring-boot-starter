package cn.cycad.proxy.config;

import org.mitre.dsmiley.httpproxy.ProxyServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.CollectionUtils;

import java.util.Map;
import java.util.Objects;


//@ConditionalOnProperty("proxy.servers")
@Configuration
@EnableConfigurationProperties({ProxyProperties.class})
@Import({ProxyServletConfiguration.ProxyServleImportBeanDefinitionRegistrar.class})
public class ProxyServletConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyServletConfiguration.class);

    private final ProxyProperties properties;

    public ProxyServletConfiguration(ProxyProperties properties) {
        this.properties = properties;
    }


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

    @Bean
    public ProxyServletFactory proxyServletFactory(){
        return new ProxyServletFactory();
    }

    /**
     * 代理工厂
     */
    public static class ProxyServletFactory{

        public ServletRegistrationBean getServletRegistrationBean(String key, ProxyProperties.Server server) {
            return createServletRegistrationBean(key, server);
        }

    }

    /**
     *
     */
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

}
