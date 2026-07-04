package com.polaris.web.core.config;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;
import com.polaris.common.annotation.ApiGroup;
import com.polaris.common.constant.ApiVersionConstants;

/**
 * 基于 @ApiGroup 注解动态获取所有版本号并动态注册 Knife4j/Springdoc 分组
 *
 * @author polaris
 */
@Configuration("apiGroupConfig")
public class ApiGroupConfig
{
    @Bean
    public static BeanDefinitionRegistryPostProcessor apiGroupRegistryPostProcessor()
    {
        return new BeanDefinitionRegistryPostProcessor()
        {
            @Override
            public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException
            {
                Set<String> versions = new TreeSet<>();
                String[] beanNames = registry.getBeanDefinitionNames();

                for (String beanName : beanNames)
                {
                    BeanDefinition bd = registry.getBeanDefinition(beanName);
                    if (bd instanceof AnnotatedBeanDefinition)
                    {
                        AnnotatedBeanDefinition abd = (AnnotatedBeanDefinition) bd;
                        AnnotationMetadata metadata = abd.getMetadata();
                        String className = metadata.getClassName();
                        if (className != null && className.startsWith("com.polaris"))
                        {
                            if (metadata.hasAnnotation(RestController.class.getName())
                                || metadata.hasAnnotation(Controller.class.getName()))
                            {
                                // 类级别版本注解扫描
                                Map<String, Object> classAttrs = metadata.getAnnotationAttributes(ApiGroup.class.getName());
                                if (classAttrs != null && classAttrs.containsKey("value"))
                                {
                                    versions.add((String) classAttrs.get("value"));
                                }
                                // 方法级别版本注解扫描
                                Set<MethodMetadata> annotatedMethods = metadata.getAnnotatedMethods(ApiGroup.class.getName());
                                if (annotatedMethods != null)
                                {
                                    for (MethodMetadata methodMetadata : annotatedMethods)
                                    {
                                        Map<String, Object> methodAttrs = methodMetadata.getAnnotationAttributes(ApiGroup.class.getName());
                                        if (methodAttrs != null && methodAttrs.containsKey("value"))
                                        {
                                            versions.add((String) methodAttrs.get("value"));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 如果没扫到任何版本分组，给一个默认的兜底版本
                if (versions.isEmpty())
                {
                    versions.add(ApiVersionConstants.CURRENT);
                }

                // 动态注册 GroupedOpenApi 的 BeanDefinition，采用经典的静态工厂方法声明并提供显式 targetType 保证注入生效
                for (String version : versions)
                {
                    RootBeanDefinition rbd = new RootBeanDefinition();
                    rbd.setBeanClass(ApiGroupConfig.class); // 包含静态工厂方法的类
                    rbd.setFactoryMethodName("buildGroupedOpenApi");
                    rbd.setTargetType(GroupedOpenApi.class); // 必须设置 targetType，确保 Spring 在自动注入 List<GroupedOpenApi> 时识别此 Bean
                    rbd.getConstructorArgumentValues().addGenericArgumentValue(version); // 传给工厂方法的参数

                    String beanName = "groupedOpenApi_" + version.replace(".", "_");
                    registry.registerBeanDefinition(beanName, rbd);
                }
            }

            @Override
            public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException
            {
            }
        };
    }

    /**
     * 静态工厂方法，供 Spring 实例化 GroupedOpenApi Bean
     */
    public static GroupedOpenApi buildGroupedOpenApi(String version)
    {
        return GroupedOpenApi.builder()
            .group(version)
            .displayName(version)
            .pathsToMatch("/**")
            .addOpenApiMethodFilter(method -> matchesVersion(method, version))
            .build();
    }

    /**
     * 判断接口是否属于指定版本分组
     */
    private static boolean matchesVersion(Method method, String version)
    {
        ApiGroup methodGroup = AnnotatedElementUtils.findMergedAnnotation(method, ApiGroup.class);
        if (methodGroup != null)
        {
            return version.equals(methodGroup.value());
        }
        ApiGroup classGroup = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), ApiGroup.class);
        if (classGroup != null)
        {
            return version.equals(classGroup.value());
        }
        return version.equals(ApiVersionConstants.CURRENT);
    }

    /**
     * 静态声明一个默认分组，用于激活 Springdoc 的多分组支持条件 (MultipleOpenApiSupportCondition)
     * 同时作为无 @ApiGroup 注解接口的兜底归类分组
     */
    @Bean
    public GroupedOpenApi defaultGroupedOpenApi()
    {
        return GroupedOpenApi.builder()
            .group("default")
            .displayName("default")
            .pathsToMatch("/**")
            .addOpenApiMethodFilter(method -> {
                // 只有类级别和方法级别都不包含 @ApiGroup 注解的接口，才归入 default 分组
                return !AnnotatedElementUtils.hasAnnotation(method, ApiGroup.class)
                    && !AnnotatedElementUtils.hasAnnotation(method.getDeclaringClass(), ApiGroup.class);
            })
            .build();
    }
}
