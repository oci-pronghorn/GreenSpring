package com.ociweb.greenspring.adaptors;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import javax.lang.model.element.VariableElement;

public class GreenMethod {

    public boolean isParamInRoute(VariableElement param) {
        PathVariable routeParam = param.getAnnotation(PathVariable.class);
        if (routeParam != null) {
            return true;
        }
        return false;
    }

    public boolean isParamRequestBody(VariableElement param) {
        RequestBody requestBodyParam = param.getAnnotation(RequestBody.class);
        if (requestBodyParam != null) {
            return true;
        }
        return false;
    }
}
