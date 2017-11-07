package com.ociweb.greenspring.annotations;

public @interface GreenParallelism {
    boolean parallelBehaviorDefault = false;
    boolean parallelRoutesDefault = false;
    int serviceScopeDefault = GreenServiceScope.behavior;

    boolean parallelBehavior() default parallelBehaviorDefault;
    boolean parallelRoutes() default parallelRoutesDefault;
    int serviceScope() default serviceScopeDefault;
}
