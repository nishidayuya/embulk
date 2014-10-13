package org.quickload.model;

import java.lang.reflect.Proxy;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import com.google.common.collect.ImmutableMap;

class ModelAccessorHandler
        implements InvocationHandler
{
    private final Class<?> iface;
    private final ModelValidator modelValidator;
    private final Map<String, Object> objects;

    public ModelAccessorHandler(Class<?> iface, ModelValidator modelValidator, Map<String, Object> objects)
    {
        this.iface = iface;
        this.modelValidator = modelValidator;
        this.objects = objects;
    }

    /**
     * fieldName = Method of the getter
     */
    public static Map<String, Method> fieldGetters(Class<?> iface)
    {
        ImmutableMap.Builder<String, Method> builder = ImmutableMap.builder();
        for (Method method : iface.getMethods()) {
            String methodName = method.getName();
            String fieldName = getterFieldNameOrNull(methodName);
            if (fieldName != null && hasExpectedArgumentLength(method, 0)) {
                builder.put(fieldName, method);
            }
        }
        return builder.build();
    }

    /**
     * visible for ModelManager.AccessorSerializer
     */
    Map<String, Object> getObjects()
    {
        return objects;
    }

    protected int invokeHashCode()
    {
        return objects.hashCode();
    }

    protected boolean invokeEquals(Object other)
    {
        return (other instanceof ModelAccessorHandler) &&
            objects.equals(((ModelAccessorHandler) other).objects);
    }

    protected Object invokeGetter(Method method, String fieldName)
    {
        return objects.get(fieldName);
    }

    protected void invokeSetter(Method method, String fieldName, Object value)
    {
        if (value == null) {
            objects.remove(fieldName);
        } else {
            objects.put(fieldName, value);
        }
    }

    public Object invoke(Object proxy, Method method, Object[] args)
    {
        String methodName = method.getName();

        switch(methodName) {
        case "hashCode":
            checkArgumentLength(method, 0, methodName);
            return invokeHashCode();

        case "equals":
            checkArgumentLength(method, 1, methodName);
            if (args[0] instanceof Proxy) {
                Object otherHandler = Proxy.getInvocationHandler(args[0]);
                return invokeEquals(otherHandler);
            }
            return false;

        case "validate":
            modelValidator.validateModel(proxy);
            return proxy;

        case "toString":
            checkArgumentLength(method, 0, methodName);
            // TODO json?
            return this.toString();

        default:
            {
                String fieldName;
                fieldName = getterFieldNameOrNull(methodName);
                if (fieldName != null) {
                    checkArgumentLength(method, 0, methodName);
                    return invokeGetter(method, fieldName);
                }
                fieldName = setterFieldNameOrNull(methodName);
                if (fieldName != null) {
                    checkArgumentLength(method, 1, methodName);
                    invokeSetter(method, fieldName, args[0]);
                }
            }
        }

        throw new IllegalArgumentException(String.format("Undefined method '%s'", methodName));
    }

    private static String getterFieldNameOrNull(String methodName)
    {
        if (methodName.startsWith("get")) {
            return methodName.substring(3);
        }
        return null;
    }

    private static String setterFieldNameOrNull(String methodName)
    {
        if (methodName.startsWith("set")) {
            return methodName.substring(3);
        }
        return null;
    }

    protected static boolean hasExpectedArgumentLength(Method method, int expected)
    {
        return method.getParameterTypes().length == expected;
    }

    protected static void checkArgumentLength(Method method, int expected, String methodName)
    {
        if (!hasExpectedArgumentLength(method, expected)) {
            throw new IllegalArgumentException(
                    String.format("Method '%s' expected %d argument but got %d arguments", methodName, expected, method.getParameterTypes().length));
        }
    }
}
