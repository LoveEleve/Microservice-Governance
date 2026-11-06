package io.microsphere.labs.spring.tomcat;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;

import java.nio.file.Files;
import java.nio.file.Path;

public final class TomcatLabApplication {

    public static void main(String[] args) throws Exception {
        Tomcat tomcat = new Tomcat();
        Path baseDir = Files.createTempDirectory("tomcat-lab");
        Path webapps = Files.createDirectory(baseDir.resolve("webapps"));
        tomcat.setBaseDir(baseDir.toString());
        tomcat.setPort(0);
        tomcat.getConnector();

        Context context = tomcat.addContext("", webapps.toString());
        context.getPipeline().addValve(new TraceValve());
        Tomcat.addServlet(context, "hello", new HelloServlet());
        context.addServletMappingDecoded("/hello", "hello");

        tomcat.start();
        System.out.println("PORT=" + tomcat.getConnector().getLocalPort());
        System.out.flush();

        tomcat.getServer().await();
    }
}
