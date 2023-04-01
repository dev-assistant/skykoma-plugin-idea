package cn.hylstudio.skykoma.plugin.idea.serializer;

import com.google.common.collect.Sets;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Set;

public class IntellijExclusionStrategy implements ExclusionStrategy {
    private static final Set<String> SHOULD_SKIP_PACKAGES = Sets.newHashSet(
            "java.io",
            "com.intellij"
    );
    private static final Set<String> GENERIC_COLLECTIONS = Sets.newHashSet(
            "java.util.List",
            "java.util.Set",
            "java.util.Collections"
    );

    @Override
    public boolean shouldSkipField(FieldAttributes f) {
        // 排除 Intellij Platform SDK 中的类
        Type declaredType = f.getDeclaredType();
        String typeName = declaredType.getTypeName();
        for (String packageName : SHOULD_SKIP_PACKAGES) {
            if (typeName.startsWith(packageName)) {
                return true;
            }
        }
        // 排除泛型类型为 Intellij Platform SDK 中的类
        for (String genericCollection : GENERIC_COLLECTIONS) {
            if (isGenericTypeOf(declaredType, genericCollection)) {
                Type actualTypeArgument = ((ParameterizedType) declaredType).getActualTypeArguments()[0];
                for (String packageName : SHOULD_SKIP_PACKAGES) {
                    if (actualTypeArgument.getTypeName().startsWith(packageName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean shouldSkipClass(Class<?> clazz) {
        // 排除 Intellij Platform SDK 中的类
        for (String packageName : SHOULD_SKIP_PACKAGES) {
            if (clazz.getName().startsWith(packageName)) {
                return true;
            }
        }
        return false;
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
