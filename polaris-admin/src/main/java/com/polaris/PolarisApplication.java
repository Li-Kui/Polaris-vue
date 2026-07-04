package com.polaris;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

/**
 * 启动程序
 * 
 * @author polaris
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class PolarisApplication
{
    public static void main(String[] args)
    {
        // System.setProperty("spring.devtools.restart.enabled", "false");
        SpringApplication.run(PolarisApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  北辰启动成功   ლ(´ڡ`ლ)ﾞ  \n" +
                "    ____        __           _\n" +
                "   / __ \\____  / /___ ______(_)____\n" +
                "  / /_/ / __ \\/ / __ `/ ___/ / ___/\n" +
                " / ____/ /_/ / / /_/ / /  / (__  )\n" +
                "/_/    \\____/_/\\__,_/_/  /_/____/ ");
    }
}
