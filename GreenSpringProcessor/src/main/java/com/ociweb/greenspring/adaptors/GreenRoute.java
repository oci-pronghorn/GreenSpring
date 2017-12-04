package com.ociweb.greenspring.adaptors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;

import java.util.List;
import java.util.stream.Collectors;

public class GreenRoute {
    private final String routeStr;
    private final Element element;

    public static List<GreenRoute> fetchControllers(RoundEnvironment roundEnv) {
        return roundEnv.getElementsAnnotatedWith(RequestMapping.class).stream()
                .filter(element -> element.getKind() == ElementKind.CLASS)
                .map(element -> {
                    RequestMapping mapping = element.getAnnotation(RequestMapping.class);
                    return new GreenRoute(mapping, element);
                }
        ).collect(Collectors.toList());
    }

    public static List<GreenRoute> fetchMethods(Element controller) {
        return controller.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .filter(element -> element.getAnnotation(RequestMapping.class) != null)
                .map(element -> {
                    RequestMapping mapping = element.getAnnotation(RequestMapping.class);
                    return new GreenRoute(mapping, element);
                }
        ).collect(Collectors.toList());
    }

    private GreenRoute(RequestMapping springAnnotation, Element element) {
        this.element = element;
        String[] routeStrs = springAnnotation.value();
        if (routeStrs.length > 0) {
            this.routeStr = routeStrs[0];
        }
        else {
            this.routeStr = "";
        }
    }

    public Element getElement() {
        return element;
    }

    public String getRoute() {
        return this.routeStr;
    }

    public String getNormalizedRoute() {
        String norm = routeStr;
        if (norm.length() > 1) {
            if (norm.charAt(norm.length() - 1) == '/') {
                norm = norm.substring(0, norm.length()-1);
            }
            if (norm.charAt(0) != '/') {
                norm = '/' + norm;
            }
        }
        else {
            norm = "/";
        }
        return norm;
    }
}
