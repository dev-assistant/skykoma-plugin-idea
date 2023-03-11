package cn.hylstudio.skykoma.plugin.idea.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class GsonUtils {
    public static final Gson GSON = new GsonBuilder()
            .addSerializationExclusionStrategy(new IntellijExclusionStrategy())
            .create();
    private GsonUtils() {
    }

}
