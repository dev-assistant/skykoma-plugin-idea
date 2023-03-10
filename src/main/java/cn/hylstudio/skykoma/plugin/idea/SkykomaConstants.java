package cn.hylstudio.skykoma.plugin.idea;

import com.google.common.collect.Sets;

import java.util.Set;

public class SkykomaConstants {

    private SkykomaConstants() {

    }
    public static final String END_LINE = ";";
    public static final String OUTPUT_TEMPLATE = "" +
            "// -------------- generated by skykoma begin --------------\n" +
            "%s\n" +
            "// -------------- generated by skykoma end --------------";
    public static final String UNKNOWN_TYPE = "unknown";
    public static final String OBJECT_CLASS = "java.lang.Object";
    public static final String TYPE_TIMESTAMP = "java.sql.Timestamp";
    public static final String TYPE_LONG = "java.lang.Long";

    public static final String LOADING_CACHE_GET_METHOD = "com.google.common.cache.LoadingCache.get";
    public static final String LOADING_CACHE_GET_CLASS = "com.google.common.cache.LoadingCache";
    public static final String EVENT_BUS_PUBLISH_METHOD = "org.springframework.context.ApplicationEventPublisher.publishEvent";
    public static final String EVENT_BUS_CLASS = "org.springframework.context.ApplicationEventPublisher";
    public static final String EVENT_BUS_ONEVENT_METHOD = "org.springframework.context.ApplicationListener.onApplicationEvent";
    public static final String ANN_REQUEST_MAPPING_CLASS = "org.springframework.web.bind.annotation.RequestMapping";
    public static final String SERVICE_ANNOTATION_CLASS = "org.springframework.stereotype.Service";
    public static final String COMPONENT_ANNOTATION_CLASS = "org.springframework.stereotype.Component";
    public static final String CONTROLLER_ANNOTATION_CLASS = "org.springframework.stereotype.Controller";
    public static final String REST_CONTROLLER_ANNOTATION_CLASS = "org.springframework.web.bind.annotation.RestController";
    public static final String JPA_TABLE_CLASS = "javax.persistence.Table";
    public static final String JPA_COLUMN_CLASS = "javax.persistence.Column";
    public static final String JPA_ENTITY_ANNOTATION_CLASS = "javax.persistence.Entity";
    public static final Set<String> CONTROLLER_ANNOTATIONS = Sets.newHashSet(
            SkykomaConstants.CONTROLLER_ANNOTATION_CLASS,
            SkykomaConstants.REST_CONTROLLER_ANNOTATION_CLASS
    );
}