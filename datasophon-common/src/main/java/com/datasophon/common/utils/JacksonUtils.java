package com.datasophon.common.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/**
 * @author zhanghuangbin
 */
public class JacksonUtils {

    public static ObjectMapper getInstance() {
        JsonMapper.Builder builder = JsonMapper.builder();

        builder.defaultDateFormat(new SimpleDateFormat("yyyy-MM-dd"));
        builder.defaultLocale(Locale.CHINA);
        builder.defaultTimeZone(TimeZone.getTimeZone("GMT+8"));

        builder.disable(MapperFeature.DEFAULT_VIEW_INCLUSION);
        builder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        builder.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        builder.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);

        return builder.build();
    }
}
