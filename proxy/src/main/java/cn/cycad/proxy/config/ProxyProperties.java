package cn.cycad.proxy.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

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