package ru.otus.appcontainer;

import org.reflections.Reflections;
import ru.otus.appcontainer.api.AppComponent;
import ru.otus.appcontainer.api.AppComponentsContainer;
import ru.otus.appcontainer.api.AppComponentsContainerConfig;

import java.lang.reflect.Method;
import java.util.*;

import static ru.otus.appcontainer.ContextException.*;
import static ru.otus.appcontainer.ReflectionUtils.*;

public class AppComponentsContainerImpl implements AppComponentsContainer {

    private final Map<String, List<Object>> appComponentsByName = new HashMap<>();

    public AppComponentsContainerImpl(String configsPath) {
        Reflections reflections = new Reflections(configsPath);
        Class<?>[] configClasses = reflections.getTypesAnnotatedWith(AppComponentsContainerConfig.class)
                .toArray(Class<?>[]::new);

        processConfig(configClasses);
    }

    public AppComponentsContainerImpl(Class<?>... initialConfigClass) {
        processConfig(initialConfigClass);
    }

    private void processConfig(Class<?>... configClasses) {
        checkConfigClass(configClasses);

        Arrays.stream(configClasses)
                .sorted(Comparator.comparing(o -> o.getAnnotation(AppComponentsContainerConfig.class).order()))
                .forEach(this::extractAndStore);
    }

    private void extractAndStore(Class<?> configClass) {
        Arrays.stream(configClass.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(AppComponent.class))
                .sorted(Comparator.comparing(m -> m.getAnnotation(AppComponent.class).order()))
                .forEach(this::extractAndStore);
    }

    private void extractAndStore(Method method) {
        Class<?> configClass = method.getDeclaringClass();
        String beanNameBase = method.getAnnotation(AppComponent.class).name();
        if (appComponentsByName.containsKey(beanNameBase)) {
            throw new ContextException(DUPLICATE_BEAN_NAME.formatted(beanNameBase));
        }
        Class<?> returnType = method.getReturnType();
        String beanNameByType = returnType.getName();

        Object noArgInstance = getNoArgInstance(configClass);
        Object[] args = Arrays.stream(method.getParameterTypes())
                .map(par -> getAppComponent(par.getName()))
                .toArray(Object[]::new);
        Object bean = invokeMethod(method, noArgInstance, args);

        String beanNameByClass = bean.getClass().getName();

        store(beanNameBase, bean);
        store(beanNameByType, bean);
        store(beanNameByClass, bean);
    }

    private void store(String name, Object bean) {
        List<Object> beans = Optional.ofNullable(appComponentsByName.get(name)).orElse(new ArrayList<>());
        beans.add(bean);
        appComponentsByName.put(name, beans);
    }

    private void checkConfigClass(Class<?>... configClasses) {
        Arrays.stream(configClasses).forEach(configClass -> {
            if (!configClass.isAnnotationPresent(AppComponentsContainerConfig.class)) {
                throw new IllegalArgumentException(String.format("Given class is not config %s", configClass.getName()));
            }
        });
    }

    @Override
    public <C> C getAppComponent(Class<C> componentClass) {
        return getAppComponent(componentClass.getTypeName());
    }

    @Override
    public <C> C getAppComponent(String componentName) {
        List<Object> beans = this.appComponentsByName.get(componentName);
        if (beans == null) {
            throw new ContextException(BEAN_BY_NAME_DOES_NOT_CONTAINS.formatted(componentName));
        } else if (beans.size() > 1) {
            throw new ContextException(MORE_THEN_ONE_IMPL.formatted(componentName));
        }
        return uncheckedCast(beans.get(0));
    }
}
