package cn.hylstudio.skykoma.plugin.idea.service.impl;

import cn.hylstudio.skykoma.plugin.idea.service.IHttpService;
import com.intellij.openapi.diagnostic.Logger;
import okhttp3.*;

public class HttpServiceImpl implements IHttpService {
    private static final Logger LOGGER = Logger.getInstance(HttpServiceImpl.class);

    public static final MediaType JSON
            = MediaType.get("application/json; charset=utf-8");

    public static final OkHttpClient client = new OkHttpClient();

    @Override
    public String postJsonBody(String url, String payload) {
        try {
            RequestBody body = RequestBody.create(payload, JSON);
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();
            try (Response response = client.newCall(request).execute()) {
                return response.body().string();
            }
        } catch (Exception e) {
            LOGGER.error(String.format("post json body error, %s", e.getMessage()), e);
        }
        return "";
    }
}
