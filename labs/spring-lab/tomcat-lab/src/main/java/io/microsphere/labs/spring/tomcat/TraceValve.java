package io.microsphere.labs.spring.tomcat;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

import jakarta.servlet.ServletException;
import java.io.IOException;

public class TraceValve extends ValveBase {

    @Override
    public void invoke(Request request, Response response)
            throws IOException, ServletException {
        response.addHeader("X-Tomcat-Valve", "trace");
        getNext().invoke(request, response);
    }
}
