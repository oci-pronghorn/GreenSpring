package com.ociweb.greenspring.annotations;

import com.ociweb.greenspring.GreenServiceScope;

public @interface GreenParallelism {
    boolean parallelBehavior() default false;
    boolean parallelRoutes() default false;
    GreenServiceScope serviceScope() default GreenServiceScope.behavior;
}
