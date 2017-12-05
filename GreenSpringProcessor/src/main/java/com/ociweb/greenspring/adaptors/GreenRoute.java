package com.ociweb.greenspring.adaptors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GreenRoute {
    private final String routeStr;
    private final Element element;

    private final static Map<String, String> spec = new HashMap<>();
    static {
        spec.put("java.lang.String", "$");
        spec.put("byte", "#");
        spec.put("short", "#");
        spec.put("int", "#");
        spec.put("long", "#");
        spec.put("float", "%");
        spec.put("double", "%");
    }

    public static List<GreenRoute> fetchControllers(RoundEnvironment roundEnv) {
        return roundEnv.getElementsAnnotatedWith(RequestMapping.class).stream()
                .filter(element -> element.getKind() == ElementKind.CLASS)
                .map(element -> {
                    RequestMapping mapping = element.getAnnotation(RequestMapping.class);
                    return new GreenRoute(mapping.value(), element);
                }
        ).collect(Collectors.toList());
    }

    public static List<GreenRoute> fetchMethods(Element controller) {
        List<GreenRoute> routes = new ArrayList<>();
        for (Element element : controller.getEnclosedElements()) {
            if (element.getKind() == ElementKind.METHOD) {
                RequestMapping m1 = element.getAnnotation(RequestMapping.class);
                if (m1 != null) {
                    routes.add(new GreenRoute(m1.value(), element));
                }
                else {
                    GetMapping m2 = element.getAnnotation(GetMapping.class);
                    if (m2 != null) {
                        routes.add(new GreenRoute(m2.value(), element));
                    }
                }
            }
        }
        return routes;
    }

    private GreenRoute(String[] routeStrs, Element element) {
        this.element = element;
        if (routeStrs != null && routeStrs.length > 0) {
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

    public String getGreenRouteString(String baseRoute, Map<String, String> routedParams) {
        int s = 1;
        String route = getNormalizedRoute();
        StringBuilder transformed = new StringBuilder();
        transformed.append(baseRoute);
        do {
            int p = route.indexOf("/", s);
            if (p == -1) {
                p = route.length();
            }
            String partial = route.substring(s, p);
            if (partial.charAt(0) == '{') {
                String key = partial.substring(1, partial.length() - 1);
                String kind = routedParams.get(key);
                if (kind != null) {
                    String typeSpec = spec.get(kind);
                    if (typeSpec != null) {
                        partial = typeSpec + partial;
                    }
                }
            }
            transformed.append("/").append(partial);
            s = p + 1;
        } while (s < route.length());

        return transformed.toString();
    }
}
