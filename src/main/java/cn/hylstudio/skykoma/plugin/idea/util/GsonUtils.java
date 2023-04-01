package cn.hylstudio.skykoma.plugin.idea.util;

import cn.hylstudio.skykoma.plugin.idea.serializer.IntellijExclusionStrategy;
import cn.hylstudio.skykoma.plugin.idea.serializer.PsiElementSerializer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.psi.PsiElement;

public class GsonUtils {
    public static final Gson GSON = new GsonBuilder()
            .addSerializationExclusionStrategy(new IntellijExclusionStrategy())
            .create();
    public static final Gson PSI_GSON = new GsonBuilder()
            .registerTypeAdapter(PsiElement.class, new PsiElementSerializer())
            .create();

    private GsonUtils() {
    }

}
