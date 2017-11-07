package com.ociweb.greenspring.annotation;

import org.springframework.web.bind.annotation.RequestMapping;

// TODO: turn into annotation
// TODO split into service and code-gen responsibilties
public class CreateGreenSpringAppConfig {
    private final String indent = "    ";
    private final String subPackage =  "";
    private final String appName =  "GreenSpringApp";
    private final int port =  80;

    public String getIndent() {
        return indent;
        }

    public String getSubPackage() {
        return subPackage;
        }

    public String getAppName() {
        return appName;
    }

    public int getPort() {
        return port;
        }

    public static String normalizedRoute(RequestMapping mapping) {
        String[] routeStrs = mapping.value();
        if (routeStrs.length > 0) {
            String routeStr = routeStrs[0];
            if (routeStr.length() > 1) {
                if (routeStr.charAt(routeStr.length() - 1) == '/') {
                    routeStr = routeStr.substring(0, routeStr.length()-1);
                }
            }
            if (routeStr.charAt(0) != '/') {
                routeStr = '/' + routeStr;
            }
            return routeStr;
        }
        return "/";
    }
}
