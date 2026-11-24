package pl.demo.ice.web;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.net.URLEncoder;

public class RootFlowEntryFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) { }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        // Cookie-only session tracking: ICEfaces endpoint matching rejects ;jsessionid.
        response = new HttpServletResponseWrapper((HttpServletResponse) response) {
            @Override public String encodeURL(String url) { return url; }
            @Override public String encodeRedirectURL(String url) { return url; }
        };
        String path = httpRequest.getRequestURI().substring(httpRequest.getContextPath().length());
        if ("/".equals(path)) {
            String viewNumber = httpRequest.getParameter("rvn");
            String viewKey = RootFlowEntryFilter.class.getName() + ".reloadView";
            if (viewNumber != null && viewNumber.matches("[0-9]+")) {
                httpRequest.getSession().setAttribute(viewKey, viewNumber);
                ((HttpServletResponse) response).sendRedirect(httpRequest.getContextPath() + "/");
                return;
            }
            String query = httpRequest.getQueryString();
            Object executionKey = httpRequest.getSession().getAttribute(SessionFlowPhaseListener.EXECUTION_KEY);
            if (query == null && "GET".equalsIgnoreCase(httpRequest.getMethod()) && executionKey instanceof String) {
                query = "_flowExecutionKey=" + URLEncoder.encode((String) executionKey, "UTF-8");
                httpRequest.setAttribute(SessionFlowPhaseListener.class.getName() + ".refresh", Boolean.TRUE);
            }
            String target = "/login.iface" + (query == null ? "?_flowId=main-flow" : "?" + query);
            Object reloadView = httpRequest.getSession().getAttribute(viewKey);
            if (reloadView != null) {
                httpRequest.getSession().removeAttribute(viewKey);
                target += "&rvn=" + reloadView;
            }
            httpRequest.getRequestDispatcher(target).forward(request, response);
            return;
        }
        int pathParameter = path.indexOf(';');
        String viewPath = pathParameter < 0 ? path : path.substring(0, pathParameter);
        if (viewPath.endsWith(".xhtml")) {
            String target = httpRequest.getContextPath() + "/";
            String query = httpRequest.getQueryString();
            if (query != null) target += "?" + query;
            ((javax.servlet.http.HttpServletResponse) response).sendRedirect(
                    ((javax.servlet.http.HttpServletResponse) response).encodeRedirectURL(target));
            return;
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() { }
}
