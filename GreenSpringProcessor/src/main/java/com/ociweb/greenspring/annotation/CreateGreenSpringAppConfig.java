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
}
