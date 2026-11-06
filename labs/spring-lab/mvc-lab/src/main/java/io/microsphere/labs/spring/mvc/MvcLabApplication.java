package io.microsphere.labs.spring.mvc;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

import java.nio.file.Files;
import java.nio.file.Path;

public final class MvcLabApplication {

    public static void main(String[] args) throws Exception {
        Tomcat tomcat = new Tomcat();
        Path baseDir = Files.createTempDirectory("spring-mvc-lab");
        Path webapps = Files.createDirectory(baseDir.resolve("webapps"));
        tomcat.setBaseDir(baseDir.toString());
        tomcat.setPort(0);
        tomcat.getConnector();

        Context context = tomcat.addContext("", webapps.toString());
        AnnotationConfigWebApplicationContext spring =
                new AnnotationConfigWebApplicationContext();
        spring.register(WebConfig.class);

        Tomcat.addServlet(context, "dispatcher", new DispatcherServlet(spring));
        context.addServletMappingDecoded("/*", "dispatcher");

        tomcat.start();
        System.out.println("PORT=" + tomcat.getConnector().getLocalPort());
        System.out.flush();

        tomcat.getServer().await();
    }
}
