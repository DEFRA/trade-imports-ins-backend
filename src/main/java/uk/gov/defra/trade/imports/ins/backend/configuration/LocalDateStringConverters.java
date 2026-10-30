package uk.gov.defra.trade.imports.ins.backend.configuration;

import java.time.LocalDate;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;

public final class LocalDateStringConverters {

    private LocalDateStringConverters() {
    }

    @WritingConverter
    public static class LocalDateToStringConverter implements Converter<LocalDate, String> {

        @Override
        public String convert(LocalDate source) {
            return source.toString();
        }
    }

    @ReadingConverter
    public static class StringToLocalDateConverter implements Converter<String, LocalDate> {

        @Override
        public LocalDate convert(String source) {
            return LocalDate.parse(source);
        }
    }
}
