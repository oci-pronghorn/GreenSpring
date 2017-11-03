package com.ociweb.greenspring;

import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.Set;

@SupportedAnnotationTypes("org.springframework.web.bind.annotation.*")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
//@AutoService(CreateGreenSpringApp.class) - Google thing to produce meta file
public class CreateGreenSpringApp extends AbstractProcessor {
    private Filer filer;
    private Messager messager;
    private final String indent = "    ";
    private final String appName =  "GreenSpringApp";
    private final String subPackage =  "";
    private final int port =  80;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.isEmpty()) {
            return true;
        }
        AppStructure app = new AppStructure(appName, subPackage, port);
        BehaviorStructure current = null;
        for (Element element : roundEnv.getElementsAnnotatedWith(RequestMapping.class)) {
            RequestMapping mapping = element.getAnnotation(RequestMapping.class);
            if (element.getKind() == ElementKind.CLASS) {
                current = null;
                try {
                    current = new BehaviorStructure(mapping, element, subPackage);
                    app.addBehavior(current);
                } catch (ClassNotFoundException e) {
                    messager.printMessage(Diagnostic.Kind.ERROR, e.getLocalizedMessage(), element);
                }
            } else if (current != null && element.getKind() == ElementKind.METHOD) {
                current.addRoutedMethod(mapping, (ExecutableElement) element);
            }
        }
        try {
            app.write(filer, indent);
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR, e.getLocalizedMessage());
        }
        return true;
    }
}
