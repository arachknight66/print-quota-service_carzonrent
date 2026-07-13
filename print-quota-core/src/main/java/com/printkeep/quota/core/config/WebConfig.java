package com.printkeep.quota.core.config;

import com.printkeep.quota.core.interceptor.MdcInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration class to register web MVC interceptors.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final MdcInterceptor mdcInterceptor;

    public WebConfig(final MdcInterceptor mdcInterceptor) {
        this.mdcInterceptor = mdcInterceptor;
    }

    @Override
    public void addInterceptors(final InterceptorRegistry registry) {
        registry.addInterceptor(mdcInterceptor);
    }
}
