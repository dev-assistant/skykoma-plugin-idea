package cn.hylstudio.skykoma.plugin.idea.util;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class IntellijExclusionStrategy implements ExclusionStrategy {
    @Override
    public boolean shouldSkipField(FieldAttributes f) {
        Class<?> declaringClass = f.getDeclaringClass();
        // 排除 Intellij Platform SDK 中的类
        if (declaringClass.getName().startsWith("com.intellij")) {
            return true;
        }

        // 排除泛型类型为 Intellij Platform SDK 中的类
        Class<?> fieldType = f.getDeclaredClass();
        if (isGenericTypeOf(fieldType, "java.util.List") &&
            isGenericTypeOf(f.getDeclaredType(), "com.intellij")) {
            return true;
        }

        return false;
    }

    @Override
    public boolean shouldSkipClass(Class<?> clazz) {
        // 排除 Intellij Platform SDK 中的类
        return clazz.getName().startsWith("com.intellij");
    }

    private boolean isGenericTypeOf(Type type, String className) {
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Type rawType = parameterizedType.getRawType();
            if (rawType.getTypeName().startsWith(className)) {
                return true;
            }
        }
        return false;
    }
}
