package io.microsphere.labs.seata.at.account.config;

import io.seata.core.context.RootContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SeataXidFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String xid = request.getHeader(RootContext.KEY_XID);
        boolean bound = xid != null && !xid.isEmpty();
        if (bound) {
            RootContext.bind(xid);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (bound) {
                RootContext.unbind();
            }
        }
    }
}
