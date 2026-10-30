package avsa.infrastructure;

import avsa.domain.VehicleProfile;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Wraps the third-party Jackson library behind the small interface the application
 * needs. Domain classes stay free of JSON annotations: objects are written from their
 * fields and rebuilt through their constructors (parameter names) or builder.
 */
public class JsonMapper {

    @JsonDeserialize(builder = VehicleProfile.Builder.class)
    private abstract static class VehicleProfileMixin {
    }

    @JsonPOJOBuilder(withPrefix = "")
    private abstract static class VehicleProfileBuilderMixin {
    }

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new ParameterNamesModule())
            .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
            .setVisibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.ANY)
            .setVisibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.ANY)
            .addMixIn(VehicleProfile.class, VehicleProfileMixin.class)
            .addMixIn(VehicleProfile.Builder.class, VehicleProfileBuilderMixin.class)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public <T> T read(Path file, Class<T> type) throws IOException {
        return mapper.readValue(file.toFile(), type);
    }

    public JsonNode readTree(Path file) throws IOException {
        return mapper.readTree(file.toFile());
    }

    public <T> T fromTree(JsonNode tree, Class<T> type) throws JsonProcessingException {
        return mapper.treeToValue(tree, type);
    }

    public byte[] toBytes(Object value) throws JsonProcessingException {
        return mapper.writeValueAsBytes(value);
    }
}
