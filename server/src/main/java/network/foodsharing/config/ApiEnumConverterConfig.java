package network.foodsharing.config;

import java.lang.reflect.Method;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Query / path parameters of generated enums are bound by their JSON value ({@code lang=fr} → {@code Language.FR})
 * through the generated {@code fromValue}; the constant name keeps working.
 */
@Configuration(proxyBeanMethods = false)
public class ApiEnumConverterConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(new ApiEnumConverterFactory());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static final class ApiEnumConverterFactory implements ConverterFactory<String, Enum> {

        @Override
        public <T extends Enum> Converter<String, T> getConverter(Class<T> targetType) {
            return source -> {
                String value = source.strip();
                if (value.isEmpty()) {
                    return null;
                }
                if (targetType.getPackageName().equals("network.foodsharing.api.model")) {
                    try {
                        Method fromValue = targetType.getMethod("fromValue", String.class);
                        return (T) fromValue.invoke(null, value);
                    } catch (ReflectiveOperationException e) {
                        // fall back to the constant name below
                    }
                }
                return (T) Enum.valueOf(targetType, value);
            };
        }
    }
}
