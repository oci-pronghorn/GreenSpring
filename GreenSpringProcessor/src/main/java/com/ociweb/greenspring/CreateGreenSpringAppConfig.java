package com.ociweb.greenspring;

public class CreateGreenSpringAppConfig {
    private final String indent = "    ";
    private final String appName =  "GreenSpringApp";
    private final String subPackage =  "";
    private final int port =  80;
    private final boolean parallelBehaviors =  false;
    private final boolean parallelRoutes =  false;
    private final GreenServiceScope serviceScope =  GreenServiceScope.behavior;

    public String getIndent() {
        return indent;
        }

    public String getAppName() {
        return appName;
        }

    public String getSubPackage() {
        return subPackage;
        }

    public int getPort() {
        return port;
        }

    public boolean isParallelBehaviors() {
        return parallelBehaviors;
        }

    public boolean isParallelRoutes() {
        return parallelRoutes;
        }

    public GreenServiceScope getServiceScope() {
        return serviceScope;
        }
}
