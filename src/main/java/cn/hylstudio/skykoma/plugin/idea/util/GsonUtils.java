package cn.hylstudio.skykoma.plugin.idea.util;

import cn.hylstudio.skykoma.plugin.idea.serializer.IntellijExclusionStrategy;
import cn.hylstudio.skykoma.plugin.idea.serializer.PsiElementSerializer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.psi.PsiElement;
import org.apache.commons.collections.CollectionUtils;
import org.apache.lucene.util.CollectionUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GsonUtils {
    public static final Gson GSON = new GsonBuilder()
            .addSerializationExclusionStrategy(new IntellijExclusionStrategy())
            .create();
    public static final Gson PSI_GSON = new GsonBuilder()
            .registerTypeAdapter(PsiElement.class, new PsiElementSerializer())
            .create();
    public static final Gson JUPYTER_KERNEL_JSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private GsonUtils() {
    }

    private static Map<String, Map<String, String>> errMap = new HashMap<>();

    public static Map<String, Map<String, String>> reportErrorMap() {
        return errMap;
    }
    public static void collectInvokeError(String className, String methodName, String errorMsg) {
        if (className == null) {
            return;
        }
        Map<String, String> methodMap = errMap.getOrDefault(className, null);
        if (methodMap == null) {
            methodMap = new HashMap<>();
            errMap.put(className, methodMap);
        }
        if (methodName == null) {
            return;
        }
        if (errorMsg == null) {
            errorMsg = "null";
        }
        methodMap.put(methodName, errorMsg);
    }

}

